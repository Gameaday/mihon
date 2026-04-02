package ephyra.presentation.core.ui

/**
 * Implemented by the host screen ([HomeScreen]) to toggle bottom navigation bar visibility.
 *
 * Feature tabs use this interface instead of importing [HomeScreen] directly, keeping
 * feature modules free of dependencies on the app-level navigation shell.
 *
 * Usage in a Tab composable:
 * ```kotlin
 * val navigator = LocalNavigator.currentOrThrow
 * LaunchedEffect(selectionMode) {
 *     (navigator.parent?.lastItemOrNull as? BottomNavController)?.showBottomNav(!selectionMode)
 * }
 * ```
 *
 * [HomeScreen] is a Voyager [cafe.adriel.voyager.core.screen.Screen] that implements this
 * interface via its `companion object`, which is the object passed in [navigator.items].
 */
interface BottomNavController {
    suspend fun showBottomNav(show: Boolean)
}
