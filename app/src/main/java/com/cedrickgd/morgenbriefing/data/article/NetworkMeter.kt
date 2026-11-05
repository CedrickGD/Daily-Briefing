package com.cedrickgd.morgenbriefing.data.article

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/** Is the current connection free to use (Wi-Fi, Ethernet, an unmetered plan)? Behind an interface for tests. */
fun interface NetworkMeter {
    fun isUnmetered(): Boolean

    /** Is there a connection at all? (Verifying articles needs one; without it nothing is tried.) */
    fun isOnline(): Boolean = true
}

/** [NetworkMeter] from Android's [ConnectivityManager] (`NET_CAPABILITY_NOT_METERED`, `NET_CAPABILITY_INTERNET`). */
class AndroidNetworkMeter(context: Context) : NetworkMeter {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private fun capabilities(): NetworkCapabilities? {
        val manager = connectivity ?: return null
        return try {
            manager.getNetworkCapabilities(manager.activeNetwork)
        } catch (_: SecurityException) {
            null
        }
    }

    override fun isUnmetered(): Boolean {
        val caps = capabilities() ?: return false
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return true
        // Android 11+: a metered plan that is free for now (e.g. unlimited 5G) counts as unmetered.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
    }

    override fun isOnline(): Boolean {
        // No manager (should not happen): rather try and fail than never verify.
        if (connectivity == null) return true
        return capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
