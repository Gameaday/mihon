package ephyra.presentation.core.ui.delegate

import ephyra.core.common.core.security.SecurityPreferences
import ephyra.presentation.core.util.system.AuthenticatorUtil

/**
 * Global state and lifecycle hooks for app-wide security.
 *
 * Lives in presentation-core so that feature modules (e.g. feature:security) can
 * call [unlock] without depending on the main :app module.
 */
object SecureActivityDelegateState {
    /**
     * Set to true if we need the first activity to authenticate.
     *
     * Always require unlock if app is killed.
     */
    var requireUnlock = true

    fun onApplicationStopped(preferences: SecurityPreferences) {
        if (!preferences.useAuthenticator().get()) return

        if (!AuthenticatorUtil.isAuthenticating) {
            if (requireUnlock) return
            if (preferences.lockAppAfter().get() > 0) {
                preferences.lastAppClosed().set(System.currentTimeMillis())
            }
        }
    }

    /**
     * Checks if unlock is needed when the app comes to the foreground.
     */
    fun onApplicationStart(preferences: SecurityPreferences) {
        if (!preferences.useAuthenticator().get()) return

        val lastClosedPref = preferences.lastAppClosed()

        if (!AuthenticatorUtil.isAuthenticating && !requireUnlock) {
            requireUnlock = when (val lockDelay = preferences.lockAppAfter().get()) {
                -1 -> false
                0 -> true
                else -> lastClosedPref.get() + lockDelay * 60_000 <= System.currentTimeMillis()
            }
        }

        lastClosedPref.delete()
    }

    fun unlock() {
        requireUnlock = false
    }
}
