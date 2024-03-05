package com.example.tun_proxy

import android.R
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.example.tun_proxy.proxy.service.Tun2HttpVpnService
import com.example.tun_proxy.utils.IPUtil.isValidIPv4Address
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel


class MainActivity : FlutterActivity() {
    val REQUEST_VPN = 1
    val REQUEST_CERT = 2
    lateinit var hostText: String
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "tun_proxy"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "startVPN" -> {
                    hostText = ((call.arguments as String?).toString())
                    val i = VpnService.prepare(this)
                    if (i != null) {
                        startActivityForResult(i, REQUEST_VPN);
                    } else {
                        onActivityResult(REQUEST_VPN, RESULT_OK, null);
                    }
                }
                "stopVPN" -> {
                    Tun2HttpVpnService.stop(this)
                }
                "loadHostPort" -> result.success(loadHostPort())

            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            return
        }
        if (requestCode == REQUEST_VPN && parseAndSaveHostPort()) {
            Tun2HttpVpnService.start(this)
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
        edit.commit()
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
