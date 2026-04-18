package ephyra.data.manga

import ephyra.data.room.daos.ExcludedScanlatorDao
import ephyra.data.room.entities.ExcludedScanlatorEntity
import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExcludedScanlatorRepositoryImpl(
    private val dao: ExcludedScanlatorDao,
) : ExcludedScanlatorRepository {

    override suspend fun getExcludedScanlators(mangaId: Long): Set<String> {
        return dao.getExcludedScanlators(mangaId).toSet()
    }

    override fun subscribeExcludedScanlators(mangaId: Long): Flow<Set<String>> {
        return dao.getExcludedScanlatorsAsFlow(mangaId).map { it.toSet() }
    }

    override suspend fun setExcludedScanlators(mangaId: Long, scanlators: Set<String>) {
        val current = dao.getExcludedScanlators(mangaId).toSet()
        val toAdd = scanlators - current
        val toRemove = current - scanlators
        if (toRemove.isNotEmpty()) {
            dao.deleteByMangaIdAndScanlators(mangaId, toRemove.toList())
        }
        if (toAdd.isNotEmpty()) {
            dao.insertAll(toAdd.map { ExcludedScanlatorEntity(mangaId = mangaId, scanlator = it) })
        }
    }
}
