package tachiyomi.data.manga

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount

object MangaMapper {
    fun mapManga(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        notes: String,
        metadataSource: Long?,
        metadataUrl: String?,
        canonicalId: String?,
        sourceStatus: Long,
        alternativeTitles: String?,
        deadSince: Long?,
    ): Manga = Manga(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate ?: 0,
        nextUpdate = nextUpdate ?: 0,
        fetchInterval = calculateInterval.toInt(),
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        coverLastModified = coverLastModified,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        updateStrategy = updateStrategy,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        notes = notes,
        metadataSource = metadataSource,
        metadataUrl = metadataUrl,
        canonicalId = canonicalId,
        sourceStatus = sourceStatus.toInt(),
        alternativeTitles = parseAlternativeTitles(alternativeTitles),
        deadSince = deadSince,
    )

    fun mapLibraryManga(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        metadataSource: Long?,
        metadataUrl: String?,
        canonicalId: String?,
        sourceStatus: Long,
        alternativeTitles: String?,
        deadSince: Long?,
        totalCount: Long,
        readCount: Double,
        latestUpload: Long,
        chapterFetchedAt: Long,
        lastRead: Long,
        bookmarkCount: Double,
        categories: String,
    ): LibraryManga = LibraryManga(
        manga = mapManga(
            id,
            source,
            url,
            artist,
            author,
            description,
            genre,
            title,
            status,
            thumbnailUrl,
            favorite,
            lastUpdate,
            nextUpdate,
            initialized,
            viewerFlags,
            chapterFlags,
            coverLastModified,
            dateAdded,
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            metadataSource,
            metadataUrl,
            canonicalId,
            sourceStatus,
            alternativeTitles,
            deadSince,
        ),
        categories = categories.split(",").map { it.toLong() },
        totalChapters = totalCount,
        readCount = readCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        latestUpload = latestUpload,
        chapterFetchedAt = chapterFetchedAt,
        lastRead = lastRead,
    )

    fun mapMangaWithChapterCount(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        metadataSource: Long?,
        metadataUrl: String?,
        canonicalId: String?,
        sourceStatus: Long,
        alternativeTitles: String?,
        deadSince: Long?,
        totalCount: Long,
    ): MangaWithChapterCount = MangaWithChapterCount(
        manga = mapManga(
            id,
            source,
            url,
            artist,
            author,
            description,
            genre,
            title,
            status,
            thumbnailUrl,
            favorite,
            lastUpdate,
            nextUpdate,
            initialized,
            viewerFlags,
            chapterFlags,
            coverLastModified,
            dateAdded,
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            metadataSource,
            metadataUrl,
            canonicalId,
            sourceStatus,
            alternativeTitles,
            deadSince,
        ),
        chapterCount = totalCount,
    )

    /**
     * Parses alternative titles from the DB column.
     * Supports both JSON array format (new) and pipe-separated format (legacy).
     * Falls back to pipe-separated if JSON parsing fails, enabling a smooth migration.
     */
    fun parseAlternativeTitles(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        // Try JSON array first (new format)
        if (trimmed.startsWith("[")) {
            try {
                return Json.decodeFromString<List<String>>(trimmed).filter { it.isNotBlank() }
            } catch (_: Exception) {
                // Fall through to pipe-separated parsing
            }
        }
        // Legacy pipe-separated format
        return trimmed.split(LEGACY_ALT_TITLE_SEPARATOR).filter { it.isNotBlank() }
    }

    /**
     * Serializes alternative titles to JSON array format for DB storage.
     */
    fun serializeAlternativeTitles(titles: List<String>?): String? {
        if (titles.isNullOrEmpty()) return null
        return Json.encodeToString(ListSerializer(String.serializer()), titles)
    }

    /** Legacy separator used in pre-JSON pipe-separated storage. Kept for backward compatibility. */
    private const val LEGACY_ALT_TITLE_SEPARATOR = "|"
}
