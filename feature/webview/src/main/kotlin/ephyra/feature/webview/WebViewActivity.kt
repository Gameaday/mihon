package ephyra.feature.webview

import android.app.Activity
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import ephyra.core.common.util.system.WebViewUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager
import ephyra.i18n.MR
import ephyra.presentation.core.R
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import ephyra.presentation.core.util.view.overrideTransitionCompat
import ephyra.presentation.core.util.view.setComposeContent
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.android.ext.android.inject

class WebViewActivity : BaseActivity() {

    private val sourceManager: SourceManager by inject()
    private val network: NetworkHelper by inject()

    private var assistUrl: String? = null

    init {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        overrideTransitionCompat(
            OVERRIDE_TRANSITION_OPEN,
            R.anim.shared_axis_x_push_enter,
            R.anim.shared_axis_x_push_exit,
        )
        super.onCreate(savedInstanceState)

        if (!WebViewUtil.supportsWebView(this)) {
            toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        val url = intent.extras?.getString(URL_KEY) ?: return
        assistUrl = url

        var headers = emptyMap<String, String>()
        (sourceManager.get(intent.extras!!.getLong(SOURCE_KEY)) as? HttpSource)?.let { source ->
            try {
                headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
            }
        }

        setComposeContent {
            WebViewScreenContent(
                onNavigateUp = { finish() },
                initialTitle = intent.extras?.getString(TITLE_KEY),
                url = url,
                headers = headers,
                onUrlChange = { assistUrl = it },
                onShare = this::shareWebpage,
                onOpenInBrowser = this::openInBrowser,
                onClearCookies = this::clearCookies,
            )
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    override fun finish() {
        super.finish()
        overrideTransitionCompat(
            OVERRIDE_TRANSITION_CLOSE,
            R.anim.shared_axis_x_pop_enter,
            R.anim.shared_axis_x_pop_exit,
        )
    }

    private fun shareWebpage(url: String) {
        try {
            startActivity(url.toUri().toShareIntent(this, type = "text/plain"))
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    private fun openInBrowser(url: String) {
        openInBrowser(url, forceDefaultBrowser = true)
    }

    private fun clearCookies(url: String) {
        val cleared = network.cookieJar.remove(url.toHttpUrl())
        logcat { "Cleared $cleared cookies for: $url" }
    }

    companion object {
        private const val URL_KEY = "url_key"
        private const val SOURCE_KEY = "source_key"
        private const val TITLE_KEY = "title_key"

        fun newIntent(context: Context, url: String, sourceId: Long? = null, title: String? = null): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(URL_KEY, url)
                putExtra(SOURCE_KEY, sourceId)
                putExtra(TITLE_KEY, title)
            }
        }
    }
}
