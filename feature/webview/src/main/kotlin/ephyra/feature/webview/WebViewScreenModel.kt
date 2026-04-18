package ephyra.feature.webview

import android.content.Context
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam

@Factory
class WebViewScreenModel(
    @InjectedParam val sourceId: Long?,
    private val sourceManager: SourceManager,
    private val network: NetworkHelper,
) : ScreenModel {

    var headers = emptyMap<String, String>()

    init {
        sourceId?.let { sourceManager.get(it) as? HttpSource }?.let { source ->
            try {
                headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
            }
        }
    }

    fun onEvent(event: WebViewScreenEvent) {
        when (event) {
            is WebViewScreenEvent.ShareWebpage -> shareWebpage(event.context, event.url)
            is WebViewScreenEvent.OpenInBrowser -> openInBrowser(event.context, event.url)
            is WebViewScreenEvent.ClearCookies -> clearCookies(event.url)
        }
    }

    private fun shareWebpage(context: Context, url: String) {
        try {
            context.startActivity(url.toUri().toShareIntent(context, type = "text/plain"))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to share webpage: $url" }
            context.toast(e.message)
        }
    }

    private fun openInBrowser(context: Context, url: String) {
        context.openInBrowser(url, forceDefaultBrowser = true)
    }

    private fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }
}
