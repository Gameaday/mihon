package ephyra.data.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ephyra.core.common.util.lang.toLong
import ephyra.data.room.daos.UpdateDao
import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.domain.updates.repository.UpdatesRepository

class UpdatesRepositoryImpl(
    private val updateDao: UpdateDao,
) : UpdatesRepository {

    override suspend fun awaitWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> {
        return updateDao.getUpdatesByReadStatusBlocking(read, after, limit)
            .map(UpdatesMapper::mapUpdatesWithRelations)
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
    ): Flow<List<UpdatesWithRelations>> {
        return updateDao.getRecentUpdatesWithFilters(
            after = after,
            limit = limit,
            read = unread?.let { !it },
            bookmarked = bookmarked,
            hideExcludedScanlators = if (hideExcludedScanlators) 1 else 0,
        ).map { list -> list.map(UpdatesMapper::mapUpdatesWithRelations) }
    }

    override fun subscribeWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<UpdatesWithRelations>> {
        return updateDao.getUpdatesByReadStatus(read, after, limit)
            .map { list -> list.map(UpdatesMapper::mapUpdatesWithRelations) }
    }
}
