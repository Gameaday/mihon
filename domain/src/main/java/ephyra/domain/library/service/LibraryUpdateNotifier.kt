package ephyra.domain.library.service

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga

interface LibraryUpdateNotifier {
    suspend fun showProgressNotification(manga: List<Manga>, current: Int, total: Int)
    fun showQueueSizeWarningNotificationIfNeeded(mangaToUpdate: List<LibraryManga>)

    /**
     * Shows a notification listing titles that failed to update, with an action to open
     * the full error log.
     *
     * @param failed  Number of entries that failed to update.
     * @param uriString  String form of the URI for the error log file.  Represented as a String
     *   at the domain boundary so that this interface remains free of `android.*` imports.
     */
    fun showUpdateErrorNotification(failed: Int, uriString: String)
    suspend fun showUpdateNotifications(updates: List<Pair<Manga, Array<Chapter>>>)
    fun cancelProgressNotification()
    suspend fun showSourceHealthNotification(deadManga: List<Manga>, degradedManga: List<Manga>)
    suspend fun showMigrationSuggestionNotification(persistentlyDeadManga: List<Manga>)
}
