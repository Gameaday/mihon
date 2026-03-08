package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MURecord(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val image: MUImage? = null,
    val type: String? = null,
    val year: String? = null,
    val status: String? = null,
    @SerialName("bayesian_rating")
    val bayesianRating: Double? = null,
    @SerialName("rating_votes")
    val ratingVotes: Int? = null,
    @SerialName("latest_chapter")
    val latestChapter: Int? = null,
    val genres: List<MUGenre> = emptyList(),
    val associated: List<MUAssociatedName> = emptyList(),
)

@Serializable
data class MUGenre(
    val genre: String? = null,
)

@Serializable
data class MUAssociatedName(
    val title: String? = null,
)

fun MURecord.toTrackSearch(id: Long): TrackSearch {
    return TrackSearch.create(id).apply {
        remote_id = this@toTrackSearch.seriesId ?: 0L
        title = this@toTrackSearch.title?.htmlDecode() ?: ""
        total_chapters = 0
        cover_url = this@toTrackSearch.image?.url?.original ?: ""
        summary = this@toTrackSearch.description?.htmlDecode() ?: ""
        tracking_url = this@toTrackSearch.url ?: ""
        publishing_status = this@toTrackSearch.status ?: ""
        publishing_type = this@toTrackSearch.type ?: ""
        start_date = this@toTrackSearch.year ?: ""
        start_year = this@toTrackSearch.year?.take(4)?.toIntOrNull() ?: 0
        score = this@toTrackSearch.bayesianRating ?: -1.0
        genres = this@toTrackSearch.genres.mapNotNull { it.genre }
        alternative_titles = this@toTrackSearch.associated
            .mapNotNull { it.title?.htmlDecode() }
            .filterNot { it.isBlank() || it == this@toTrackSearch.title }
            .distinct()
    }
}
