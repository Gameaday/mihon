package ephyra.feature.webview

/**
 * All user intents for the WebView screen.
 * Events must not carry Android framework types (Context, Activity, View).
 * For side-effects that require Activity context, [WebViewScreenModel] emits a [WebViewEffect].
 */
sealed interface WebViewScreenEvent {
    /** Share the current page URL via the system chooser. */
    data class ShareWebpage(val url: String) : WebViewScreenEvent

    /** Open the current page URL in the default browser. */
    data class OpenInBrowser(val url: String) : WebViewScreenEvent

    /** Clear cookies for the given [url]. */
    data class ClearCookies(val url: String) : WebViewScreenEvent
}

/**
 * One-shot UI side-effects emitted by [WebViewScreenModel].
 * The composable collects these via [WebViewScreenModel.effectFlow] and calls
 * Activity-context-dependent APIs (startActivity, openInBrowser) from the UI layer.
 */
sealed interface WebViewEffect {
    /** Trigger the system share chooser for [url]. */
    data class ShareWebpage(val url: String) : WebViewEffect

    /** Open [url] in the default browser. */
    data class OpenInBrowser(val url: String) : WebViewEffect
}
