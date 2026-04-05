package ephyra.feature.settings.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.util.view.setComposeContent
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

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (intent != null) {
            startActivity(intent)
        }
    }
}
