package ephyra.feature.webview

import android.content.Context

sealed interface WebViewScreenEvent {
    data class ShareWebpage(val context: Context, val url: String) : WebViewScreenEvent
    data class OpenInBrowser(val context: Context, val url: String) : WebViewScreenEvent
    data class ClearCookies(val url: String) : WebViewScreenEvent
}
