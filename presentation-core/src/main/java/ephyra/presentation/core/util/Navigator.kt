package ephyra.presentation.core.util

import android.content.Context

/**
 * A generic navigator interface to decouple feature modules from the main application
 * navigation logic. This allows features like the reader to request navigation to
 * screens (e.g., Manga details or WebViews) without having a direct dependency on
 * the Activities residing in the app module.
 */
interface Navigator {
    /**
     * Navigates to the manga details screen.
     */
    fun openMangaScreen(context: Context, mangaId: Long)

    /**
     * Navigates to a web view for a given URL and source.
     */
    fun openWebView(context: Context, url: String, sourceId: Long, title: String)
}

interface AssistContentScreen {
    fun onProvideAssistUrl(): String?
}
