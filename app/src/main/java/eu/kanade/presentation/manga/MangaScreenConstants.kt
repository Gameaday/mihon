package eu.kanade.presentation.manga

enum class DownloadAction {
    NEXT_1_CHAPTER,
    NEXT_5_CHAPTERS,
    NEXT_10_CHAPTERS,
    NEXT_25_CHAPTERS,
    UNREAD_CHAPTERS,
    BOOKMARKED_CHAPTERS,

    /** Download all chapters to local storage with Jellyfin-compatible naming, then trigger a library scan. */
    SYNC_TO_JELLYFIN,

    /** Download only the read chapters that Jellyfin is missing (fill gaps in JF library). */
    SYNC_READ_TO_JELLYFIN,

    /** Download ALL chapters that Jellyfin is missing (complete the JF library). */
    SYNC_ALL_TO_JELLYFIN,
}

enum class EditCoverAction {
    EDIT,
    DELETE,
    SEARCH,
}

enum class MangaScreenItem {
    INFO_BOX,
    ACTION_ROW,
    SOURCE_HEALTH_BANNER,
    DESCRIPTION_WITH_TAG,
    CHAPTER_HEADER,
    CHAPTER,
}
