package ephyra.feature.reader

import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.Viewer

/**
 * All user intents processed by [ReaderViewModel].
 * [ReaderViewModel.onEvent] is the sole external mutation entry-point.
 *
 * The following remain public because they *return values* used by the Activity:
 *   • `getSource()` — returns [HttpSource] for web-view navigation
 *   • `getChapterUrl()` — returns URL String for share/open-in-browser
 *   • `getMangaReadingMode()` — returns Int for toolbar state display
 *   • `getMangaOrientation()` — returns Int for toolbar state display
 *   • `toggleCropBorders()` — returns Boolean used to update local icon state
 *   • `needsInit()` — returns Boolean guard before init()
 *   • `init()` / `preload()` / `loadNextChapter()` / `loadPreviousChapter()` — suspend functions
 *     called by the Activity inside coroutines with their return values consumed
 */
sealed interface ReaderEvent {
    data object ActivityFinish : ReaderEvent
    data class ViewerLoaded(val viewer: Viewer?) : ReaderEvent
    data class PageSelected(val page: ReaderPage) : ReaderEvent
    data object RestartReadTimer : ReaderEvent
    data object ToggleChapterBookmark : ReaderEvent
    data class SetMangaReadingMode(val readingMode: ReadingMode) : ReaderEvent
    data class SetMangaOrientationType(val orientation: ReaderOrientation) : ReaderEvent
    data object ToggleCropBorders : ReaderEvent
    data class ShowMenus(val visible: Boolean) : ReaderEvent
    data object ShowLoadingDialog : ReaderEvent
    data object OpenReadingModeSelectDialog : ReaderEvent
    data object OpenOrientationModeSelectDialog : ReaderEvent
    data class OpenPageDialog(val page: ReaderPage) : ReaderEvent
    data object OpenSettingsDialog : ReaderEvent
    data object CloseDialog : ReaderEvent
    data class SetBrightnessOverlayValue(val value: Int) : ReaderEvent
    data object SaveImage : ReaderEvent
    data class ShareImage(val copyToClipboard: Boolean) : ReaderEvent
    data object SetAsCover : ReaderEvent
    data object BlockPage : ReaderEvent
    data class UnblockPage(val hex: String) : ReaderEvent
}
