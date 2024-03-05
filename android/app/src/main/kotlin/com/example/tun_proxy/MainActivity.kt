package com.example.tun_proxy

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.example.tun_proxy.proxy.service.Tun2HttpVpnService
import com.example.tun_proxy.proxy.service.Tun2HttpVpnService.ServiceBinder
import com.example.tun_proxy.utils.IPUtil.isValidIPv4Address
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel


class MainActivity : FlutterActivity() {
    private val requestVPN = 1
    val VPN_CONNECTION_MODE = "vpn_connection_mode"
    private lateinit var hostText: String
    private lateinit var channelFlutter: MethodChannel

    var statusHandler: Handler = Handler(Looper.getMainLooper())

    private var service: Tun2HttpVpnService? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val serviceBinder = binder as ServiceBinder
            service = serviceBinder.service
        }

        override fun onServiceDisconnected(className: ComponentName) {
            service = null
            channelFlutter.invokeMethod("startResult", isRunning())
        }
    }

    private var statusRunnable: Runnable = object : Runnable {
        override fun run() {
            statusHandler.post(this)
        }
    }

    private fun isRunning(): Boolean {
        return service != null && service!!.isRunning
    }

    override fun onResume() {
        super.onResume()
        statusHandler.post(statusRunnable)

        val intent = Intent(this, Tun2HttpVpnService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        statusHandler.removeCallbacks(statusRunnable)
        unbindService(serviceConnection)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channelFlutter = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "tun_proxy/fl")
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "tun_proxy"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "startVPN" -> {
                    hostText = ((call.arguments as String?).toString())
                    val i = VpnService.prepare(this)
                    if (i != null) {
                        startActivityForResult(i, requestVPN)
                    } else {
                        onActivityResult(requestVPN, RESULT_OK, null)
                    }
                    result.success(null)
                }

                "stopVPN" -> {
                    Tun2HttpVpnService.stop(this)
                    result.success(null)
                }

                "loadHostPort" -> result.success(loadHostPort())
                "version" -> result.success(getVersionName())
                "changeMode" -> result.success(changeMode(call.arguments as Int))
                "clearAllSelection" -> result.success(clearAllSelection())
                "isRunning" -> result.success(isRunning())
                "isValidIPv4Address" -> result.success(isValidIPv4Address(call.arguments as String))
            }
        }
    }

    private fun clearAllSelection() {
        val set: Set<String> = HashSet()
        MainApplication.getInstance()!!.storeVPNApplication(MainApplication.VPNMode.ALLOW, set)
        MainApplication.getInstance()!!.storeVPNApplication(MainApplication.VPNMode.DISALLOW, set)
    }

    //        DISALLOW 0
    //        ALLOW 1
    private fun changeMode(model: Int) {
        val mode: MainApplication.VPNMode = MainApplication.VPNMode.values()[model]
        MainApplication.getInstance()!!.storeVPNMode(mode)
        return
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            channelFlutter.invokeMethod("startResult", false)
            return
        }
        if (requestCode == requestVPN && parseAndSaveHostPort()) {
            Tun2HttpVpnService.start(this)
            channelFlutter.invokeMethod("startResult", true)
        }
    }

    private fun getVersionName(): String? {
        val packageManager = packageManager ?: return null
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun parseAndSaveHostPort(): Boolean {
        val hostPort: String = hostText
        if (!isValidIPv4Address(hostPort)) {
//            hostEditText.setError(getString(R.string.enter_host))
            return false
        }
        val parts = hostPort.split(":").toTypedArray()
        var port = 0
        if (parts.size > 1) {
            port = try {
                parts[1].toInt()
            } catch (e: NumberFormatException) {
                return false
            }
        }
        val ipParts = parts[0].split("\\.").toTypedArray()
        val host = parts[0]
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = prefs.edit()
        edit.putString(Tun2HttpVpnService.PREF_PROXY_HOST, host)
        edit.putInt(Tun2HttpVpnService.PREF_PROXY_PORT, port)
        edit.apply()
        return true
    }

    private fun loadHostPort(): String? {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val proxyHost = prefs.getString(Tun2HttpVpnService.PREF_PROXY_HOST, "")
        val proxyPort = prefs.getInt(Tun2HttpVpnService.PREF_PROXY_PORT, 0)
        if (TextUtils.isEmpty(proxyHost)) {
            return null
        }
        return "$proxyHost:$proxyPort"
    }
}
