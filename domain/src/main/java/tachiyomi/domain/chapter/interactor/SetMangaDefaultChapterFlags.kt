package tachiyomi.domain.chapter.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga

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
