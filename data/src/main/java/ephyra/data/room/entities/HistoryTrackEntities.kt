package ephyra.data.room.entities

import androidx.room.*
import java.util.Date

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["_id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chapter_id"], unique = true),
        Index(value = ["last_read"]),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long,
    @ColumnInfo(name = "chapter_id") val chapterId: Long,
    @ColumnInfo(name = "last_read") val lastRead: Date?,
    @ColumnInfo(name = "time_read") val timeRead: Long,
)

@Entity(
    tableName = "manga_sync",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["_id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["manga_id", "sync_id"], unique = true),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "sync_id") val syncId: Long,
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo(name = "library_id") val libraryId: Long?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "last_chapter_read") val lastChapterRead: Double,
    @ColumnInfo(name = "total_chapters") val totalChapters: Long,
    @ColumnInfo(name = "status") val status: Long,
    @ColumnInfo(name = "score") val score: Double,
    @ColumnInfo(name = "remote_url") val remoteUrl: String,
    @ColumnInfo(name = "start_date") val startDate: Long,
    @ColumnInfo(name = "finish_date") val finishDate: Long,
    @ColumnInfo(name = "private") val isPrivate: Boolean,
)

@Entity(tableName = "extension_repos")
data class ExtensionRepoEntity(
    @PrimaryKey @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "short_name") val shortName: String?,
    @ColumnInfo(name = "website") val website: String,
    @ColumnInfo(name = "signing_key_fingerprint") val signingKeyFingerprint: String,
)

@Entity(
    tableName = "excluded_scanlators",
    primaryKeys = ["manga_id", "scanlator"],
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["_id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExcludedScanlatorEntity(
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "scanlator") val scanlator: String,
)
