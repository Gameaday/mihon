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
 *
 * ### Kotlin class delegation and Koin
 * [SecureActivityDelegate] and [ThemingDelegate] are delegated using Kotlin's `by` syntax,
 * which requires a concrete instance at the declaration site.  Koin's `inject()` returns a
 * `Lazy<T>` — not a `T` — and therefore cannot be used directly in a `by` delegation clause.
 * `KoinJavaComponent.get()` is the idiomatic Koin solution for this specific pattern: it is
 * resolved eagerly at the time the class is instantiated, which always occurs after
 * `App.onCreate()` / `startKoin()`, so Koin is guaranteed to be ready for any activity that
 * is started normally.  [GlobalExceptionHandler] guards against the pre-Koin window by
 * checking `GlobalContext.getOrNull()` before launching [ephyra.app.crash.CrashActivity].
 * This usage is intentional and does not require further refactoring.
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
