package ephyra.data.category

import ephyra.data.room.entities.CategoryEntity
import ephyra.domain.category.model.Category

object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
        )
    }

    fun mapCategory(entity: CategoryEntity): Category = mapCategory(
        id = entity.id,
        name = entity.name,
        order = entity.sort.toLong(),
        flags = entity.flags,
    )
}
