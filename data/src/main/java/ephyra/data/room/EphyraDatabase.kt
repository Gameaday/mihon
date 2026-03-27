package ephyra.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ephyra.data.room.entities.*
import ephyra.data.room.views.*

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        HistoryEntity::class,
        TrackEntity::class,
        ExtensionRepoEntity::class,
        ExcludedScanlatorEntity::class,
    ],
    views = [
        LibraryView::class,
        HistoryView::class,
        UpdatesView::class,
    ],
    version = 1, // Start with 1, but it will pick up legacy schema
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class EphyraDatabase : RoomDatabase() {
    abstract fun mangaDao(): ephyra.data.room.daos.MangaDao
    abstract fun chapterDao(): ephyra.data.room.daos.ChapterDao
    abstract fun categoryDao(): ephyra.data.room.daos.CategoryDao
    abstract fun historyDao(): ephyra.data.room.daos.HistoryDao
    abstract fun trackDao(): ephyra.data.room.daos.TrackDao
    abstract fun updateDao(): ephyra.data.room.daos.UpdateDao
    abstract fun extensionRepoDao(): ephyra.data.room.daos.ExtensionRepoDao
}

@androidx.room.Entity(tableName = "sources")
data class SourceEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) @androidx.room.ColumnInfo(name = "_id") val id: Long,
    @androidx.room.ColumnInfo(name = "name") val name: String,
    @androidx.room.ColumnInfo(name = "lang") val lang: String,
)
