package ephyra.app.ui.base.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ephyra.app.ui.base.delegate.SecureActivityDelegate
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegate
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.app.util.system.prepareTabletUiContext

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }
}
