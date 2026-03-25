package ephyra.app.crash

import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import ephyra.presentation.crash.CrashScreen
import ephyra.app.ui.base.activity.BaseActivity
import ephyra.app.ui.main.MainActivity
import ephyra.app.util.view.setComposeContent

class CrashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val exception = GlobalExceptionHandler.getThrowableFromIntent(intent)
        setComposeContent {
            CrashScreen(
                exception = exception,
                onRestartClick = {
                    finishAffinity()
                    startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                },
            )
        }
    }
}
