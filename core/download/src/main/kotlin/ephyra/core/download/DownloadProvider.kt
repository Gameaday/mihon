package ephyra.core.download

import android.content.Context
import com.hippo.unifile.UniFile
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.storage.displayablePath
import ephyra.core.common.util.lang.Hash.md5
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.model.JellyfinNaming
import ephyra.domain.manga.model.Manga
import ephyra.domain.storage.service.StorageManager
import ephyra.i18n.MR
import eu.kanade.tachiyomi.source.Source
import logcat.LogPriority
import java.io.IOException
import ephyra.domain.download.service.DownloadProvider as IDownloadProvider

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
 *
 * @param context the application context.
 */
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager,
    private val libraryPreferences: LibraryPreferences,
) : IDownloadProvider {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for a manga. For internal use only.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(mangaTitle: String, source: Source): Result<UniFile> {
        val downloadsDir = downloadsDir
        if (downloadsDir == null) {
            logcat(LogPriority.ERROR) { "Failed to create download directory" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory)),
            )
        }

        val sourceDirName = getSourceDirName(source)
        val sourceDir = downloadsDir.createDirectory(sourceDirName)
        if (sourceDir == null) {
            val displayablePath = downloadsDir.displayablePath + "/$sourceDirName"
            logcat(LogPriority.ERROR) { "Failed to create source download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        val mangaDirName = getMangaDirName(mangaTitle)
        val mangaDir = sourceDir.createDirectory(mangaDirName)
        if (mangaDir == null) {
            val displayablePath = sourceDir.displayablePath + "/$mangaDirName"
            logcat(LogPriority.ERROR) { "Failed to create manga download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        return Result.success(mangaDir)
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    override fun findSourceDir(source: Source): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    override fun findMangaDir(mangaTitle: String, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getMangaDirName(mangaTitle))
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param chapterUrl the url of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the chapter.
     */
    override fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        source: Source,
    ): UniFile? {
        val mangaDir = findMangaDir(mangaTitle, source)
        return getValidChapterDirNames(chapterName, chapterScanlator, chapterUrl).asSequence()
            .mapNotNull { mangaDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    override fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): Pair<UniFile?, List<UniFile>> {
        val mangaDir = findMangaDir(manga.title, source) ?: return null to emptyList()
        return mangaDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url).asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    override fun getSourceDirName(source: Source): String {
        @Suppress("DEPRECATION")
        return DiskUtil.buildValidFilename(
            source.toString(),
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().getSync(),
        )
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param mangaTitle the title of the manga to query.
     */
    override fun getMangaDirName(mangaTitle: String): String {
        @Suppress("DEPRECATION")
        return DiskUtil.buildValidFilename(
            mangaTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().getSync(),
        )
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     */
    override fun getChapterDirName(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        disallowNonAsciiFilenames: Boolean,
    ): String {
        var dirName = sanitizeChapterName(chapterName)
        if (!chapterScanlator.isNullOrBlank()) {
            dirName = chapterScanlator + "_" + dirName
        }
        // Subtract 7 bytes for hash and underscore, 4 bytes for .cbz
        dirName = DiskUtil.buildValidFilename(dirName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAsciiFilenames)
        dirName += "_" + md5(chapterUrl).take(6)
        return dirName
    }

    /**
     * Returns a Jellyfin-compatible chapter file name for CBZ downloads.
     *
     * Format: `Series Name Ch. 001` (or `Series Name Vol. 01 Ch. 001`)
     *
     * This naming follows Jellyfin's Bookshelf plugin conventions so that
     * downloaded CBZ files can be directly served by a Jellyfin media server.
     *
     * @param mangaTitle the manga/series title.
     * @param chapterNumber the chapter number (from ChapterRecognition).
     * @param chapterName the chapter name (used as fallback title).
     * @return Jellyfin-compatible file name without extension.
     */
    override fun getJellyfinChapterDirName(
        mangaTitle: String,
        chapterNumber: Double,
        chapterName: String,
    ): String {
        return JellyfinNaming.chapterFileName(
            seriesTitle = mangaTitle,
            chapterNumber = chapterNumber.takeIf { it >= 0 },
            chapterTitle = chapterName.takeIf { it != mangaTitle },
        )
    }

    /**
     * Returns list of names that might have been previously used as
     * the directory name for a chapter.
     * Add to this list if naming pattern ever changes.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     */
    private fun getLegacyChapterDirNames(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
    ): List<String> {
        val sanitizedChapterName = sanitizeChapterName(chapterName)
        val chapterNameV1 = DiskUtil.buildValidFilename(
            when {
                !chapterScanlator.isNullOrBlank() -> "${chapterScanlator}_$sanitizedChapterName"
                else -> sanitizedChapterName
            },
        )

        // Get the filename that would be generated if the user were
        // using the other value for the disallow non-ASCII
        // filenames setting. This ensures that chapters downloaded
        // before the user changed the setting can still be found.
        @Suppress("DEPRECATION")
        val otherChapterDirName =
            getChapterDirName(
                chapterName,
                chapterScanlator,
                chapterUrl,
                !libraryPreferences.disallowNonAsciiFilenames().getSync(),
            )

        return buildList(2) {
            // Chapter name without hash (unable to handle duplicate
            // chapter names)
            add(chapterNameV1)
            add(otherChapterDirName)
        }
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    override fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        @Suppress("DEPRECATION")
        val disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().getSync()
        return getChapterDirName(oldChapter.name, oldChapter.scanlator, oldChapter.url, disallowNonAscii) !=
            getChapterDirName(newChapter.name, newChapter.scanlator, newChapter.url, disallowNonAscii)
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapter the domain chapter object.
     */
    override fun getValidChapterDirNames(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
    ): List<String> {
        @Suppress("DEPRECATION")
        val disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().getSync()
        val chapterDirName = getChapterDirName(chapterName, chapterScanlator, chapterUrl, disallowNonAscii)
        val legacyChapterDirNames = getLegacyChapterDirNames(chapterName, chapterScanlator, chapterUrl)

        return buildList {
            // Folder of images
            add(chapterDirName)
            // Archived chapters
            add("$chapterDirName.cbz")

            // any legacy names
            legacyChapterDirNames.forEach {
                add(it)
                add("$it.cbz")
            }
        }
    }
}
