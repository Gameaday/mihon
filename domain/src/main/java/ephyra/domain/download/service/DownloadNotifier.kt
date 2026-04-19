package ephyra.domain.download.service

import ephyra.domain.download.model.Download

interface DownloadNotifier {
    fun dismissProgress()
    suspend fun onProgressChange(download: Download)
    fun onPaused()
    fun onComplete()

    /**
     * Shows a warning notification.
     *
     * The `PendingIntent` for the notification tap action is not part of the domain interface
     * (which must remain free of `android.*` imports). Implementations create their own
     * platform-specific content intent from [mangaId] when provided.
     *
     * @param reason  Human-readable warning text.
     * @param timeout  If non-null, auto-dismiss the notification after this many milliseconds.
     * @param mangaId  When non-null, an "Open manga" action button is added to the notification.
     */
    fun onWarning(reason: String, timeout: Long? = null, mangaId: Long? = null)
    fun onError(error: String? = null, chapter: String? = null, mangaTitle: String? = null, mangaId: Long? = null)
}
