package ephyra.core.common.util.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import logcat.LogPriority
import rikka.sui.Sui
import java.io.File

/**
 * Returns true if [packageName] is installed.
 */
fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

val Context.hasMiuiPackageInstaller get() = isPackageInstalled("com.miui.packageinstaller")

val Context.isShizukuInstalled get() = isPackageInstalled("moe.shizuku.privileged.api") || Sui.isSui()

val Context.powerManager: PowerManager
    get() = getSystemService()!!

val Context.isPowerSaveMode: Boolean
    get() = powerManager.isPowerSaveMode

fun Context.openInBrowser(url: String, forceDefaultBrowser: Boolean = false) {
    this.openInBrowser(url.toUri(), forceDefaultBrowser)
}

fun Context.openInBrowser(uri: Uri, forceDefaultBrowser: Boolean = false) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // Force default browser so that verified extensions don't re-open Tachiyomi
            if (forceDefaultBrowser) {
                defaultBrowserPackageName()?.let { setPackage(it) }
            }
        }
        startActivity(intent)
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to open URI in browser: $uri" }
    }
}

private fun Context.defaultBrowserPackageName(): String? {
    val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
    val resolveInfo = packageManager.resolveActivity(
        browserIntent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
    )
    return resolveInfo
        ?.activityInfo?.packageName
        ?.takeUnless { it in DeviceUtil.invalidDefaultBrowsers }
}

fun Context.createFileInCacheDir(name: String): File {
    val file = File(externalCacheDir, name)
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
    return file
}
