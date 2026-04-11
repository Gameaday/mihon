@file:Suppress("ktlint:standard:filename")

package ephyra.app.ui.base.delegate

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.domain.base.BasePreferences
import ephyra.feature.security.UnlockActivity
import ephyra.presentation.core.ui.delegate.SecureActivityDelegateState
import ephyra.presentation.core.util.system.AuthenticatorUtil
import ephyra.presentation.core.util.system.AuthenticatorUtil.isAuthenticationSupported
import ephyra.presentation.core.util.view.overrideTransitionCompat
import ephyra.presentation.core.util.view.setSecureScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate as CoreSecureActivityDelegate

class SecureActivityDelegateImpl(
    private val preferences: BasePreferences,
    private val securityPreferences: SecurityPreferences,
) : CoreSecureActivityDelegate, DefaultLifecycleObserver {

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
        if (!securityPreferences.useAuthenticator().getSync()) return
        if (activity.isAuthenticationSupported()) {
            if (!SecureActivityDelegateState.requireUnlock) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            activity.overrideTransitionCompat(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            securityPreferences.useAuthenticator().set(false)
        }
    }
}
