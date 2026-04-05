package ephyra.presentation.core.ui.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate
import ephyra.presentation.core.util.system.prepareTabletUiContext
import org.koin.android.ext.android.inject

/**
 * Common foundation for all activities in the application.
 *
 * This class provides standard functionality for theming, security, and tablet UI support.
 * By residing in the presentation-core module, it can be shared across all UI features
 * without having a dependency on the main application module.
 */
open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by org.koin.java.KoinJavaComponent.get(SecureActivityDelegate::class.java),
    ThemingDelegate by org.koin.java.KoinJavaComponent.get(ThemingDelegate::class.java) {

    private val uiPreferences: UiPreferences by inject()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext(uiPreferences))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }
}
