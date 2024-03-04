package com.example.tun_proxy.proxy.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.RemoteException
import android.text.TextUtils
import android.util.Log
import com.example.tun_proxy.R
import java.io.IOException
import java.net.InetAddress
import java.util.Arrays

class Tun2HttpVpnService : VpnService() {
    private var lastBuilder: Builder? = null
    private var vpn: ParcelFileDescriptor? = null
    private external fun jni_init()
    private external fun jni_start(
        tun: Int,
        fwd53: Boolean,
        rcode: Int,
        proxyIp: String?,
        proxyPort: Int
    )

    private external fun jni_stop(tun: Int)
    private external fun jni_get_mtu(): Int
    private external fun jni_done()
    override fun onBind(intent: Intent): IBinder? {
        return ServiceBinder()
    }

    val isRunning: Boolean
        get() = vpn != null

    private fun start() {
        if (vpn == null) {
            lastBuilder = builder
            vpn = startVPN(lastBuilder)
            checkNotNull(vpn) { getString(R.string.msg_start_failed) }
            startNative(vpn!!)
        }
    }

    private fun stop() {
        if (vpn != null) {
            stopNative(vpn!!)
            stopVPN(vpn!!)
            vpn = null
        }
        stopForeground(true)
    }

    override fun onRevoke() {
        Log.i(TAG, "Revoke")
        stop()
        vpn = null
        super.onRevoke()
    }

    @Throws(SecurityException::class)
    private fun startVPN(builder: Builder?): ParcelFileDescriptor? {
        return try {
            builder!!.establish()
        } catch (ex: SecurityException) {
            throw ex
        } catch (ex: Throwable) {
            Log.e(
                TAG, """
     $ex
     ${Log.getStackTraceString(ex)}
     """.trimIndent()
            )
            null
        }
    }

    private val builder: Builder
        private get() {
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

            // Build VPN service
            val builder: Builder = Builder()
            builder.setSession(getString(R.string.app_name))

            // VPN address
            val vpn4 = prefs.getString("vpn4", "10.1.10.1")
            builder.addAddress(vpn4!!, 32)
            val vpn6 = prefs.getString("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1")
            builder.addAddress(vpn6!!, 128)
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("0:0:0:0:0:0:0:0", 0)
            val dnsList: List<String> =
                Util.getDefaultDNS(MyApplication.getInstance().getApplicationContext())
            for (dns in dnsList) {
                Log.i(TAG, "default DNS:$dns")
                builder.addDnsServer(dns)
            }

            // MTU
            val mtu = jni_get_mtu()
            Log.i(TAG, "MTU=$mtu")
            builder.setMtu(mtu)

            // AAdd list of allowed and disallowed applications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val app: MyApplication = this.application as MyApplication
                if (app.loadVPNMode() === MyApplication.VPNMode.DISALLOW) {
                    val disallow: MutableSet<String> =
                        app.loadVPNApplication(MyApplication.VPNMode.DISALLOW)
                    Log.d(TAG, "disallowed:" + disallow.size)
                    val notFoundPackageList: MutableList<String> = ArrayList()
                    builder.addDisallowedApplication(
                        Arrays.asList(*disallow.toTypedArray()),
                        notFoundPackageList
                    )
                    disallow.removeAll(notFoundPackageList)
                    MyApplication.getInstance()
                        .storeVPNApplication(MyApplication.VPNMode.DISALLOW, disallow)
                } else {
                    val allow: MutableSet<String> =
                        app.loadVPNApplication(MyApplication.VPNMode.ALLOW)
                    Log.d(TAG, "allowed:" + allow.size)
                    val notFoundPackageList: MutableList<String> = ArrayList()
                    builder.addAllowedApplication(
                        Arrays.asList(*allow.toTypedArray()),
                        notFoundPackageList
                    )
                    allow.removeAll(notFoundPackageList)
                    MyApplication.getInstance()
                        .storeVPNApplication(MyApplication.VPNMode.ALLOW, allow)
                }
            }

            // Add list of allowed applications
            return builder
        }

    private fun startNative(vpn: ParcelFileDescriptor) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val proxyHost = prefs.getString(PREF_PROXY_HOST, "")
        val proxyPort = prefs.getInt(PREF_PROXY_PORT, 0)
        if (proxyPort != 0 && !TextUtils.isEmpty(proxyHost)) {
            jni_start(vpn.fd, false, 3, proxyHost, proxyPort)
            prefs.edit().putBoolean(PREF_RUNNING, true).apply()
        }
    }

    private fun stopNative(vpn: ParcelFileDescriptor) {
        try {
            jni_stop(vpn.fd)
        } catch (ex: Throwable) {
            // File descriptor might be closed
            Log.e(
                TAG, """
     $ex
     ${Log.getStackTraceString(ex)}
     """.trimIndent()
            )
            jni_stop(-1)
        }
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean(PREF_RUNNING, false).apply()
    }

    private fun stopVPN(pfd: ParcelFileDescriptor) {
        Log.i(TAG, "Stopping")
        try {
            pfd.close()
        } catch (ex: IOException) {
            Log.e(
                TAG, """
     $ex
     ${Log.getStackTraceString(ex)}
     """.trimIndent()
            )
        }
    }

    // Called from native code
    private fun nativeExit(reason: String?) {
        Log.w(TAG, "Native exit reason=$reason")
        if (reason != null) {
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putBoolean("enabled", false).apply()
        }
    }

    // Called from native code
    private fun nativeError(error: Int, message: String) {
        Log.w(TAG, "Native error $error: $message")
    }

    private fun isSupported(protocol: Int): Boolean {
        return protocol == 1 || protocol == 59 || protocol == 6 || protocol == 17 /* UDP */
    }

    override fun onCreate() {
        // Native init
        jni_init()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received $intent")
        // Handle service restart
        if (intent == null) {
            return START_STICKY
        }
        if (ACTION_START == intent.action) {
            start()
        }
        if (ACTION_STOP == intent.action) {
            stop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroy")
        try {
            if (vpn != null) {
                stopNative(vpn!!)
                stopVPN(vpn!!)
                vpn = null
            }
        } catch (ex: Throwable) {
            Log.e(
                TAG, """
     $ex
     ${Log.getStackTraceString(ex)}
     """.trimIndent()
            )
        }
        jni_done()
        super.onDestroy()
    }

    inner class ServiceBinder : Binder() {
        @Throws(RemoteException::class)
        public override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean {
            // see Implementation of android.net.VpnService.Callback.onTransact()
            if (code == LAST_CALL_TRANSACTION) {
                onRevoke()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }

        val service: Tun2HttpVpnService
            get() = this@Tun2HttpVpnService
    }

    private inner class Builder private constructor() : VpnService.Builder() {
        private var mtu = 0
        private val listAddress: MutableList<String> = ArrayList()
        private val listRoute: MutableList<String> = ArrayList()
        private val listDns: MutableList<String> = ArrayList()
        override fun setMtu(mtu: Int): VpnService.Builder {
            this.mtu = mtu
            super.setMtu(mtu)
            return this
        }

        override fun addAddress(address: String, prefixLength: Int): Builder {
            listAddress.add("$address/$prefixLength")
            super.addAddress(address, prefixLength)
            return this
        }

        override fun addRoute(address: String, prefixLength: Int): Builder {
            listRoute.add("$address/$prefixLength")
            super.addRoute(address, prefixLength)
            return this
        }

        override fun addDnsServer(address: InetAddress): Builder {
            listDns.add(address.hostAddress)
            super.addDnsServer(address)
            return this
        }

        override fun addDnsServer(address: String): Builder {
//            listDns.add(address);
            super.addDnsServer(address)
            return this
        }

        // min sdk 26
        fun addAllowedApplication(
            packageList: List<String>,
            notFoundPackegeList: MutableList<String>
        ): Builder {
            for (pkg in packageList) {
                try {
                    Log.i(TAG, "allowed:$pkg")
                    addAllowedApplication(pkg)
                } catch (e: PackageManager.NameNotFoundException) {
                    notFoundPackegeList.add(pkg)
                }
            }
            return this
        }

        @Throws(PackageManager.NameNotFoundException::class)
        fun addDisallowedApplication(packageList: List<String>): Builder {
            //
            for (pkg in packageList) {
                Log.i(TAG, "disallowed:$pkg")
                addDisallowedApplication(pkg)
            }
            return this
        }

        fun addDisallowedApplication(
            packageList: List<String>,
            notFoundPackegeList: MutableList<String>
        ): Builder {
            //
            for (pkg in packageList) {
                try {
                    Log.i(TAG, "disallowed:$pkg")
                    addDisallowedApplication(pkg)
                } catch (e: PackageManager.NameNotFoundException) {
                    notFoundPackegeList.add(pkg)
                }
            }
            return this
        }

        override fun equals(obj: Any?): Boolean {
            val other = obj as Builder? ?: return false
            if (mtu != other.mtu) return false
            if (listAddress.size != other.listAddress.size) return false
            if (listRoute.size != other.listRoute.size) return false
            if (listDns.size != other.listDns.size) return false
            for (address in listAddress) if (!other.listAddress.contains(address)) return false
            for (route in listRoute) if (!other.listRoute.contains(route)) return false
            for (dns in listDns) if (!other.listDns.contains(dns)) return false
            return true
        } //        public boolean isNetworkConnected() {
        //            final ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        //            if (cm != null) {
        //                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        //                    final android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
        //                    if (ni != null) {
        //                        return (ni.isConnected() && (ni.getType() == ConnectivityManager.TYPE_WIFI || ni.getType() == ConnectivityManager.TYPE_MOBILE));
        //                    }
        //                } else {
        //                    final Network n = cm.getActiveNetwork();
        //                    if (n != null) {
        //                        final NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        //                        return (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
        //                    }
        //                }
        //            }
        //            return false;
        //        }
    }

    companion object {
        const val PREF_PROXY_HOST = "pref_proxy_host"
        const val PREF_PROXY_PORT = "pref_proxy_port"
        const val PREF_RUNNING = "pref_running"
        private const val TAG = "Tun2Http.Service"
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"

        @Volatile
        private var wlInstance: WakeLock? = null

        init {
            System.loadLibrary("tun2http")
        }

        @Synchronized
        private fun getLock(context: Context): WakeLock? {
            if (wlInstance == null) {
                val pm = context.getSystemService(POWER_SERVICE) as PowerManager
                wlInstance = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    context.getString(R.string.app_name) + " wakelock"
                )
                wlInstance.setReferenceCounted(true)
            }
            return wlInstance
        }

        fun start(context: Context) {
            val intent = Intent(context, Tun2HttpVpnService::class.java)
            intent.action = ACTION_START
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, Tun2HttpVpnService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }
}
