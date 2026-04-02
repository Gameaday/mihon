package ephyra.presentation.core.ui

/**
 * Implemented by the host Activity (MainActivity) to signal that the app has finished
 * its loading/splash phase and is fully interactive.
 *
 * Feature tabs that need to clear the splash screen should cast the current [android.content.Context]
 * to this interface rather than importing [MainActivity] directly, preserving module boundaries.
 *
 * Usage in a Tab composable:
 * ```kotlin
 * val context = LocalContext.current
 * LaunchedEffect(isLoaded) {
 *     if (isLoaded) (context as? AppReadySignal)?.signalReady()
 * }
 * ```
 */
interface AppReadySignal {
    fun signalReady()
}
