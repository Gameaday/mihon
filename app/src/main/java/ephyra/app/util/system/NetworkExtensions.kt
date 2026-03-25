package ephyra.app.util.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService

val Context.connectivityManager: ConnectivityManager
    get() = getSystemService()!!

val Context.wifiManager: WifiManager
    get() = getSystemService()!!

fun Context.isOnline(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return (NetworkCapabilities.TRANSPORT_CELLULAR..NetworkCapabilities.TRANSPORT_LOWPAN)
        .any(networkCapabilities::hasTransport)
}

/**
 * Returns true if device is connected to Wifi.
 */
fun Context.isConnectedToWifi(): Boolean {
    if (!wifiManager.isWifiEnabled) return false

    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
