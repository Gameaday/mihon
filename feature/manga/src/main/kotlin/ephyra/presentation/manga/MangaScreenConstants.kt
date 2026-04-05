package ephyra.feature.manga.presentation

import ephyra.presentation.core.util.manga.DownloadAction

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
