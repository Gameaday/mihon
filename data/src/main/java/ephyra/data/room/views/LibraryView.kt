package ephyra.data.room.views

import androidx.room.DatabaseView
import ephyra.data.room.entities.MangaEntity
import eu.kanade.tachiyomi.source.model.UpdateStrategy

@DatabaseView(
    viewName = "libraryView",
    value = """
        SELECT
            M.*,
            coalesce(C.total, 0) AS totalCount,
            coalesce(C.readCount, 0) AS readCount,
            coalesce(C.latestUpload, 0) AS latestUpload,
            coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
            coalesce(C.lastRead, 0) AS lastRead,
            coalesce(C.bookmarkCount, 0) AS bookmarkCount,
            coalesce(MC.categories, '0') AS categories
        FROM mangas M
        LEFT JOIN (
            SELECT
                chapters.manga_id,
                count(*) AS total,
                sum(read) AS readCount,
                coalesce(max(chapters.date_upload), 0) AS latestUpload,
                coalesce(max(history.last_read), 0) AS lastRead,
                coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                sum(chapters.bookmark) AS bookmarkCount
            FROM chapters
            LEFT JOIN excluded_scanlators
            ON chapters.manga_id = excluded_scanlators.manga_id
            AND chapters.scanlator = excluded_scanlators.scanlator
            LEFT JOIN history
            ON chapters._id = history.chapter_id
            WHERE excluded_scanlators.scanlator IS NULL
            GROUP BY chapters.manga_id
        ) AS C
        ON M._id = C.manga_id
        LEFT JOIN (
            SELECT manga_id, group_concat(category_id) AS categories
            FROM mangas_categories
            GROUP BY manga_id
        ) AS MC
        ON MC.manga_id = M._id
        WHERE M.favorite = 1
    """
)
data class LibraryView(
    val _id: Long,
    val source: Long,
    val url: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val title: String,
    val status: Long,
    val thumbnail_url: String?,
    val favorite: Boolean,
    val last_update: Long?,
    val next_update: Long?,
    val initialized: Boolean,
    val viewer: Long,
    val chapter_flags: Long,
    val cover_last_modified: Long,
    val date_added: Long,
    val update_strategy: UpdateStrategy,
    val calculate_interval: Int,
    val last_modified_at: Long,
    val favorite_modified_at: Long?,
    val version: Long,
    val is_syncing: Boolean,
    val notes: String,
    val metadata_source: Long?,
    val metadata_url: String?,
    val canonical_id: String?,
    val source_status: Int,
    val alternative_titles: String?,
    val dead_since: Long?,
    val content_type: Int,
    val locked_fields: Long,
    
    val totalCount: Long,
    val readCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
    val bookmarkCount: Long,
    val categories: String
)
