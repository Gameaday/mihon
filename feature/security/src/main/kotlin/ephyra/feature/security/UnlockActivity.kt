package ephyra.feature.security

import android.os.Bundle
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.fragment.app.FragmentActivity
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.logcat
import ephyra.i18n.MR
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.ui.delegate.SecureActivityDelegateState
import ephyra.presentation.core.util.system.AuthenticatorUtil
import ephyra.presentation.core.util.system.AuthenticatorUtil.startAuthentication
import logcat.LogPriority

/**
 * Blank activity with a BiometricPrompt.
 */
class UnlockActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAuthentication(
            stringResource(MR.strings.unlock_app_title, stringResource(MR.strings.app_name)),
            confirmationRequired = false,
            callback = object : AuthenticatorUtil.AuthenticationCallback() {
                override fun onAuthenticationError(
                    activity: FragmentActivity?,
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(activity, errorCode, errString)
                    logcat(LogPriority.ERROR) { errString.toString() }
                    finishAffinity()
                }

                override fun onAuthenticationSucceeded(
                    activity: FragmentActivity?,
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(activity, result)
                    SecureActivityDelegateState.unlock()
                    finish()
                }
            },
        )
    }
}
