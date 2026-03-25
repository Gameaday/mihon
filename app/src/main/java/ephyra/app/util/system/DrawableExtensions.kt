package ephyra.app.util.system

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import coil3.size.ScaleDrawable

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
