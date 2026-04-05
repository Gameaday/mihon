package ephyra.presentation.core.data.coil

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import coil3.size.pxOrElse
import ephyra.core.common.util.system.ImageUtil
import okio.BufferedSource
import tachiyomi.decoder.ImageDecoder

/**
 * A [Decoder] that uses the native [ImageDecoder] library to decode JXL images,
 * which are not supported by the Android platform even at API 34+.
 *
 * AVIF and HEIF are handled by the platform's [android.graphics.ImageDecoder] at API 34+,
 * so they no longer need this custom decoder.
 */
class TachiyomiImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    override suspend fun decode(): DecodeResult {
        val decoder = resources.sourceOrNull()?.use {
            ImageDecoder.newInstance(it.inputStream(), true, displayProfile)
        }

        check(decoder != null && decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder" }

        val srcWidth = decoder.width
        val srcHeight = decoder.height

        val dstWidth = options.size.width.pxOrElse { srcWidth }
        val dstHeight = options.size.height.pxOrElse { srcHeight }

        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        var bitmap = decoder.decode(sampleSize = sampleSize)
        decoder.recycle()

        check(bitmap != null) { "Failed to decode image" }

        if (options.bitmapConfig == Bitmap.Config.HARDWARE && ImageUtil.canUseHardwareBitmap(bitmap)) {
            val hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
            if (hwBitmap != null) {
                bitmap.recycle()
                bitmap = hwBitmap
            }
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (isApplicable(result.source.source())) {
                TachiyomiImageDecoder(result.source, options)
            } else {
                null
            }
        }

        /**
         * Only intercept JXL images — AVIF and HEIF are handled natively at API 34+.
         */
        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().use {
                ImageUtil.findImageType(it)
            }
            return type == ImageUtil.ImageType.JXL
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }

    companion object {
        var displayProfile: ByteArray? = null
    }
}
