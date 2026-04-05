package ephyra.data.cache

import android.content.Context
import android.text.format.Formatter
import coil3.disk.DiskCache
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.logcat
import ephyra.data.cache.ChapterCache.Companion.CACHE_SIZE_HIGH
import ephyra.data.cache.ChapterCache.Companion.CACHE_SIZE_LOW
import ephyra.data.cache.ChapterCache.Companion.CACHE_SIZE_MEDIUM
import ephyra.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Response
import okio.Path.Companion.toOkioPath
import okio.buffer
import java.io.File
import java.io.IOException

/**
 * Class used to create chapter cache
 * For each image in a chapter a file is created
 * For each chapter a JSON list is created and persisted.
 * Backed by [DiskCache] from Coil, which provides LRU eviction, transactional writes,
 * and concurrent-edit deduplication without any additional dependencies.
 *
 * @param context the application context.
 */
class ChapterCache(
    private val context: Context,
    private val json: Json,
) {

    companion object {
        /** Cache size for low-RAM devices (< 2 GB total RAM): 100 MB. */
        const val CACHE_SIZE_LOW = 100L * 1024 * 1024

        /** Cache size for mid-range devices (2–3.9 GB total RAM): 256 MB. */
        const val CACHE_SIZE_MEDIUM = 256L * 1024 * 1024

        /** Cache size for high-end devices (≥ 4 GB total RAM): 512 MB. */
        const val CACHE_SIZE_HIGH = 512L * 1024 * 1024

        /**
         * The maximum number of bytes this cache should use to store.
         * Kept for backward-compatibility; new code should use [CACHE_SIZE_LOW],
         * [CACHE_SIZE_MEDIUM], and [CACHE_SIZE_HIGH] directly.
         */
        const val PARAMETER_CACHE_SIZE = CACHE_SIZE_LOW
    }

    /**
     * The effective cache capacity chosen at construction time based on the device's
     * [DeviceUtil.PerformanceTier]. Exposed so the settings UI can display the true cap
     * rather than always showing the LOW-tier constant.
     */
    val cacheSize: Long = when (DeviceUtil.performanceTier(context)) {
        DeviceUtil.PerformanceTier.LOW -> CACHE_SIZE_LOW
        DeviceUtil.PerformanceTier.MEDIUM -> CACHE_SIZE_MEDIUM
        DeviceUtil.PerformanceTier.HIGH -> CACHE_SIZE_HIGH
    }

    /** Cache class used for cache management. */
    private val diskCache = DiskCache.Builder()
        .directory(File(context.cacheDir, "chapter_disk_cache").toOkioPath())
        .maxSizeBytes(cacheSize)
        .build()

    /**
     * Returns directory of cache.
     */
    private val cacheDir: File = File(diskCache.directory.toString())

    /**
     * Returns real size of directory in human readable format.
     */
    suspend fun getReadableSize(): String = withContext(Dispatchers.IO) {
        val size = DiskUtil.getDirectorySize(cacheDir)
        Formatter.formatFileSize(context, size)
    }

    /**
     * Get page list from cache.
     *
     * @param chapter the chapter.
     * @return the list of pages.
     */
    fun getPageListFromCache(chapter: Chapter): List<Page> {
        // Get the key for the chapter.
        val key = DiskUtil.hashKeyForDisk(getKey(chapter))

        // Convert JSON string to list of objects. Throws an exception if snapshot is null
        val snapshot = diskCache.openSnapshot(key) ?: throw IOException("Not in cache")
        return snapshot.use {
            diskCache.fileSystem.source(it.data).buffer().use { source ->
                json.decodeFromString(source.readUtf8())
            }
        }
    }

    /**
     * Add page list to disk cache.
     *
     * @param chapter the chapter.
     * @param pages list of pages.
     */
    fun putPageListToCache(chapter: Chapter, pages: List<Page>) {
        // Convert list of pages to json string.
        val cachedValue = json.encodeToString(pages)
        val key = DiskUtil.hashKeyForDisk(getKey(chapter))
        try {
            val editor = diskCache.openEditor(key) ?: return
            try {
                diskCache.fileSystem.write(editor.data) {
                    writeUtf8(cachedValue)
                }
                editor.commitAndOpenSnapshot()?.close()
            } catch (e: Exception) {
                try {
                    editor.abort()
                } catch (_: Exception) {
                }
                throw e
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to put page list to cache" }
            // Ignore.
        }
    }

    /**
     * Returns true if image is in cache.
     *
     * @param imageUrl url of image.
     * @return true if in cache otherwise false.
     */
    fun isImageInCache(imageUrl: String): Boolean {
        return try {
            diskCache.openSnapshot(DiskUtil.hashKeyForDisk(imageUrl))?.use { true } ?: false
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Get image file from url.
     *
     * The snapshot is closed before the [File] is returned. The underlying file stays on disk
     * because Coil's [DiskCache] only evicts entries lazily — during a background trim cycle
     * and only when the cache is over capacity. Callers should use the returned file immediately
     * and must not hold long-lived references to it across operations that could trigger eviction
     * (e.g. a manual [clear]).
     *
     * Returns `null` if the entry has been evicted from the disk cache (e.g. due to LRU
     * pressure or a concurrent [clear] call). Callers that stored a stream lambda around
     * this function should treat `null` as a transient cache-miss and re-queue the page
     * for download rather than surfacing an error to the user.
     *
     * @param imageUrl url of image.
     * @return path of image, or `null` if the entry is no longer cached.
     */
    fun getImageFile(imageUrl: String): File? {
        val key = DiskUtil.hashKeyForDisk(imageUrl)
        return diskCache.openSnapshot(key)?.use { File(it.data.toString()) }
    }

    /**
     * Add image to cache.
     *
     * @param imageUrl url of image.
     * @param response http response from page.
     * @throws IOException image error.
     */
    @Throws(IOException::class)
    fun putImageToCache(imageUrl: String, response: Response) {
        val key = DiskUtil.hashKeyForDisk(imageUrl)
        try {
            val editor = diskCache.openEditor(key) ?: return
            try {
                diskCache.fileSystem.write(editor.data) {
                    response.body.source().readAll(this)
                }
                editor.commitAndOpenSnapshot()?.close()
            } catch (e: Exception) {
                try {
                    editor.abort()
                } catch (_: Exception) {
                }
                throw e
            }
        } finally {
            response.body.close()
        }
    }

    /**
     * Fetches the image via [fetchImage] and stores it under [imageUrl] in the cache. If the
     * entry already has an in-progress write (another coroutine is downloading the same image),
     * this method returns without making a network request. The response body returned by
     * [fetchImage] is always closed by this method.
     *
     * @param imageUrl url of image.
     * @param fetchImage suspending lambda that fetches the image from the network.
     * @throws IOException on network or disk error.
     */
    @Throws(IOException::class)
    suspend fun fetchAndCacheImage(imageUrl: String, fetchImage: suspend () -> Response) {
        val key = DiskUtil.hashKeyForDisk(imageUrl)
        // openEditor() returns null if another edit is already in progress for this key, which
        // prevents duplicate network requests for the same image.
        val editor = diskCache.openEditor(key) ?: return
        try {
            val response = fetchImage()
            try {
                diskCache.fileSystem.write(editor.data) {
                    response.body.source().readAll(this)
                }
            } finally {
                response.body.close()
            }
            editor.commitAndOpenSnapshot()?.close()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    fun clear(): Int {
        // Count data files before clearing so we can report how many entries were removed.
        val count = cacheDir.listFiles()
            ?.count { it.isFile && !it.name.startsWith("journal") }
            ?: 0
        diskCache.clear()
        return count
    }

    private fun getKey(chapter: Chapter): String {
        return "${chapter.mangaId}${chapter.url}"
    }
}
