package ephyra.data.room.views

import androidx.room.DatabaseView
import java.util.Date

@DatabaseView(
    viewName = "historyView",
    value = """
        SELECT
            history._id AS id,
            mangas._id AS mangaId,
            chapters._id AS chapterId,
            mangas.title,
            mangas.thumbnail_url AS thumbnailUrl,
            mangas.source,
            mangas.favorite,
            mangas.cover_last_modified,
            chapters.chapter_number AS chapterNumber,
            history.last_read AS readAt,
            history.time_read AS readDuration,
            max_last_read.last_read AS maxReadAt,
            max_last_read.chapter_id AS maxReadAtChapterId
        FROM mangas
        JOIN chapters
        ON mangas._id = chapters.manga_id
        JOIN history
        ON chapters._id = history.chapter_id
        JOIN (
            SELECT chapters.manga_id, chapters._id AS chapter_id, MAX(history.last_read) AS last_read
            FROM chapters JOIN history
            ON chapters._id = history.chapter_id
            GROUP BY chapters.manga_id
        ) AS max_last_read
        ON chapters.manga_id = max_last_read.manga_id
    """
)
data class HistoryView(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val source: Long,
    val favorite: Boolean,
    val cover_last_modified: Long,
    val chapterNumber: Double,
    val readAt: Date?,
    val readDuration: Long,
    val maxReadAt: Long?,
    val maxReadAtChapterId: Long?
)

@DatabaseView(
    viewName = "updatesView",
    value = """
        SELECT
            mangas._id AS mangaId,
            mangas.title AS mangaTitle,
            chapters._id AS chapterId,
            chapters.name AS chapterName,
            chapters.scanlator,
            chapters.url AS chapterUrl,
            chapters.read,
            chapters.bookmark,
            chapters.last_page_read,
            mangas.source,
            mangas.favorite,
            mangas.thumbnail_url AS thumbnailUrl,
            mangas.cover_last_modified AS coverLastModified,
            chapters.date_upload AS dateUpload,
            chapters.date_fetch AS datefetch,
            excluded_scanlators.scanlator AS excludedScanlator
        FROM mangas JOIN chapters
        ON mangas._id = chapters.manga_id
        LEFT JOIN excluded_scanlators
        ON mangas._id = excluded_scanlators.manga_id
        AND chapters.scanlator = excluded_scanlators.scanlator
    """
)
data class UpdatesView(
    val mangaId: Long,
    val mangaTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val chapterUrl: String,
    val read: Boolean,
    val bookmark: Boolean,
    val last_page_read: Int,
    val source: Long,
    val favorite: Boolean,
    val thumbnailUrl: String?,
    val coverLastModified: Long,
    val dateUpload: Long,
    val datefetch: Long,
    val excludedScanlator: String?
)
