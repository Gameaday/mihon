package ephyra.feature.manga

import android.content.Context
import android.net.Uri

sealed interface MangaCoverScreenEvent {
    data class SaveCover(val context: Context) : MangaCoverScreenEvent
    data class ShareCover(val context: Context) : MangaCoverScreenEvent
    data class EditCover(val context: Context, val data: Uri) : MangaCoverScreenEvent
    data class DeleteCustomCover(val context: Context) : MangaCoverScreenEvent
    data class SetCoverFromUrl(val context: Context, val coverUrl: String, val sourceId: Long) : MangaCoverScreenEvent
}
