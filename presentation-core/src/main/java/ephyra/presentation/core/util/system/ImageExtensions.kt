package ephyra.presentation.core.util.system

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import coil3.size.ScaleDrawable
import ephyra.domain.library.service.LibraryPreferences.ImageFormat
import java.io.OutputStream

/**
 * Returns a lossless encoder for **persisting** images to disk (covers, download splits, merges).
 *
 * WebP lossless uses Android's native encoder — fast to encode, produces files ~25 % smaller
 * than PNG, and has hardware-accelerated decoding on all Android devices for efficient loading.
 * PNG uses Android's built-in encoder — universal compatibility.
 */
fun ImageFormat.encoder(): (Bitmap, OutputStream) -> Unit = when (this) {
    ImageFormat.PNG -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    }

    ImageFormat.WEBP -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
    }
}

fun Drawable.getBitmapOrNull(): Bitmap? {
    val raw = when (this) {
        is BitmapDrawable -> bitmap
        is ScaleDrawable -> child.toBitmap()
        else -> null
    } ?: return null
    // HARDWARE bitmaps are GPU-resident and immutable; compress() and setLargeIcon()
    // require a software-backed bitmap, so copy to ARGB_8888 when necessary.
    return if (raw.config == Bitmap.Config.HARDWARE) {
        raw.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        raw
    }
}
