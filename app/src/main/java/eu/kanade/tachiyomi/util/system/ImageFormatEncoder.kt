package eu.kanade.tachiyomi.util.system

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlChannelsConfiguration
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlDecodingSpeed
import com.awxkee.jxlcoder.JxlEffort
import tachiyomi.domain.library.service.LibraryPreferences.ImageFormat
import java.io.OutputStream

/**
 * Returns a lossless encoder for persisting images to disk (covers, splits, merges).
 *
 * JXL uses effort 7 ("squirrel") — best compression ratio for stored files.
 * WebP uses Android's built-in lossless encoder.
 */
fun ImageFormat.encoder(): (Bitmap, OutputStream) -> Unit = when (this) {
    ImageFormat.WebP -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
    }
    ImageFormat.JXL -> { bitmap, os ->
        os.write(jxlEncode(bitmap, JxlEffort.SQUIRREL, JxlDecodingSpeed.SLOWEST))
    }
}

/**
 * Returns a fast lossless encoder for transient in-memory buffers (reader display).
 *
 * JXL uses effort 1 ("lightning") — fastest encode, still lossless.
 * WebP uses Android's built-in lossless encoder (no effort control available).
 */
fun ImageFormat.fastEncoder(): (Bitmap, OutputStream) -> Unit = when (this) {
    ImageFormat.WebP -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
    }
    ImageFormat.JXL -> { bitmap, os ->
        os.write(jxlEncode(bitmap, JxlEffort.LIGHTNING, JxlDecodingSpeed.FASTEST))
    }
}

private fun jxlEncode(bitmap: Bitmap, effort: JxlEffort, decodingSpeed: JxlDecodingSpeed): ByteArray {
    return JxlCoder.encode(
        bitmap,
        channelsConfiguration = if (bitmap.hasAlpha()) {
            JxlChannelsConfiguration.RGBA
        } else {
            JxlChannelsConfiguration.RGB
        },
        compressionOption = JxlCompressionOption.LOSSLESS,
        effort = effort,
        decodingSpeed = decodingSpeed,
    )
}
