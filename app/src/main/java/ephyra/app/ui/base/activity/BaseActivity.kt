package ephyra.app.ui.base.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ephyra.app.ui.base.delegate.SecureActivityDelegate
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.domain.ui.UiPreferences
import org.koin.android.ext.android.inject

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by org.koin.android.ext.android.get(SecureActivityDelegate::class.java),
    ThemingDelegate by org.koin.android.ext.android.get(ThemingDelegate::class.java) {

    private val uiPreferences: UiPreferences by inject()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext(uiPreferences))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }
}
