package ephyra.domain.chapter.interactor

import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.model.Manga

class SetMangaDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val getFavorites: GetFavorites,
) {

    suspend fun await(manga: Manga) {
        withNonCancellableContext {
            with(libraryPreferences) {
                // Don't apply "show only read" as the default unread filter for manga being
                // added to the library (manga.favorite == false). Applying it would hide all
                // unread chapters, making new chapters invisible in the manga screen.
                val unreadFilter = filterChapterByRead().get().let { storedFilter ->
                    if (!manga.favorite && storedFilter == Manga.CHAPTER_SHOW_READ) Manga.SHOW_ALL else storedFilter
                }
                setMangaChapterFlags.awaitSetAllFlags(
                    mangaId = manga.id,
                    unreadFilter = unreadFilter,
                    downloadedFilter = filterChapterByDownloaded().get(),
                    bookmarkedFilter = filterChapterByBookmarked().get(),
                    sortingMode = sortChapterBySourceOrNumber().get(),
                    sortingDirection = sortChapterByAscendingOrDescending().get(),
                    displayMode = displayChapterByNameOrNumber().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
