package ephyra.app.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuAddMangaResult(
    val data: KitsuAddMangaItem,
)

@Serializable
data class KitsuAddMangaItem(
    val id: Long,
)
