package ephyra.feature.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.presentation.core.util.AssistContentScreen
import ephyra.presentation.core.util.Screen
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

        WebViewScreenContent(
            onNavigateUp = { navigator.pop() },
            initialTitle = initialTitle,
            url = url,
            headers = screenModel.headers,
            onUrlChange = { assistUrl = it },
            onShare = { screenModel.onEvent(WebViewScreenEvent.ShareWebpage(context, it)) },
            onOpenInBrowser = { screenModel.onEvent(WebViewScreenEvent.OpenInBrowser(context, it)) },
            onClearCookies = { screenModel.onEvent(WebViewScreenEvent.ClearCookies(it)) },
        )
    }
}
