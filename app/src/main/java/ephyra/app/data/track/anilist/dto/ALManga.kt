package ephyra.app.data.track.anilist.dto

import ephyra.app.data.database.models.Track
import ephyra.app.data.track.TrackerManager
import ephyra.app.data.track.anilist.Anilist
import ephyra.app.data.track.anilist.AnilistApi
import ephyra.app.data.track.model.TrackSearch
import ephyra.app.util.lang.htmlDecode
import java.text.SimpleDateFormat
import java.util.Locale

data class ALManga(
    val remoteId: Long,
    val title: String,
    val imageUrl: String,
    val description: String?,
    val format: String,
    val publishingStatus: String,
    val startDateFuzzy: Long,
    val totalChapters: Long,
    val averageScore: Int,
    val staff: ALStaff,
    val alternativeTitles: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val startYear: Int = 0,
) {
    fun toTrack() = TrackSearch.create(TrackerManager.ANILIST).apply {
        remote_id = remoteId
        title = this@ALManga.title
        total_chapters = totalChapters
        cover_url = imageUrl
        summary = description?.htmlDecode() ?: ""
        score = averageScore.toDouble()
        tracking_url = AnilistApi.mangaUrl(remote_id)
        publishing_status = publishingStatus
        publishing_type = format
        alternative_titles = this@ALManga.alternativeTitles
        genres = this@ALManga.genres
        start_year = this@ALManga.startYear
        if (startDateFuzzy != 0L) {
            start_date = try {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(startDateFuzzy)
            } catch (e: IllegalArgumentException) {
                ""
            }
        }
        staff.edges.forEach {
            val name = it.node.name() ?: return@forEach
            if ("Story" in it.role) authors += name
            if ("Art" in it.role) artists += name
        }
    }
}

data class ALUserManga(
    val libraryId: Long,
    val listStatus: String,
    val scoreRaw: Int,
    val chaptersRead: Int,
    val startDateFuzzy: Long,
    val completedDateFuzzy: Long,
    val manga: ALManga,
    val private: Boolean,
) {
    fun toTrack() = Track.create(TrackerManager.ANILIST).apply {
        remote_id = manga.remoteId
        title = manga.title
        status = toTrackStatus()
        score = scoreRaw.toDouble()
        started_reading_date = startDateFuzzy
        finished_reading_date = completedDateFuzzy
        last_chapter_read = chaptersRead.toDouble()
        library_id = libraryId
        total_chapters = manga.totalChapters
        private = this@ALUserManga.private
    }

    private fun toTrackStatus() = when (listStatus) {
        "CURRENT" -> Anilist.READING
        "COMPLETED" -> Anilist.COMPLETED
        "PAUSED" -> Anilist.ON_HOLD
        "DROPPED" -> Anilist.DROPPED
        "PLANNING" -> Anilist.PLAN_TO_READ
        "REPEATING" -> Anilist.REREADING
        else -> throw NotImplementedError("Unknown status: $listStatus")
    }
}
