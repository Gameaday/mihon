package ephyra.domain.manga.model

/**
 * Generates Jellyfin-compatible directory and file names for manga content.
 *
 * Follows Jellyfin's Bookshelf plugin naming conventions so that downloaded
 * or locally stored manga can be directly served by a Jellyfin media server
 * without manual renaming.
 *
 * ## Jellyfin Comics/Manga Library Structure
 *
 * ```
 * Manga/
 * ├── Series Name/
 * │   ├── Series Name Vol. 01 Ch. 001.cbz
 * │   ├── Series Name Vol. 01 Ch. 002.cbz
 * │   └── ComicInfo.xml   (series-level metadata)
 * ```
 *
 * For series without volume info:
 * ```
 * Manga/
 * ├── Series Name/
 * │   ├── Series Name Ch. 001.cbz
 * │   ├── Series Name Ch. 002.cbz
 * │   └── ComicInfo.xml
 * ```
 *
 * Jellyfin uses ComicInfo.xml embedded in CBZ files for metadata, which
 * the app already generates during downloads. This utility ensures the
 * *naming* matches what Jellyfin's scanner expects.
 *
 * @see <a href="https://jellyfin.org/docs/general/server/media/comics/">Jellyfin Comics Guide</a>
 */
object JellyfinNaming {

    /**
     * Characters that are invalid in most file systems and should be removed
     * or replaced in directory/file names.
     */
    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|]")

    /**
     * Sanitizes a string for use as a file or directory name.
     * Replaces invalid characters with underscores and trims whitespace.
     */
    fun sanitize(name: String): String {
        return name
            .replace(INVALID_CHARS, "_")
            .replace(Regex("_+"), "_")
            .trim()
            .trimEnd('.')
            .ifBlank { "Unknown" }
    }

    /**
     * Generates a Jellyfin-compatible series directory name.
     *
     * Format: `Series Name`
     *
     * Jellyfin identifies series by their folder name. The folder name should
     * match the series title as closely as possible.
     *
     * @param title The manga/series title.
     * @return Sanitized directory name for the series.
     */
    fun seriesDirName(title: String): String {
        return sanitize(title)
    }

    /**
     * Generates a Jellyfin-compatible chapter file name (without extension).
     *
     * Follows Jellyfin Bookshelf plugin conventions:
     * - With volume: `Series Name Vol. 01 Ch. 001`
     * - Without volume: `Series Name Ch. 001`
     * - Volume-only: `Series Name Vol. 01`
     *
     * Chapter numbers are zero-padded to 3 digits for proper sort order.
     * Volume numbers are zero-padded to 2 digits.
     *
     * @param seriesTitle The series title.
     * @param chapterNumber The chapter number (null if not applicable).
     * @param volumeNumber The volume number (null if not applicable).
     * @param chapterTitle Optional chapter title suffix.
     * @return Formatted file name without extension.
     */
    fun chapterFileName(
        seriesTitle: String,
        chapterNumber: Double? = null,
        volumeNumber: Int? = null,
        chapterTitle: String? = null,
    ): String {
        val sanitizedTitle = sanitize(seriesTitle)
        val parts = mutableListOf(sanitizedTitle)

        if (volumeNumber != null && volumeNumber > 0) {
            parts.add("Vol. ${volumeNumber.toString().padStart(2, '0')}")
        }

        if (chapterNumber != null && chapterNumber >= 0) {
            val chapterStr = if (chapterNumber == chapterNumber.toLong().toDouble()) {
                // Integer chapter number — pad to 3 digits
                chapterNumber.toLong().toString().padStart(3, '0')
            } else {
                // Decimal chapter number (e.g., 10.5, 10.25)
                // Preserve the full decimal portion via string conversion
                val fullStr = chapterNumber.toBigDecimal().toPlainString()
                val dotIndex = fullStr.indexOf('.')
                val intPart = fullStr.substring(0, dotIndex).padStart(3, '0')
                val decPart = fullStr.substring(dotIndex + 1)
                "$intPart.$decPart"
            }
            parts.add("Ch. $chapterStr")
        }

        var result = parts.joinToString(" ")

        // Append chapter title if present and different from series title
        if (!chapterTitle.isNullOrBlank() && chapterTitle != seriesTitle) {
            val sanitizedChapterTitle = sanitize(chapterTitle)
            // Only append if it doesn't duplicate the chapter number info
            if (!sanitizedChapterTitle.startsWith("Ch.", ignoreCase = true) &&
                !sanitizedChapterTitle.startsWith("Chapter", ignoreCase = true)
            ) {
                result += " - $sanitizedChapterTitle"
            }
        }

        return result
    }

    /**
     * Generates a Jellyfin-compatible content type library root name.
     *
     * Jellyfin organizes content into libraries by type. This returns the
     * conventional top-level directory name for each content type.
     *
     * @param contentType The content type.
     * @return Library root directory name.
     */
    fun libraryRootName(contentType: ContentType): String {
        return when (contentType) {
            ContentType.MANGA -> "Manga"
            ContentType.NOVEL -> "Novels"
            ContentType.BOOK -> "Books"
            ContentType.UNKNOWN -> "Manga"
        }
    }

    /**
     * Builds a full Jellyfin-compatible relative path for a chapter file.
     *
     * Format: `Manga/Series Name/Series Name Ch. 001.cbz`
     *
     * @param manga The manga metadata.
     * @param chapterNumber Chapter number for naming.
     * @param volumeNumber Volume number (optional).
     * @param chapterTitle Chapter title (optional).
     * @return Relative path segments: [libraryRoot, seriesDir, fileName.cbz]
     */
    fun buildPath(
        seriesTitle: String,
        contentType: ContentType,
        chapterNumber: Double? = null,
        volumeNumber: Int? = null,
        chapterTitle: String? = null,
    ): List<String> {
        val root = libraryRootName(contentType)
        val seriesDir = seriesDirName(seriesTitle)
        val fileName = chapterFileName(seriesTitle, chapterNumber, volumeNumber, chapterTitle) + ".cbz"

        return listOf(root, seriesDir, fileName)
    }

    /**
     * Attempts to parse a Jellyfin-formatted chapter filename to extract
     * chapter and volume numbers. This allows the local source to correctly
     * import content organized in Jellyfin conventions.
     *
     * Recognizes patterns like:
     * - `Series Name Vol. 01 Ch. 001.cbz`
     * - `Series Name Ch. 001.cbz`
     * - `Series Name Vol. 01.cbz`
     *
     * @param filename The filename (with or without extension).
     * @return Parsed result or null if not a recognized format.
     */
    fun parseChapterFilename(filename: String): ParsedChapter? {
        // Strip known archive extensions only
        val archiveExtensions = listOf(".cbz", ".cbr", ".zip", ".rar", ".7z", ".cb7", ".tar", ".epub")
        val name = archiveExtensions.fold(filename) { acc, ext ->
            if (acc.endsWith(ext, ignoreCase = true)) acc.dropLast(ext.length) else acc
        }

        // Try: Vol. XX Ch. YYY
        val volChRegex = Regex("""(.+?)\s+Vol\.\s*(\d+)\s+Ch\.\s*([\d.]+)(?:\s+-\s+(.+))?""")
        volChRegex.matchEntire(name)?.let { match ->
            return ParsedChapter(
                seriesTitle = match.groupValues[1].trim(),
                volumeNumber = match.groupValues[2].toIntOrNull(),
                chapterNumber = match.groupValues[3].toDoubleOrNull(),
                chapterTitle = match.groupValues[4].takeIf { it.isNotBlank() },
            )
        }

        // Try: Ch. YYY (no volume)
        val chOnlyRegex = Regex("""(.+?)\s+Ch\.\s*([\d.]+)(?:\s+-\s+(.+))?""")
        chOnlyRegex.matchEntire(name)?.let { match ->
            return ParsedChapter(
                seriesTitle = match.groupValues[1].trim(),
                volumeNumber = null,
                chapterNumber = match.groupValues[2].toDoubleOrNull(),
                chapterTitle = match.groupValues[3].takeIf { it.isNotBlank() },
            )
        }

        // Try: Vol. XX only (volume-only files, e.g., tankōbon)
        val volOnlyRegex = Regex("""(.+?)\s+Vol\.\s*(\d+)(?:\s+-\s+(.+))?""")
        volOnlyRegex.matchEntire(name)?.let { match ->
            return ParsedChapter(
                seriesTitle = match.groupValues[1].trim(),
                volumeNumber = match.groupValues[2].toIntOrNull(),
                chapterNumber = null,
                chapterTitle = match.groupValues[3].takeIf { it.isNotBlank() },
            )
        }

        return null
    }

    /**
     * Result of parsing a Jellyfin-formatted chapter filename.
     */
    data class ParsedChapter(
        val seriesTitle: String,
        val volumeNumber: Int?,
        val chapterNumber: Double?,
        val chapterTitle: String?,
    )
}
