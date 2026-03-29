package ephyra.feature.webview

import android.content.Context
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.ScreenModel
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager

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

    fun shareWebpage(context: Context, url: String) {
        try {
            context.startActivity(url.toUri().toShareIntent(context, type = "text/plain"))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        context.openInBrowser(url, forceDefaultBrowser = true)
    }

    fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }
}
