package ephyra.feature.settings.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import ephyra.app.data.track.TrackerManager
import ephyra.app.ui.base.activity.BaseActivity
import ephyra.app.ui.main.MainActivity
import ephyra.app.util.view.setComposeContent
import ephyra.presentation.core.screens.LoadingScreen
import org.koin.android.ext.android.inject

abstract class BaseOAuthLoginActivity : BaseActivity() {

    internal val trackerManager: TrackerManager by inject()

    abstract fun handleResult(uri: Uri)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            LoadingScreen()
        }

        val data = intent.data
        if (data == null) {
            returnToSettings()
        } else {
            handleResult(data)
        }
    }

    internal fun returnToSettings() {
        finish()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
