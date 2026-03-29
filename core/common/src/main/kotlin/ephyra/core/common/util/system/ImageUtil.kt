package ephyra.core.common.util.system

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.blue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.hippo.unifile.UniFile
import ephyra.core.common.util.system.GLUtil
import ephyra.core.common.util.system.ImageUtil.STUB_MAX_HEIGHT_FRACTION
import ephyra.core.common.util.system.ImageUtil.STUB_MIN_HEIGHT_FRACTION
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false

        val extension = name.substringAfterLast('.')
        return ImageType.entries.any { it.extension == extension } || openStream?.let { findImageType(it) } != null
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        return try {
            val bytes = readMagicBytes(stream) ?: return null
            detectImageType(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { ImageType.entries.find { it.mime == mime } } ?: findImageType(openStream)
        return type?.extension ?: "jpg"
    }

    fun isAnimatedAndSupported(source: BufferedSource): Boolean {
        return try {
            val bytes = readMagicBytes(source.peek().inputStream()) ?: return false
            val type = detectImageType(bytes) ?: return false
            when (type) {
                ImageType.GIF -> true
                ImageType.WEBP -> isAnimatedWebP(bytes)
                ImageType.HEIF -> isAnimatedHeif(bytes)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read up to 32 bytes from [stream] for magic-byte detection.
     * If the stream supports mark/reset the position is restored.
     */
    private fun readMagicBytes(stream: InputStream): ByteArray? {
        val bytes = ByteArray(32)
        val length = if (stream.markSupported()) {
            stream.mark(bytes.size)
            stream.read(bytes, 0, bytes.size).also { stream.reset() }
        } else {
            stream.read(bytes, 0, bytes.size)
        }
        return if (length == -1) null else bytes
    }

    /**
     * Detect image type from the first 32 bytes of the file using magic byte signatures.
     */
    private fun detectImageType(bytes: ByteArray): ImageType? {
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        ) {
            return ImageType.PNG
        }

        // GIF: "GIF87a" or "GIF89a"
        if (bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() &&
            (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) &&
            bytes[5] == 0x61.toByte()
        ) {
            return ImageType.GIF
        }

        // JPEG: FF D8 FF
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return ImageType.JPEG
        }

        // WebP: "RIFF" at 0..3 and "WEBP" at 8..11
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) {
            return ImageType.WEBP
        }

        // JXL: FF 0A (bare codestream) or 00 00 00 0C 4A 58 4C 20 (ISOBMFF container)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0x0A.toByte()) {
            return ImageType.JXL
        }
        if (bytes.size >= 12 &&
            bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
            bytes[2] == 0x00.toByte() && bytes[3] == 0x0C.toByte() &&
            bytes[4] == 0x4A.toByte() && bytes[5] == 0x58.toByte() &&
            bytes[6] == 0x4C.toByte() && bytes[7] == 0x20.toByte() &&
            bytes[8] == 0x0D.toByte() && bytes[9] == 0x0A.toByte() &&
            bytes[10] == 0x87.toByte() && bytes[11] == 0x0A.toByte()
        ) {
            return ImageType.JXL
        }

        // AVIF / HEIF: ISOBMFF boxes — "ftyp" at offset 4, scan major + compatible brands
        if (bytes.size >= 12 &&
            bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
        ) {
            return detectIsobmffType(bytes)
        }

        return null
    }

    /**
     * Check if the WebP image (from its first 32 bytes) is animated.
     * Animated WebP uses "VP8X" chunk with the animation flag (bit 1 of byte 20).
     */
    private fun isAnimatedWebP(bytes: ByteArray): Boolean {
        if (bytes.size < 21) return false
        // VP8X chunk starts at byte 12: "VP8X"
        if (bytes[12] == 0x56.toByte() && bytes[13] == 0x50.toByte() &&
            bytes[14] == 0x38.toByte() && bytes[15] == 0x58.toByte()
        ) {
            // Byte 20 is the VP8X flags; bit 1 = animation
            return (bytes[20].toInt() and 0x02) != 0
        }
        return false
    }

    /**
     * Check if the HEIF/AVIF image is animated by scanning the ftyp box for
     * sequence brands (`avis`, `msf1`) in both major and compatible brand lists.
     */
    private fun isAnimatedHeif(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val animationBrands = setOf("avis", "msf1")
        return collectFtypBrands(bytes).any { it in animationBrands }
    }

    /**
     * Detect AVIF vs HEIF by scanning both the major brand and all compatible brands
     * from the ISOBMFF `ftyp` box. Many files use a generic major brand like `mif1`
     * with the actual format brand (e.g. `avif`, `heic`) only in compatible brands.
     */
    private fun detectIsobmffType(bytes: ByteArray): ImageType? {
        val avifBrands = setOf("avif", "avis")
        val heifBrands = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
        val brands = collectFtypBrands(bytes)
        // Check AVIF first — a file that lists both avif and mif1 is AVIF
        if (brands.any { it in avifBrands }) return ImageType.AVIF
        if (brands.any { it in heifBrands }) return ImageType.HEIF
        return null
    }

    /**
     * Collect all 4-byte brand strings from an ISOBMFF `ftyp` box:
     * major brand (bytes 8..11) plus every compatible brand (bytes 16..N).
     * Callers must have verified `ftyp` signature at bytes 4..7.
     */
    private fun collectFtypBrands(bytes: ByteArray): List<String> {
        // Box size is a big-endian uint32 at bytes 0..3
        val boxSize = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        // Clamp to available data
        val end = minOf(boxSize, bytes.size)
        return buildList {
            // Major brand at offset 8
            if (bytes.size >= 12) {
                add(String(bytes, 8, 4, Charsets.ISO_8859_1))
            }
            // Compatible brands start at offset 16, each 4 bytes
            var offset = 16
            while (offset + 4 <= end) {
                add(String(bytes, offset, 4, Charsets.ISO_8859_1))
                offset += 4
            }
        }
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }

    /**
     * Check whether the image is wide (which we consider a double-page spread).
     *
     * @return true if the width is greater than the height
     */
    fun isWideImage(imageSource: BufferedSource): Boolean {
        val options = extractImageOptions(imageSource)
        return options.outWidth > options.outHeight
    }

    /**
     * Lossless encoder used only for **persistent** disk operations (download tall-image splits).
     *
     * Reader transforms (split, rotate, merge) return [Bitmap] directly — no encoding is
     * needed since [SubsamplingScaleImageView] accepts bitmaps via [ImageSource.bitmap].
     *
     * Callers that persist to disk should supply their own encoder via
     * [ImageFormat.encoder()][tachiyomi.domain.library.service.LibraryPreferences.ImageFormat].
     */
    val defaultEncoder: (Bitmap, OutputStream) -> Unit = { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    }

    /**
     * Extract the [side] half from [imageSource] and return it as a [Bitmap].
     *
     * Uses [android.graphics.ImageDecoder] with [setCrop][android.graphics.ImageDecoder.setCrop]
     * so only the requested half is decoded — halving peak memory compared to decoding the
     * full image and copying pixels with [Canvas].
     */
    fun splitInHalf(imageSource: BufferedSource, side: Side): Bitmap {
        val bytes = imageSource.readByteArray()
        val source = android.graphics.ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.crop = when (side) {
                Side.RIGHT -> Rect(width - width / 2, 0, width, height)
                Side.LEFT -> Rect(0, 0, width / 2, height)
            }
        }
    }

    /**
     * Decode [imageSource] and rotate the resulting [Bitmap] by [degrees].
     */
    fun rotateImage(imageSource: BufferedSource, degrees: Float): Bitmap {
        val imageBitmap = BitmapFactory.decodeStream(imageSource.inputStream())
        val rotated = rotateBitMap(imageBitmap, degrees)
        imageBitmap.recycle()
        return rotated
    }

    /**
     * If [imageSource] is a wide (double-page spread) image, rotate it by [degrees]
     * and return the resulting [Bitmap]; otherwise return `null`.
     */
    fun rotateDualPageIfWide(imageSource: BufferedSource, degrees: Float): Bitmap? =
        if (isWideImage(imageSource)) rotateImage(imageSource, degrees) else null

    private fun rotateBitMap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Split the image into left and right parts, then merge them into a new [Bitmap]
     * with the [upperSide] half on top.
     */
    fun splitAndMerge(imageSource: BufferedSource, upperSide: Side): Bitmap {
        val imageBitmap = BitmapFactory.decodeStream(imageSource.inputStream())
        val height = imageBitmap.height
        val width = imageBitmap.width

        val result = createBitmap(width / 2, height * 2)
        result.applyCanvas {
            // right -> upper
            val rightPart = when (upperSide) {
                Side.RIGHT -> Rect(width - width / 2, 0, width, height)
                Side.LEFT -> Rect(0, 0, width / 2, height)
            }
            val upperPart = Rect(0, 0, width / 2, height)
            drawBitmap(imageBitmap, rightPart, upperPart, null)
            // left -> bottom
            val leftPart = when (upperSide) {
                Side.LEFT -> Rect(width - width / 2, 0, width, height)
                Side.RIGHT -> Rect(0, 0, width / 2, height)
            }
            val bottomPart = Rect(0, height, width / 2, height * 2)
            drawBitmap(imageBitmap, leftPart, bottomPart, null)
        }
        imageBitmap.recycle()
        return result
    }

    /**
     * Check whether the image is a small "stub" relative to a reference image.
     * A stub has approximately the same width as the reference and a height that is
     * at least [STUB_MIN_HEIGHT_FRACTION] but strictly less than [STUB_MAX_HEIGHT_FRACTION]
     * of the reference height.
     *
     * The lower bound filters out near-empty or 1-pixel filler images that should never
     * trigger a merge; the upper bound (exclusive) rejects pages that are too large to be
     * a watermark strip.
     */
    fun isSmallPage(imageSource: BufferedSource, referenceSource: BufferedSource): Boolean {
        val options = extractImageOptions(imageSource)
        val refOptions = extractImageOptions(referenceSource)
        if (options.outWidth <= 0 || options.outHeight <= 0 || refOptions.outWidth <= 0 || refOptions.outHeight <= 0) {
            return false
        }
        val widthSimilar = abs(options.outWidth - refOptions.outWidth) <= refOptions.outWidth * 0.05f
        if (!widthSimilar) return false
        val heightFraction = options.outHeight.toFloat() / refOptions.outHeight.toFloat()
        return heightFraction in STUB_MIN_HEIGHT_FRACTION..<STUB_MAX_HEIGHT_FRACTION
    }

    /**
     * Lightweight overload that accepts a raw [InputStream] for the candidate stub so that
     * callers can check dimensions without having to buffer the entire image in memory first.
     * Only the image headers are read from [imageStream].
     */
    fun isSmallPage(imageStream: InputStream, referenceSource: BufferedSource): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeStream(imageStream, null, this)
        }
        val refOptions = extractImageOptions(referenceSource)
        if (options.outWidth <= 0 || options.outHeight <= 0 || refOptions.outWidth <= 0 || refOptions.outHeight <= 0) {
            return false
        }
        val widthSimilar = abs(options.outWidth - refOptions.outWidth) <= refOptions.outWidth * 0.05f
        if (!widthSimilar) return false
        val heightFraction = options.outHeight.toFloat() / refOptions.outHeight.toFloat()
        return heightFraction in STUB_MIN_HEIGHT_FRACTION..<STUB_MAX_HEIGHT_FRACTION
    }

    /**
     * Lightweight overload for callers that have already decoded the reference image and
     * cached its dimensions. Only the image headers are read from [imageStream]; no stream
     * is opened for the reference.
     */
    fun isSmallPage(imageStream: InputStream, refWidth: Int, refHeight: Int): Boolean {
        if (refWidth <= 0 || refHeight <= 0) return false
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeStream(imageStream, null, this)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return false
        val widthSimilar = abs(options.outWidth - refWidth) <= refWidth * 0.05f
        if (!widthSimilar) return false
        val heightFraction = options.outHeight.toFloat() / refHeight.toFloat()
        return heightFraction in STUB_MIN_HEIGHT_FRACTION..<STUB_MAX_HEIGHT_FRACTION
    }

    /**
     * Combine two images vertically, placing the second image below the first.
     * Returns a [Bitmap]; the caller decides whether to display it directly
     * (reader) or encode it for disk persistence (downloader).
     */
    fun mergePages(topSource: BufferedSource, bottomSource: BufferedSource): Bitmap {
        val topBitmap = BitmapFactory.decodeStream(topSource.inputStream())
        val bottomBitmap = BitmapFactory.decodeStream(bottomSource.inputStream())

        val width = max(topBitmap.width, bottomBitmap.width)
        val result = createBitmap(width, topBitmap.height + bottomBitmap.height)
        result.applyCanvas {
            drawBitmap(topBitmap, 0f, 0f, null)
            drawBitmap(bottomBitmap, 0f, topBitmap.height.toFloat(), null)
        }
        topBitmap.recycle()
        bottomBitmap.recycle()
        return result
    }

    enum class Side {
        RIGHT,
        LEFT,
    }

    /** Minimum height fraction for a page to be considered a watermark stub (2 % of reference). */
    private const val STUB_MIN_HEIGHT_FRACTION = 0.02f

    /** Maximum height fraction (exclusive) for a page to be considered a watermark stub (< 30 % of reference). */
    private const val STUB_MAX_HEIGHT_FRACTION = 0.3f

    /**
     * Check whether the image is considered a tall image.
     *
     * @return true if the height:width ratio is greater than 3.
     */
    private fun isTallImage(imageSource: BufferedSource): Boolean {
        val options = extractImageOptions(imageSource)
        return (options.outHeight / options.outWidth) > 3
    }

    /**
     * Splits tall images to improve performance of reader
     */
    fun splitTallImage(
        tmpDir: UniFile,
        imageFile: UniFile,
        filenamePrefix: String,
        encoder: (Bitmap, OutputStream) -> Unit = defaultEncoder,
        formatExtension: String = "png",
    ): Boolean {
        val imageSource = imageFile.openInputStream().use { Buffer().readFrom(it) }
        if (isAnimatedAndSupported(imageSource) || !isTallImage(imageSource)) {
            return true
        }

        val bitmapRegionDecoder = getBitmapRegionDecoder(imageSource.peek().inputStream())
        if (bitmapRegionDecoder == null) {
            logcat { "Failed to create new instance of BitmapRegionDecoder" }
            return false
        }

        val options = extractImageOptions(imageSource).apply {
            inJustDecodeBounds = false
        }

        val splitDataList = options.splitData

        return try {
            splitDataList.forEach { splitData ->
                val splitImageName = splitImageName(filenamePrefix, splitData.index, formatExtension)
                // Remove pre-existing split if exists (this split shouldn't exist under normal circumstances)
                tmpDir.findFile(splitImageName)?.delete()

                val splitFile = tmpDir.createFile(splitImageName)!!

                val region = Rect(0, splitData.topOffset, splitData.splitWidth, splitData.bottomOffset)

                splitFile.openOutputStream().use { outputStream ->
                    val splitBitmap = bitmapRegionDecoder.decodeRegion(region, options)
                    encoder(splitBitmap, outputStream)
                    splitBitmap.recycle()
                }
                logcat {
                    "Success: Split #${splitData.index + 1} with topOffset=${splitData.topOffset} " +
                        "height=${splitData.splitHeight} bottomOffset=${splitData.bottomOffset}"
                }
            }
            imageFile.delete()
            true
        } catch (e: Exception) {
            // Image splits were not successfully saved so delete them and keep the original image
            splitDataList
                .map { splitImageName(filenamePrefix, it.index, formatExtension) }
                .forEach { tmpDir.findFile(it)?.delete() }
            logcat(LogPriority.ERROR, e)
            false
        } finally {
            bitmapRegionDecoder.recycle()
        }
    }

    private fun splitImageName(
        filenamePrefix: String,
        index: Int,
        extension: String,
    ) = "${filenamePrefix}__${
        "%03d".format(
            Locale.ENGLISH,
            index + 1,
        )
    }.$extension"

    private val BitmapFactory.Options.splitData
        get(): List<SplitData> {
            val imageHeight = outHeight
            val imageWidth = outWidth

            // -1 so it doesn't try to split when imageHeight = optimalImageHeight
            val partCount = (imageHeight - 1) / optimalImageHeight + 1
            val optimalSplitHeight = imageHeight / partCount

            logcat {
                "Generating SplitData for image (height: $imageHeight): " +
                    "$partCount parts @ ${optimalSplitHeight}px height per part"
            }

            return buildList {
                val range = 0..<partCount
                for (index in range) {
                    // Only continue if the list is empty or there is image remaining
                    if (isNotEmpty() && imageHeight <= last().bottomOffset) break

                    val topOffset = index * optimalSplitHeight
                    var splitHeight = min(optimalSplitHeight, imageHeight - topOffset)

                    if (index == range.last) {
                        val remainingHeight = imageHeight - (topOffset + splitHeight)
                        splitHeight += remainingHeight
                    }

                    add(SplitData(index, topOffset, splitHeight, imageWidth))
                }
            }
        }

    data class SplitData(
        val index: Int,
        val topOffset: Int,
        val splitHeight: Int,
        val splitWidth: Int,
    ) {
        val bottomOffset = topOffset + splitHeight
    }

    fun canUseHardwareBitmap(bitmap: Bitmap): Boolean {
        return canUseHardwareBitmap(bitmap.width, bitmap.height)
    }

    fun canUseHardwareBitmap(imageSource: BufferedSource): Boolean {
        return with(extractImageOptions(imageSource)) {
            canUseHardwareBitmap(outWidth, outHeight)
        }
    }

    var hardwareBitmapThreshold: Int = GLUtil.SAFE_TEXTURE_LIMIT

    private fun canUseHardwareBitmap(width: Int, height: Int): Boolean {
        return maxOf(width, height) <= hardwareBitmapThreshold
    }

    /**
     * Algorithm for determining what background to accompany a comic/manga page
     */
    fun chooseBackground(context: Context, imageStream: InputStream): Drawable {
        val imageBytes = imageStream.readBytes()
        val image: Bitmap? = try {
            val source = android.graphics.ImageDecoder.createSource(ByteBuffer.wrap(imageBytes))
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            // Fallback to BitmapFactory for formats android.graphics.ImageDecoder can't handle
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        val whiteColor = Color.WHITE
        if (image == null) return ColorDrawable(whiteColor)
        if (image.width < 50 || image.height < 50) {
            image.recycle()
            return ColorDrawable(whiteColor)
        }

        val top = 5
        val bot = image.height - 5
        val left = (image.width * 0.0275).toInt()
        val right = image.width - left
        val midX = image.width / 2
        val midY = image.height / 2
        val offsetX = (image.width * 0.01).toInt()
        val leftOffsetX = left - offsetX
        val rightOffsetX = right + offsetX

        val topLeftPixel = image[left, top]
        val topRightPixel = image[right, top]
        val midLeftPixel = image[left, midY]
        val midRightPixel = image[right, midY]
        val topCenterPixel = image[midX, top]
        val botLeftPixel = image[left, bot]
        val bottomCenterPixel = image[midX, bot]
        val botRightPixel = image[right, bot]
        val topOffsetCornersIsDark = image[leftOffsetX, top].isDark() && image[rightOffsetX, top].isDark()
        val botOffsetCornersIsDark = image[leftOffsetX, bot].isDark() && image[rightOffsetX, bot].isDark()

        val topLeftIsDark = topLeftPixel.isDark()
        val topRightIsDark = topRightPixel.isDark()
        val midLeftIsDark = midLeftPixel.isDark()
        val midRightIsDark = midRightPixel.isDark()
        val topMidIsDark = topCenterPixel.isDark()
        val botLeftIsDark = botLeftPixel.isDark()
        val botRightIsDark = botRightPixel.isDark()

        var darkBG =
            (topLeftIsDark && (botLeftIsDark || botRightIsDark || topRightIsDark || midLeftIsDark || topMidIsDark)) ||
                (topRightIsDark && (botRightIsDark || botLeftIsDark || midRightIsDark || topMidIsDark))

        val topAndBotPixels =
            listOf(topLeftPixel, topCenterPixel, topRightPixel, botRightPixel, bottomCenterPixel, botLeftPixel)
        val isNotWhiteAndCloseTo = topAndBotPixels.mapIndexed { index, color ->
            val other = topAndBotPixels[(index + 1) % topAndBotPixels.size]
            !color.isWhite() && color.isCloseTo(other)
        }
        if (isNotWhiteAndCloseTo.all { it }) {
            image.recycle()
            return ColorDrawable(topLeftPixel)
        }

        val cornerPixels = listOf(topLeftPixel, topRightPixel, botLeftPixel, botRightPixel)
        val numberOfWhiteCorners = cornerPixels.map { cornerPixel -> cornerPixel.isWhite() }
            .filter { it }
            .size
        if (numberOfWhiteCorners > 2) {
            darkBG = false
        }

        var blackColor = when {
            topLeftIsDark -> topLeftPixel
            topRightIsDark -> topRightPixel
            botLeftIsDark -> botLeftPixel
            botRightIsDark -> botRightPixel
            else -> whiteColor
        }

        var overallWhitePixels = 0
        var overallBlackPixels = 0
        var topBlackStreak = 0
        var topWhiteStreak = 0
        var botBlackStreak = 0
        var botWhiteStreak = 0
        outer@ for (x in intArrayOf(left, right, leftOffsetX, rightOffsetX)) {
            var whitePixelsStreak = 0
            var whitePixels = 0
            var blackPixelsStreak = 0
            var blackPixels = 0
            var blackStreak = false
            var whiteStreak = false
            val notOffset = x == left || x == right
            inner@ for ((index, y) in (0..<image.height step image.height / 25).withIndex()) {
                val pixel = image[x, y]
                val pixelOff = image[x + (if (x < image.width / 2) -offsetX else offsetX), y]
                if (pixel.isWhite()) {
                    whitePixelsStreak++
                    whitePixels++
                    if (notOffset) {
                        overallWhitePixels++
                    }
                    if (whitePixelsStreak > 14) {
                        whiteStreak = true
                    }
                    if (whitePixelsStreak > 6 && whitePixelsStreak >= index - 1) {
                        topWhiteStreak = whitePixelsStreak
                    }
                } else {
                    whitePixelsStreak = 0
                    if (pixel.isDark() && pixelOff.isDark()) {
                        blackPixels++
                        if (notOffset) {
                            overallBlackPixels++
                        }
                        blackPixelsStreak++
                        if (blackPixelsStreak >= 14) {
                            blackStreak = true
                        }
                        continue@inner
                    }
                }
                if (blackPixelsStreak > 6 && blackPixelsStreak >= index - 1) {
                    topBlackStreak = blackPixelsStreak
                }
                blackPixelsStreak = 0
            }
            if (blackPixelsStreak > 6) {
                botBlackStreak = blackPixelsStreak
            } else if (whitePixelsStreak > 6) {
                botWhiteStreak = whitePixelsStreak
            }
            when {
                blackPixels > 22 -> {
                    if (x == right || x == rightOffsetX) {
                        blackColor = when {
                            topRightIsDark -> topRightPixel
                            botRightIsDark -> botRightPixel
                            else -> blackColor
                        }
                    }
                    darkBG = true
                    overallWhitePixels = 0
                    break@outer
                }

                blackStreak -> {
                    darkBG = true
                    if (x == right || x == rightOffsetX) {
                        blackColor = when {
                            topRightIsDark -> topRightPixel
                            botRightIsDark -> botRightPixel
                            else -> blackColor
                        }
                    }
                    if (blackPixels > 18) {
                        overallWhitePixels = 0
                        break@outer
                    }
                }

                whiteStreak || whitePixels > 22 -> darkBG = false
            }
        }

        val topIsBlackStreak = topBlackStreak > topWhiteStreak
        val bottomIsBlackStreak = botBlackStreak > botWhiteStreak
        if (overallWhitePixels > 9 && overallWhitePixels > overallBlackPixels) {
            darkBG = false
        }
        if (topIsBlackStreak && bottomIsBlackStreak) {
            darkBG = true
        }

        image.recycle()

        val isLandscape = context.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            return when {
                darkBG -> ColorDrawable(blackColor)
                else -> ColorDrawable(whiteColor)
            }
        }

        val botCornersIsWhite = botLeftPixel.isWhite() && botRightPixel.isWhite()
        val topCornersIsWhite = topLeftPixel.isWhite() && topRightPixel.isWhite()

        val topCornersIsDark = topLeftIsDark && topRightIsDark
        val botCornersIsDark = botLeftIsDark && botRightIsDark

        val gradient = when {
            darkBG && botCornersIsWhite -> {
                intArrayOf(blackColor, blackColor, whiteColor, whiteColor)
            }

            darkBG && topCornersIsWhite -> {
                intArrayOf(whiteColor, whiteColor, blackColor, blackColor)
            }

            darkBG -> {
                return ColorDrawable(blackColor)
            }

            topIsBlackStreak ||
                (
                    topCornersIsDark &&
                        topOffsetCornersIsDark &&
                        (topMidIsDark || overallBlackPixels > 9)
                    ) -> {
                intArrayOf(blackColor, blackColor, whiteColor, whiteColor)
            }

            bottomIsBlackStreak ||
                (
                    botCornersIsDark &&
                        botOffsetCornersIsDark &&
                        (bottomCenterPixel.isDark() || overallBlackPixels > 9)
                    ) -> {
                intArrayOf(whiteColor, whiteColor, blackColor, blackColor)
            }

            else -> {
                return ColorDrawable(whiteColor)
            }
        }

        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            gradient,
        )
    }

    private fun @receiver:ColorInt Int.isDark(): Boolean =
        red < 40 && blue < 40 && green < 40 && alpha > 200

    private fun @receiver:ColorInt Int.isCloseTo(other: Int): Boolean =
        abs(red - other.red) < 30 && abs(green - other.green) < 30 && abs(blue - other.blue) < 30

    private fun @receiver:ColorInt Int.isWhite(): Boolean =
        red + blue + green > 740

    /**
     * Used to check an image's dimensions without loading it in the memory.
     */
    private fun extractImageOptions(imageSource: BufferedSource): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageSource.peek().inputStream(), null, options)
        return options
    }

    private fun getBitmapRegionDecoder(imageStream: InputStream): BitmapRegionDecoder? {
        return BitmapRegionDecoder.newInstance(imageStream)
    }

    private val optimalImageHeight = getDisplayMaxHeightInPx * 2

    val HARDWARE_BITMAP_UNSUPPORTED = false

    // ------------------------------------------------------------------
    // Perceptual hashing (dHash) for page-level duplicate / blocklist matching
    // ------------------------------------------------------------------

    /**
     * Computes a perceptual *difference hash* (dHash) for the given image.
     *
     * The algorithm:
     * 1. Down-scale the image to a 9×8 grayscale thumbnail.
     * 2. For each row, compare each pixel to its right neighbour.
     * 3. Encode brighter-left as 1-bit, darker-or-equal-left as 0-bit.
     *
     * The result is a 64-bit fingerprint that is **resistant to JPEG
     * recompression, rescaling, and minor colour shifts** — ideal for
     * recognising reused scanlation credit / intro / outro pages.
     *
     * Uses [BitmapFactory.Options.inSampleSize] with a fixed conservative value
     * to decode only a coarse version of the image in a single pass, avoiding both
     * full-resolution bitmap allocation and the need for `mark()/reset()`.
     *
     * @return The dHash as a [Long], or `null` if the image cannot be decoded.
     */
    fun computeDHash(imageStream: InputStream): Long? {
        // Single-pass decode with a fixed conservative inSampleSize.
        // Manga pages are typically ≥800×1000, so inSampleSize=32 yields ≥25×31 –
        // well above the 9×8 target.  For small images BitmapFactory silently
        // caps the subsample so we never get a 0×0 result.
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = DHASH_SAMPLE_SIZE }
        val coarse = BitmapFactory.decodeStream(imageStream, null, decodeOpts) ?: return null
        val scaled = Bitmap.createScaledBitmap(coarse, DHASH_WIDTH, DHASH_HEIGHT, true)
        if (scaled !== coarse) coarse.recycle()

        var hash = 0L
        for (y in 0 until DHASH_HEIGHT) {
            for (x in 0 until DHASH_WIDTH - 1) { // 8 comparisons across 9 pixels per row
                val left = grayscaleAt(scaled, x, y)
                val right = grayscaleAt(scaled, x + 1, y)
                if (left > right) {
                    hash = hash or (1L shl (y * (DHASH_WIDTH - 1) + x))
                }
            }
        }
        scaled.recycle()
        return hash
    }

    /**
     * Returns the image dimensions *without* decoding pixel data. Uses header-only decode.
     *
     * @return Pair(width, height), or `null` if the image cannot be decoded.
     */
    fun getImageDimensions(imageStream: InputStream): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageStream, null, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    /**
     * Returns the Hamming distance between two dHash values —
     * i.e. the number of bits that differ.
     */
    fun dHashDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Encode a dHash [Long] as a zero-padded 16-character lowercase hex string. */
    fun dHashToHex(hash: Long): String = "%016x".format(hash)

    /**
     * Decode a 16-character hex string back to a dHash [Long].
     *
     * @throws IllegalArgumentException if [hex] is not exactly 16 valid hex characters.
     */
    fun hexToDHash(hex: String): Long {
        require(hex.length == 16 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Expected 16-char hex dHash, got: $hex"
        }
        return java.lang.Long.parseUnsignedLong(hex, 16)
    }

    /** ITU-R BT.601 luma from a packed ARGB pixel. */
    private fun grayscaleAt(bmp: Bitmap, x: Int, y: Int): Int {
        val p = bmp[x, y]
        return (p.red * 299 + p.green * 587 + p.blue * 114) / 1000
    }

    /** dHash thumbnail width (9 pixels → 8 horizontal comparisons per row). */
    private const val DHASH_WIDTH = 9

    /** dHash thumbnail height (8 rows → 64-bit hash). */
    private const val DHASH_HEIGHT = 8

    /**
     * Fixed subsample factor for the single-pass dHash decode.
     * At 32×, a typical 2000×3000 page decodes as ~62×93 — well above the 9×8 target.
     * For images smaller than ~288×256, BitmapFactory silently caps the subsample.
     */
    private const val DHASH_SAMPLE_SIZE = 32
}

val getDisplayMaxHeightInPx: Int
    get() = Resources.getSystem().displayMetrics.let { max(it.heightPixels, it.widthPixels) }
