package ephyra.data.room.entities

import androidx.room.*

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "sort") val sort: Int,
    @ColumnInfo(name = "flags") val flags: Long,
)

@Entity(
    tableName = "mangas_categories",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["_id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["manga_id"]),
        Index(value = ["category_id"])
    ]
)
data class MangaCategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
)
