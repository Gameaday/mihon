package ephyra.data.room.entities

import androidx.room.*

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["_id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["manga_id"]),
        Index(value = ["url"]),
        Index(value = ["manga_id", "read"], name = "chapters_unread_by_manga_index", where = "read = 0")
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "scanlator") val scanlator: String?,
    @ColumnInfo(name = "read") val read: Boolean,
    @ColumnInfo(name = "bookmark") val bookmark: Boolean,
    @ColumnInfo(name = "last_page_read") val lastPageRead: Int,
    @ColumnInfo(name = "chapter_number") val chapterNumber: Double,
    @ColumnInfo(name = "source_order") val sourceOrder: Int,
    @ColumnInfo(name = "date_fetch") val dateFetch: Long,
    @ColumnInfo(name = "date_upload") val dateUpload: Long,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "version") val version: Long,
    @ColumnInfo(name = "is_syncing") val isSyncing: Boolean,
)
