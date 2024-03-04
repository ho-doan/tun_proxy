package com.example.tun_proxy.utils

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.text.TextUtils

object Util {
    private external fun jni_getprop(name: String): String?
    fun getDefaultDNS(context: Context): List<String> {
        var dns1: String? = null
        var dns2: String? = null
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val an = cm.activeNetwork
            if (an != null) {
                val lp = cm.getLinkProperties(an)
                if (lp != null) {
                    val dns = lp.dnsServers
                    if (dns != null) {
                        if (dns.size > 0) dns1 = dns[0].hostAddress
                        if (dns.size > 1) dns2 = dns[1].hostAddress
                    }
                }
            }
        } else {
            dns1 = jni_getprop("net.dns1")
            dns2 = jni_getprop("net.dns2")
        }
        val listDns: MutableList<String> = ArrayList()
        listDns.add((if (TextUtils.isEmpty(dns1)) "8.8.8.8" else dns1)!!)
        listDns.add((if (TextUtils.isEmpty(dns2)) "8.8.4.4" else dns2)!!)
        return listDns
    }
}