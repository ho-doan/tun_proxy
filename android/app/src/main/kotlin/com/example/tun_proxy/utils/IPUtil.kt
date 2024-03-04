package com.example.tun_proxy.utils

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException

object IPUtil {
    private const val TAG = "Tun2Http.IPUtil"
    fun isValidIPv4Address(address: String): Boolean {
        if (address.isEmpty()) {
            return false
        }
        val parts = address.split(":".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        var port = 0
        if (parts.size > 1) {
            port = try {
                parts[1].toInt()
            } catch (e: NumberFormatException) {
                return false
            }
            if (!(0 < port && port < 65536)) {
                return false
            }
        }
        val ipParts = parts[0].split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (ipParts.size != 4) {
            return false
        } else {
            for (i in ipParts.indices) {
                var ipPart = -1
                ipPart = try {
                    ipParts[i].toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (!(0 <= ipPart && ipPart <= 255)) {
                    return false
                }
            }
        }
        return true
    }

    @Throws(UnknownHostException::class)
    fun toCIDR(start: String?, end: String?): List<CIDR> {
        return toCIDR(InetAddress.getByName(start), InetAddress.getByName(end))
    }

    @Throws(UnknownHostException::class)
    fun toCIDR(start: InetAddress, end: InetAddress): List<CIDR> {
        val listResult: MutableList<CIDR> = ArrayList()
        Log.i(TAG, "toCIDR(" + start.hostAddress + "," + end.hostAddress + ")")
        var from = inet2long(start)
        val to = inet2long(end)
        while (to >= from) {
            var prefix: Byte = 32
            while (prefix > 0) {
                val mask = prefix2mask(prefix - 1)
                if (from and mask != from) break
                prefix--
            }
            val max =
                (32 - Math.floor(Math.log((to - from + 1).toDouble()) / Math.log(2.0))).toInt().toByte()
            if (prefix < max) prefix = max
            listResult.add(CIDR(long2inet(from), prefix.toInt()))
            from += Math.pow(2.0, (32 - prefix).toDouble()).toLong()
        }
        for (cidr in listResult) Log.i(TAG, cidr.toString())
        return listResult
    }

    private fun prefix2mask(bits: Int): Long {
        return -0x100000000L shr bits and 0xFFFFFFFFL
    }

    private fun inet2long(addr: InetAddress?): Long {
        var result: Long = 0
        if (addr != null) for (b in addr.address) result =
            result shl 8 or (b.toInt() and 0xFF).toLong()
        return result
    }

    private fun long2inet(addr: Long): InetAddress? {
        var addr = addr
        return try {
            val b = ByteArray(4)
            for (i in b.indices.reversed()) {
                b[i] = (addr and 0xFFL).toByte()
                addr = addr shr 8
            }
            InetAddress.getByAddress(b)
        } catch (ignore: UnknownHostException) {
            null
        }
    }

    fun minus1(addr: InetAddress?): InetAddress? {
        return long2inet(inet2long(addr) - 1)
    }

    fun plus1(addr: InetAddress?): InetAddress? {
        return long2inet(inet2long(addr) + 1)
    }

    class CIDR : Comparable<CIDR> {
        var address: InetAddress? = null
        var prefix = 0

        constructor(address: InetAddress?, prefix: Int) {
            this.address = address
            this.prefix = prefix
        }

        constructor(ip: String?, prefix: Int) {
            try {
                address = InetAddress.getByName(ip)
                this.prefix = prefix
            } catch (ex: UnknownHostException) {
                Log.e(
                    TAG, """
     $ex
     ${Log.getStackTraceString(ex)}
     """.trimIndent()
                )
            }
        }

        val start: InetAddress?
            get() = long2inet(inet2long(address) and prefix2mask(prefix))
        val end: InetAddress?
            get() = long2inet((inet2long(address) and prefix2mask(prefix)) + (1L shl 32 - prefix) - 1)

        override fun toString(): String {
            return address!!.hostAddress + "/" + prefix + "=" + start!!.hostAddress + "..." + end!!.hostAddress
        }

        override operator fun compareTo(other: CIDR): Int {
            val lcidr = inet2long(address)
            val lother = inet2long(other.address)
            return lcidr.compareTo(lother)
        }
    }
}
