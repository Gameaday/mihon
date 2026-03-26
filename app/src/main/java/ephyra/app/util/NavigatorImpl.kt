package ephyra.app.util

import android.content.Context
import android.content.Intent
import ephyra.app.ui.main.MainActivity
import ephyra.feature.webview.WebViewActivity
import ephyra.core.common.Constants
import ephyra.presentation.core.util.Navigator

/**
 * Concrete implementation of the Navigator interface that bridges feature modules with
 * application-level activity navigation. Since these activities reside in the app
 * module, this implementation must also live here to resolve the target classes.
 */
class NavigatorImpl : Navigator {
    override fun openMangaScreen(context: Context, mangaId: Long) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = Constants.SHORTCUT_MANGA
                putExtra(Constants.MANGA_EXTRA, mangaId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    override fun openWebView(context: Context, url: String, sourceId: Long, title: String) {
        val intent = WebViewActivity.newIntent(context, url, sourceId, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
