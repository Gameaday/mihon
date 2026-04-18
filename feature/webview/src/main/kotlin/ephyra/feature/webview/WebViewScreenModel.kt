package ephyra.feature.webview

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val effectChannel = Channel<WebViewEffect>(Channel.BUFFERED)

    /** One-shot UI side-effects to be collected by the composable. */
    val effectFlow = effectChannel.receiveAsFlow()

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
            is WebViewScreenEvent.ShareWebpage -> shareWebpage(event.url)
            is WebViewScreenEvent.OpenInBrowser -> openInBrowser(event.url)
            is WebViewScreenEvent.ClearCookies -> clearCookies(event.url)
        }
    }

    private fun shareWebpage(url: String) {
        effectChannel.trySend(WebViewEffect.ShareWebpage(url))
    }

    private fun openInBrowser(url: String) {
        effectChannel.trySend(WebViewEffect.OpenInBrowser(url))
    }

    private fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }
}

