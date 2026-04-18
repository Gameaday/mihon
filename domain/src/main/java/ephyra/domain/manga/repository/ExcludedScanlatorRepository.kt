package ephyra.domain.manga.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the scanlator exclusion list per manga.
 *
 * The Android implementation ([ephyra.data.manga.ExcludedScanlatorRepositoryImpl]) lives in `:data`
 * where it may access the Room DAO.  This interface allows [GetExcludedScanlators] and
 * [SetExcludedScanlators] to remain in `:core:domain` without any `:data` dependency.
 */
interface ExcludedScanlatorRepository {

    suspend fun getExcludedScanlators(mangaId: Long): Set<String>

    fun subscribeExcludedScanlators(mangaId: Long): Flow<Set<String>>

    suspend fun setExcludedScanlators(mangaId: Long, scanlators: Set<String>)
}
