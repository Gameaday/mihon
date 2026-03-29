package ephyra.data.source

import ephyra.data.room.daos.SourceDao
import ephyra.data.room.entities.SourceEntity
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.StubSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StubSourceRepositoryImpl(
    private val sourceDao: SourceDao,
) : StubSourceRepository {

    override fun subscribeAll(): Flow<List<StubSource>> {
        return sourceDao.subscribeAll().map { list ->
            list.map(SourceMapper::mapStubSource)
        }
    }

    override suspend fun getStubSource(id: Long): StubSource? {
        return sourceDao.getStubSource(id)?.let(SourceMapper::mapStubSource)
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        sourceDao.upsert(SourceEntity(id, lang, name))
    }
}

object SourceMapper {
    fun mapStubSource(entity: SourceEntity): StubSource {
        return StubSource(id = entity.id, lang = entity.lang, name = entity.name)
    }
}
