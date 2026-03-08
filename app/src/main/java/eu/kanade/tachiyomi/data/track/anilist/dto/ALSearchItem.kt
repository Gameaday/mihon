package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALSearchItem(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val format: String,
    val status: String?,
    val startDate: ALFuzzyDate,
    val chapters: Long?,
    val averageScore: Int?,
    val staff: ALStaff,
    val synonyms: List<String>? = null,
    val genres: List<String>? = null,
) {
    fun toALManga(): ALManga = ALManga(
        remoteId = id,
        title = title.userPreferred,
        imageUrl = coverImage.large,
        description = description,
        format = format.replace("_", "-"),
        publishingStatus = status ?: "",
        startDateFuzzy = startDate.toEpochMilli(),
        totalChapters = chapters ?: 0,
        averageScore = averageScore ?: -1,
        staff = staff,
        alternativeTitles = buildAlternativeTitles(),
        genres = genres ?: emptyList(),
        startYear = startDate.year ?: 0,
    )

    /**
     * Collects all distinct non-blank alternative titles from AniList data:
     * romaji, english, native title variants + synonyms.
     * Excludes the primary userPreferred title since that's already stored as the main title.
     */
    private fun buildAlternativeTitles(): List<String> {
        val primary = title.userPreferred
        return buildList {
            title.romaji?.let { add(it) }
            title.english?.let { add(it) }
            title.native?.let { add(it) }
            synonyms?.let { addAll(it) }
        }
            .filterNot { it.isBlank() || it == primary }
            .distinct()
    }
}

@Serializable
data class ALItemTitle(
    val userPreferred: String,
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class ItemCover(
    val large: String,
)

@Serializable
data class ALStaff(
    val edges: List<ALEdge>,
)

@Serializable
data class ALEdge(
    val role: String,
    val id: Int,
    val node: ALStaffNode,
)

@Serializable
data class ALStaffNode(
    val name: ALStaffName,
)

@Serializable
data class ALStaffName(
    val userPreferred: String?,
    val native: String?,
    val full: String?,
) {
    operator fun invoke(): String? {
        return userPreferred ?: full ?: native
    }
}
