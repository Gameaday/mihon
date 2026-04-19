package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetFavoritesByCanonicalIdTest {

    /**
     * Minimal fake repository for testing the interactor layer without DB.
     */
    private class FakeMangaRepository : ephyra.domain.manga.repository.MangaRepository {
        val mangas = mutableListOf<Manga>()

        override suspend fun getFavoritesByCanonicalId(canonicalId: String, excludeMangaId: Long): List<Manga> {
            return mangas.filter {
                it.favorite && it.canonicalId == canonicalId && it.id != excludeMangaId
            }
        }

        override suspend fun getDeadFavorites(deadSinceBefore: Long): List<Manga> {
            return mangas.filter {
                it.favorite && it.deadSince != null && it.deadSince!! > 0 && it.deadSince!! < deadSinceBefore
            }
        }

        // Unused stubs
        override suspend fun getMangaById(id: Long): Manga = throw NotImplementedError()
        override suspend fun isMangaFavorite(id: Long): Boolean = throw NotImplementedError()
        override suspend fun getMangaByIdAsFlow(id: Long) = throw NotImplementedError()
        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long) = throw NotImplementedError()
        override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = throw NotImplementedError()
        override suspend fun getFavorites(): List<Manga> = throw NotImplementedError()
        override suspend fun getReadMangaNotInLibrary(): List<Manga> = throw NotImplementedError()
        override suspend fun getLibraryManga(): List<ephyra.domain.library.model.LibraryManga> =
            throw NotImplementedError()
        override fun getLibraryMangaAsFlow() = throw NotImplementedError()
        override fun getFavoritesBySourceId(sourceId: Long) = throw NotImplementedError()
        override suspend fun getDuplicateLibraryManga(id: Long, title: String) = throw NotImplementedError()
        override suspend fun getUpcomingManga(statuses: Set<Long>) = throw NotImplementedError()
        override suspend fun resetViewerFlags() = throw NotImplementedError()
        override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) = throw NotImplementedError()
        override suspend fun update(update: ephyra.domain.manga.model.MangaUpdate) = throw NotImplementedError()
        override suspend fun updateAll(
            mangaUpdates: List<ephyra.domain.manga.model.MangaUpdate>,
        ) = throw NotImplementedError()
        override suspend fun clearMetadataSource(mangaId: Long) = throw NotImplementedError()
        override suspend fun clearCanonicalId(mangaId: Long) = throw NotImplementedError()
        override suspend fun insertNetworkManga(manga: List<Manga>) = throw NotImplementedError()
        override suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Long) {}
        override suspend fun getAllMangaSourceAndUrl(): List<Pair<Long, String>> = emptyList()
    }

    private fun testManga(
        id: Long,
        source: Long = 1L,
        canonicalId: String? = null,
        favorite: Boolean = true,
        deadSince: Long? = null,
    ) = Manga.create().copy(
        id = id,
        source = source,
        canonicalId = canonicalId,
        favorite = favorite,
        deadSince = deadSince,
    )

    // --- GetFavoritesByCanonicalId tests ---

    @Test
    fun `returns matching favorites with same canonical ID`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.addAll(
            listOf(
                testManga(1L, source = 100L, canonicalId = "al:21"),
                testManga(2L, source = 200L, canonicalId = "al:21"),
            ),
        )
        val interactor = GetFavoritesByCanonicalId(repo)
        val results = interactor.await("al:21", excludeMangaId = 1L)
        assertEquals(1, results.size)
        assertEquals(2L, results[0].id)
    }

    @Test
    fun `excludes the source manga from results`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(1L, canonicalId = "al:21"))
        val interactor = GetFavoritesByCanonicalId(repo)
        val results = interactor.await("al:21", excludeMangaId = 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns empty when no manga match canonical ID`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(1L, canonicalId = "al:21"))
        val interactor = GetFavoritesByCanonicalId(repo)
        val results = interactor.await("mal:999", excludeMangaId = 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `excludes non-favorite manga`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(2L, canonicalId = "al:21", favorite = false))
        val interactor = GetFavoritesByCanonicalId(repo)
        val results = interactor.await("al:21", excludeMangaId = 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns multiple matches from different sources`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.addAll(
            listOf(
                testManga(1L, source = 100L, canonicalId = "al:21"),
                testManga(2L, source = 200L, canonicalId = "al:21"),
                testManga(3L, source = 300L, canonicalId = "al:21"),
            ),
        )
        val interactor = GetFavoritesByCanonicalId(repo)
        val results = interactor.await("al:21", excludeMangaId = 1L)
        assertEquals(2, results.size)
        assertEquals(setOf(2L, 3L), results.map { it.id }.toSet())
    }

    @Test
    fun `does not match null canonical IDs`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(2L, canonicalId = null))
        val interactor = GetFavoritesByCanonicalId(repo)
        val results = interactor.await("al:21", excludeMangaId = 1L)
        assertTrue(results.isEmpty())
    }

    // --- GetDeadFavorites tests ---

    @Test
    fun `GetDeadFavorites returns manga dead before cutoff`() = runTest {
        val repo = FakeMangaRepository()
        val threeDaysAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        repo.mangas.add(testManga(1L, deadSince = threeDaysAgo - 1000))
        val interactor = GetDeadFavorites(repo)
        val results = interactor.await(threeDaysAgo)
        assertEquals(1, results.size)
    }

    @Test
    fun `GetDeadFavorites excludes manga dead after cutoff`() = runTest {
        val repo = FakeMangaRepository()
        val threeDaysAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        repo.mangas.add(testManga(1L, deadSince = threeDaysAgo + 1000))
        val interactor = GetDeadFavorites(repo)
        val results = interactor.await(threeDaysAgo)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `GetDeadFavorites excludes non-favorites`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(1L, deadSince = 1000L, favorite = false))
        val interactor = GetDeadFavorites(repo)
        val results = interactor.await(System.currentTimeMillis())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `GetDeadFavorites excludes null deadSince`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(1L, deadSince = null))
        val interactor = GetDeadFavorites(repo)
        val results = interactor.await(System.currentTimeMillis())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `GetDeadFavorites excludes zero deadSince`() = runTest {
        val repo = FakeMangaRepository()
        repo.mangas.add(testManga(1L, deadSince = 0L))
        val interactor = GetDeadFavorites(repo)
        val results = interactor.await(System.currentTimeMillis())
        assertTrue(results.isEmpty())
    }
}
