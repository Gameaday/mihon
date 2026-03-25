package ephyra.feature.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.core.parameter.parametersOf
import ephyra.presentation.util.AssistContentScreen
import ephyra.presentation.util.Screen
import ephyra.presentation.webview.WebViewScreenContent

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
            onShare = { screenModel.shareWebpage(context, it) },
            onOpenInBrowser = { screenModel.openInBrowser(context, it) },
            onClearCookies = screenModel::clearCookies,
        )
    }
}
