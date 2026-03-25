package ephyra.app.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALManga(
    val id: Long,
    val title: String,
    val synopsis: String = "",
    @SerialName("num_chapters")
    val numChapters: Long,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALMangaCovers?,
    val status: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("start_date")
    val startDate: String?,
    val authors: List<MALAuthorNode> = emptyList(),
    val genres: List<MALGenre> = emptyList(),
    @SerialName("alternative_titles")
    val alternativeTitles: MALAlternativeTitles? = null,
)

@Serializable
data class MALAuthorNode(
    val node: MALAuthor,
    val role: String,
)

@Serializable
data class MALAuthor(
    val id: Int,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
) {
    fun getFullName(): String? = "$firstName $lastName".trim().ifBlank { null }
}

@Serializable
data class MALMangaCovers(
    val large: String = "",
)

@Serializable
data class MALGenre(
    val id: Long,
    val name: String,
)

@Serializable
data class MALAlternativeTitles(
    val synonyms: List<String> = emptyList(),
    val en: String? = null,
    val ja: String? = null,
) {
    fun toAlternativeTitles(primaryTitle: String): List<String> {
        return buildList {
            en?.let { add(it) }
            ja?.let { add(it) }
            addAll(synonyms)
        }
            .filterNot { it.isBlank() || it == primaryTitle }
            .distinct()
    }
}
