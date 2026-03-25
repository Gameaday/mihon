package ephyra.app.data.cache

import android.content.Context
import ephyra.app.util.storage.DiskUtil
import ephyra.domain.manga.model.Manga
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"

        /** Default max age for cover pruning: 30 days in milliseconds. */
        private const val COVER_PRUNE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = getCacheDir(COVERS_DIR)

    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

    /**
     * Returns the cover from cache.
     *
     * @param mangaThumbnailUrl thumbnail url for the manga.
     * @return cover image.
     */
    fun getCoverFile(mangaThumbnailUrl: String?): File? {
        return mangaThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Returns the custom cover from cache.
     *
     * @param mangaId the manga id.
     * @return cover image.
     */
    fun getCustomCoverFile(mangaId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(mangaId.toString()))
    }

    /**
     * Saves the given stream as the manga's custom cover to cache.
     *
     * **Stream ownership:** this method takes ownership of [inputStream] and always closes it
     * before returning (whether or not an exception occurs). Callers must not close the stream
     * themselves after passing it here.
     *
     * @param manga the manga.
     * @param inputStream the stream to copy. Closed by this method.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomCoverToCache(manga: Manga, inputStream: InputStream) {
        getCustomCoverFile(manga.id).outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Delete the cover files of the manga from the cache.
     *
     * @param manga the manga.
     * @param deleteCustomCover whether the custom cover should be deleted.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(manga: Manga, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(manga.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCover(manga.id)) ++deleted
        }

        return deleted
    }

    /**
     * Delete custom cover of the manga from the cache
     *
     * @param mangaId the manga id.
     * @return whether the cover was deleted.
     */
    fun deleteCustomCover(mangaId: Long?): Boolean {
        return getCustomCoverFile(mangaId).let {
            it.exists() && it.delete()
        }
    }

    /**
     * Removes cached cover files whose [java.io.File.lastModified] timestamp is older
     * than [maxAgeMs]. This effectively prunes covers that haven't been downloaded or
     * updated recently, based on their last write time rather than last read/access.
     *
     * Custom covers are never pruned — only auto-downloaded covers from browsing.
     * Files whose names appear in [protectedNames] are always kept (e.g. covers
     * of favorited manga so offline viewing still looks good after a long break).
     *
     * Call from a background thread (e.g. the library update job) to reclaim storage
     * consumed by covers of manga the user is no longer interacting with.
     *
     * @param protectedNames set of filenames (MD5 hashes) to keep regardless of age.
     * @param maxAgeMs maximum age in milliseconds since last modification. Default: 30 days.
     * @return number of files deleted.
     */
    fun pruneOldCovers(
        protectedNames: Set<String> = emptySet(),
        maxAgeMs: Long = COVER_PRUNE_MAX_AGE_MS,
    ): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val files = cacheDir.listFiles() ?: return 0
        var deleted = 0
        for (file in files) {
            if (file.isDirectory) continue
            if (file.name in protectedNames) continue
            if (file.lastModified() <= cutoff && file.delete()) deleted++
        }
        return deleted
    }

    /**
     * Returns the set of cover cache filenames (MD5 hashes) for the given
     * thumbnail URLs. Useful for building a protected-set so that covers of
     * favorited manga are never pruned.
     */
    fun coverFileNames(thumbnailUrls: List<String?>): Set<String> {
        return thumbnailUrls
            .filterNotNull()
            .mapTo(HashSet()) { DiskUtil.hashKeyForDisk(it) }
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?.also { it.mkdirs() }
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
