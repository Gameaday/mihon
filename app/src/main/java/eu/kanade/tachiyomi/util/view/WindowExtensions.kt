package eu.kanade.tachiyomi.util.view

import android.os.Build
import android.view.Window
import android.view.WindowManager

fun Window.setSecureScreen(enabled: Boolean) {
    if (enabled) {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Requests the highest display refresh rate available on the device for the window, and on
 * Android 15+ opts out of power-savings-balanced frame rate management so the system does
 * not cap the rate during reading sessions.
 *
 * Sets [WindowManager.LayoutParams.preferredDisplayModeId] to the display mode that has the
 * highest refresh rate, allowing the panel to run at full speed (e.g. 90/120/144 Hz) while
 * the reader is active and giving smooth page transitions and scrolling.
 */
@Suppress("DEPRECATION")
fun Window.applyHighRefreshRate() {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        windowManager.defaultDisplay
    } ?: return

    val supportedModes = display.supportedModes
    if (supportedModes.isEmpty()) return

    val highestMode = supportedModes.maxByOrNull { it.refreshRate } ?: return
    attributes = attributes.apply {
        preferredDisplayModeId = highestMode.modeId
    }

    // API 35+: Opt out of automatic refresh-rate reductions to keep the panel at the
    // selected rate throughout the reading session.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        setFrameRatePowerSavingsBalanced(false)
    }
}
