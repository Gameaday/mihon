package ephyra.data.cache

import android.content.Context
import ephyra.core.common.util.storage.DiskUtil
import ephyra.domain.manga.model.Manga
import java.io.File
import java.io.IOException
import java.io.InputStream
import ephyra.domain.manga.service.CoverCache as ICoverCache

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 */
class CoverCache(private val context: Context) : ICoverCache {

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

    override fun getCoverFile(manga: Manga): File? = getCoverFile(manga.thumbnailUrl)

    /**
     * Returns the custom cover from cache.
     *
     * @param mangaId the manga id.
     * @return cover image.
     */
    fun getCustomCoverFile(mangaId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(mangaId.toString()))
    }

    override fun getCustomCoverFile(manga: Manga): File = getCustomCoverFile(manga.id)

    /**
     * Saves the given stream as the manga's custom cover to cache.
     */
    @Throws(IOException::class)
    override fun setCustomCoverToCache(manga: Manga, inputStream: InputStream) {
        getCustomCoverFile(manga.id).outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Delete the cover files of the manga from the cache.
     */
    fun deleteFromCacheWithResult(manga: Manga, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(manga.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCoverInternal(manga.id)) ++deleted
        }

        return deleted
    }

    override fun deleteFromCache(manga: Manga, deleteCustom: Boolean): Int {
        return deleteFromCacheWithResult(manga, deleteCustom)
    }

    override fun deleteAll() {
        cacheDir.deleteRecursively()
        customCoverCacheDir.deleteRecursively()
        cacheDir.mkdirs()
        customCoverCacheDir.mkdirs()
    }

    override fun deleteCustomCover(mangaId: Long) {
        deleteCustomCoverInternal(mangaId)
    }

    /**
     * Delete custom cover of the manga from the cache
     */
    fun deleteCustomCover(mangaId: Long?): Boolean {
        return deleteCustomCoverInternal(mangaId)
    }

    private fun deleteCustomCoverInternal(mangaId: Long?): Boolean {
        return getCustomCoverFile(mangaId).let {
            it.exists() && it.delete()
        }
    }

    /**
     * Removes cached cover files whose [java.io.File.lastModified] timestamp is older
     * than [maxAgeMs].
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
     * thumbnail URLs.
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
