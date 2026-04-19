package ephyra.feature.manga

import android.net.Uri

/**
 * All user intents for the manga cover screen.
 * Events must not carry Android framework types (Context, Activity, View).
 * For side-effects requiring Activity context, [MangaCoverScreenModel] emits a [MangaCoverEffect].
 */
sealed interface MangaCoverScreenEvent {
    /** Save the current cover to the device Pictures folder. */
    data object SaveCover : MangaCoverScreenEvent

    /** Share the current cover via the system chooser. */
    data object ShareCover : MangaCoverScreenEvent

    /** Replace the cover with a locally-picked image at [data]. */
    data class EditCover(val data: Uri) : MangaCoverScreenEvent

    /** Delete the custom cover, reverting to the source cover. */
    data object DeleteCustomCover : MangaCoverScreenEvent

    /** Download and set the cover from a remote [coverUrl] owned by [sourceId]. */
    data class SetCoverFromUrl(val coverUrl: String, val sourceId: Long) : MangaCoverScreenEvent
}

/**
 * One-shot UI side-effects emitted by [MangaCoverScreenModel].
 * Collected by the composable to perform Activity-context-dependent operations.
 */
sealed interface MangaCoverEffect {
    /** Launch the system share chooser with the cached cover at [uri]. */
    data class StartShare(val uri: Uri) : MangaCoverEffect
}
