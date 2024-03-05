package com.example.tun_proxy.proxy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.util.Log
import androidx.preference.PreferenceManager
import com.example.tun_proxy.R
import com.example.tun_proxy.proxy.service.Tun2HttpVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) {
            return
        }
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isRunning = prefs.getBoolean(Tun2HttpVpnService.PREF_RUNNING, false)
        if (isRunning) {
            val prepare = VpnService.prepare(context)
            if (prepare == null) {
                Log.d(context.getString(R.string.app_name) + ".Boot", "Starting vpn")
                Tun2HttpVpnService.start(context)
            } else {
                Log.d(context.getString(R.string.app_name) + ".Boot", "Not prepared")
            }
        }
    }
}