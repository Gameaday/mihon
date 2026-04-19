package ephyra.feature.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.util.system.logcat
import ephyra.presentation.core.util.AssistContentScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import logcat.LogPriority
import org.koin.core.parameter.parametersOf

class WebViewScreen(
    private val url: String,
    private val initialTitle: String? = null,
    private val sourceId: Long? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = koinScreenModel<WebViewScreenModel> { parametersOf(sourceId) }

        // Collect one-shot UI effects that require Activity context
        LaunchedEffect(screenModel) {
            screenModel.effectFlow.collect { effect ->
                when (effect) {
                    is WebViewEffect.ShareWebpage -> {
                        try {
                            context.startActivity(
                                effect.url.toUri().toShareIntent(context, type = "text/plain"),
                            )
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to share webpage: ${effect.url}" }
                            context.toast(e.message)
                        }
                    }
                    is WebViewEffect.OpenInBrowser -> {
                        context.openInBrowser(effect.url, forceDefaultBrowser = true)
                    }
                }
            }
        }

        WebViewScreenContent(
            onNavigateUp = { navigator.pop() },
            initialTitle = initialTitle,
            url = url,
            headers = screenModel.headers,
            onUrlChange = { assistUrl = it },
            onShare = { screenModel.onEvent(WebViewScreenEvent.ShareWebpage(it)) },
            onOpenInBrowser = { screenModel.onEvent(WebViewScreenEvent.OpenInBrowser(it)) },
            onClearCookies = { screenModel.onEvent(WebViewScreenEvent.ClearCookies(it)) },
        )
    }
}
