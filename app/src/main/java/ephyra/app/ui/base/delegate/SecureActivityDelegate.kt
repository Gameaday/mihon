package ephyra.app.ui.base.delegate

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import ephyra.domain.base.BasePreferences
import ephyra.app.core.security.SecurityPreferences
import ephyra.app.ui.security.UnlockActivity
import ephyra.app.util.system.AuthenticatorUtil
import ephyra.app.util.system.AuthenticatorUtil.isAuthenticationSupported
import ephyra.app.util.view.overrideTransitionCompat
import ephyra.app.util.view.setSecureScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

    companion object {
        /**
         * Set to true if we need the first activity to authenticate.
         *
         * Always require unlock if app is killed.
         */
        var requireUnlock = true

        fun onApplicationStopped(preferences: SecurityPreferences) {
            if (!preferences.useAuthenticator().get()) return

            if (!AuthenticatorUtil.isAuthenticating) {
                // Return if app is closed in locked state
                if (requireUnlock) return
                // Save app close time if lock is delayed
                if (preferences.lockAppAfter().get() > 0) {
                    preferences.lastAppClosed().set(System.currentTimeMillis())
                }
            }
        }

        /**
         * Checks if unlock is needed when app comes foreground.
         */
        fun onApplicationStart(preferences: SecurityPreferences) {
            if (!preferences.useAuthenticator().get()) return

            val lastClosedPref = preferences.lastAppClosed()

            // `requireUnlock` can be true on process start or if app was closed in locked state
            if (!AuthenticatorUtil.isAuthenticating && !requireUnlock) {
                requireUnlock = when (val lockDelay = preferences.lockAppAfter().get()) {
                    -1 -> false // Never
                    0 -> true // Always
                    else -> lastClosedPref.get() + lockDelay * 60_000 <= System.currentTimeMillis()
                }
            }

            lastClosedPref.delete()
        }

        fun unlock() {
            requireUnlock = false
        }
    }
}

class SecureActivityDelegateImpl(
    private val preferences: BasePreferences,
    private val securityPreferences: SecurityPreferences,
) : SecureActivityDelegate, DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity

    override fun registerSecureActivity(activity: AppCompatActivity) {
        this.activity = activity
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        setSecureScreen()
    }

    override fun onResume(owner: LifecycleOwner) {
        setAppLock()
    }

    private fun setSecureScreen() {
        val secureScreenFlow = securityPreferences.secureScreen().changes()
        val incognitoModeFlow = preferences.incognitoMode().changes()
        combine(secureScreenFlow, incognitoModeFlow) { secureScreen, incognitoMode ->
            secureScreen == SecurityPreferences.SecureScreenMode.ALWAYS ||
                (secureScreen == SecurityPreferences.SecureScreenMode.INCOGNITO && incognitoMode)
        }
            .onEach(activity.window::setSecureScreen)
            .launchIn(activity.lifecycleScope)
    }

    private fun setAppLock() {
        if (!securityPreferences.useAuthenticator().get()) return
        if (activity.isAuthenticationSupported()) {
            if (!SecureActivityDelegate.requireUnlock) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            activity.overrideTransitionCompat(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            securityPreferences.useAuthenticator().set(false)
        }
    }
}
