package ephyra.presentation.core.util.view

import android.app.Activity
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
 * Sets an activity transition animation using [Activity.overrideActivityTransition].
 *
 * @param overrideTransitionType [Activity.OVERRIDE_TRANSITION_OPEN] or [Activity.OVERRIDE_TRANSITION_CLOSE]
 * @param enterAnim resource ID of the enter animation, or 0 for none
 * @param exitAnim resource ID of the exit animation, or 0 for none
 */
fun Activity.overrideTransitionCompat(overrideTransitionType: Int, enterAnim: Int, exitAnim: Int) {
    overrideActivityTransition(overrideTransitionType, enterAnim, exitAnim)
}

/**
 * Requests the highest display refresh rate available on the device for the window.
 *
 * Sets [WindowManager.LayoutParams.preferredDisplayModeId] to the display mode that has the
 * highest refresh rate, allowing the panel to run at full speed (e.g. 90/120/144 Hz) while
 * the reader is active and giving smooth page transitions and scrolling.
 *
 * On Android 15+ (API 35) also opts out of power-savings-balanced frame rate management
 * ([Window.setFrameRatePowerSavingsBalanced]) and enables touch-responsive rate boosting
 * ([Window.setFrameRateBoostOnTouchEnabled]). This lets the OS automatically reduce the
 * display refresh rate when the screen is idle/static (saving battery), while instantly
 * boosting back to the high rate the moment the user touches or scrolls.
 */
fun Window.applyHighRefreshRate() {
    val display = context.display
    val supportedModes = display.supportedModes
    if (supportedModes.isEmpty()) return

    val highestMode = supportedModes.maxByOrNull { it.refreshRate } ?: return
    attributes = attributes.apply {
        preferredDisplayModeId = highestMode.modeId
    }

    // API 35+: Opt out of automatic refresh-rate reductions during the reading session,
    // and enable automatic rate boost when touch input is detected so the rate is high
    // during scrolling/swiping and can be reduced by the OS when the display is static.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        setFrameRatePowerSavingsBalanced(false)
        setFrameRateBoostOnTouchEnabled(true)
    }
}
