package ephyra.presentation.core.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Material Expressive motion tokens for Ephyra.
 *
 * Provides consistent animation durations and easing curves aligned with
 * Material 3 Expressive motion guidelines. These tokens replace hard-coded
 * animation values throughout the codebase.
 */
object MotionTokens {

    // --- Durations ---

    /** Short duration for micro-interactions like icon toggles and ripples. */
    const val DURATION_SHORT = 150

    /** Medium duration for expanding/collapsing components and tab switches. */
    const val DURATION_MEDIUM = 300

    /** Long duration for full-screen transitions and shared element animations. */
    const val DURATION_LONG = 500

    // --- Easing curves ---

    /** Standard easing for most transitions — elements entering and exiting together. */
    val EasingStandard = FastOutSlowInEasing

    /** Emphasized easing for attention-drawing transitions. */
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Decelerate easing for elements entering the screen. */
    val EasingDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    /** Accelerate easing for elements leaving the screen. */
    val EasingAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    // --- Reusable animation specs ---

    /** Quick tween for micro-interactions. */
    fun <T> tweenShort(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_SHORT,
        easing = EasingStandard,
    )

    /** Standard tween for component-level transitions. */
    fun <T> tweenMedium(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_MEDIUM,
        easing = EasingStandard,
    )

    /** Emphasized tween for attention-drawing transitions. */
    fun <T> tweenEmphasized(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_LONG,
        easing = EasingEmphasized,
    )

    /** Enter transition tween for elements appearing on screen. */
    fun <T> tweenEnter(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_MEDIUM,
        easing = EasingDecelerate,
    )

    /** Exit transition tween for elements leaving the screen. */
    fun <T> tweenExit(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_SHORT,
        easing = EasingAccelerate,
    )
}

/**
 * Convenience accessor for motion tokens from [MaterialTheme].
 */
val MaterialTheme.motion: MotionTokens
    @Composable
    @ReadOnlyComposable
    get() = MotionTokens
