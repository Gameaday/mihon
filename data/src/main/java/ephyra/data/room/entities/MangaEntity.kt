package ephyra.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mangas",
    indices = [
        Index(value = ["url"]),
        Index(value = ["source"]),
        Index(value = ["favorite"], name = "library_favorite_index", where = "favorite = 1"),
        Index(value = ["canonical_id"], name = "idx_mangas_canonical_id", where = "canonical_id IS NOT NULL"),
        Index(value = ["next_update"], name = "idx_mangas_next_update", where = "favorite = 1 AND next_update > 0"),
    ]
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long,
    @ColumnInfo(name = "source") val source: Long,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "artist") val artist: String?,
    @ColumnInfo(name = "author") val author: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "genre") val genre: List<String>?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "status") val status: Long,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "favorite") val favorite: Boolean,
    @ColumnInfo(name = "last_update") val lastUpdate: Long?,
    @ColumnInfo(name = "next_update") val nextUpdate: Long?,
    @ColumnInfo(name = "initialized") val initialized: Boolean,
    @ColumnInfo(name = "viewer") val viewerFlags: Long,
    @ColumnInfo(name = "chapter_flags") val chapterFlags: Long,
    @ColumnInfo(name = "cover_last_modified") val coverLastModified: Long,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "update_strategy") val updateStrategy: Int,
    @ColumnInfo(name = "calculate_interval") val calculateInterval: Int,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "favorite_modified_at") val favoriteModifiedAt: Long?,
    @ColumnInfo(name = "version") val version: Long,
    @ColumnInfo(name = "is_syncing") val isSyncing: Boolean,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "metadata_source") val metadataSource: Long?,
    @ColumnInfo(name = "metadata_url") val metadataUrl: String?,
    @ColumnInfo(name = "canonical_id") val canonicalId: String?,
    @ColumnInfo(name = "source_status") val sourceStatus: Int,
    @ColumnInfo(name = "alternative_titles") val alternativeTitles: String?,
    @ColumnInfo(name = "dead_since") val deadSince: Long?,
    @ColumnInfo(name = "content_type") val contentType: Int,
    @ColumnInfo(name = "locked_fields") val lockedFields: Long,
)
