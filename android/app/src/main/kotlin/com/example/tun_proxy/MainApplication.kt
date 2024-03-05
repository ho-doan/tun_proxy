package com.example.tun_proxy

import android.app.Application
import androidx.preference.PreferenceManager


class MainApplication : Application() {
    private val PREF_VPN_MODE = "pref_vpn_connection_mode"
    private val PREF_APP_KEY =
        arrayOf("pref_vpn_disallowed_application", "pref_vpn_allowed_application")

    companion object {
        private var instance: MainApplication? = null

        fun getInstance(): MainApplication? {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    enum class VPNMode {
        DISALLOW, ALLOW
    }

    enum class AppSortBy {
        APPNAME, PKGNAME
    }

    enum class AppOrderBy {
        ASC, DESC
    }

    enum class AppFiltertBy {
        APPNAME, PKGNAME
    }

    fun loadVPNMode(): VPNMode? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )
        val vpn_mode =
            sharedPreferences.getString(PREF_VPN_MODE, MainApplication.VPNMode.DISALLOW.name)
        return VPNMode.valueOf(vpn_mode!!)
    }

    fun storeVPNMode(mode: VPNMode) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )
        val editor = prefs.edit()
        editor.putString(PREF_VPN_MODE, mode.name).apply()
        return
    }

    fun loadVPNApplication(mode: VPNMode): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )
        return prefs.getStringSet(PREF_APP_KEY[mode.ordinal], HashSet())!!
    }

    fun storeVPNApplication(mode: VPNMode, set: Set<String?>?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )
        val editor = prefs.edit()
        editor.putStringSet(PREF_APP_KEY[mode.ordinal], set).apply()
        return
    }
}