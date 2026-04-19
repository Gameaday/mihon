package ephyra.domain.manga.interactor

import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Tests for the post-processing pipeline in [FindContentSource]:
 * URL deduplication, provider diversity, and result ranking.
 */
@Execution(ExecutionMode.CONCURRENT)
class FindContentSourceTest {

    private val interactor = FindContentSource(
        sourceManager = FakeSourceManager(),
        getFavoritesByCanonicalId = GetFavoritesByCanonicalId(
            FakeMangaRepository(),
        ),
    )

    // ── deduplicateByUrl ───────────────────────────────────────────────

    @Test
    fun `deduplicateByUrl removes duplicate URLs keeping highest confidence`() {
        val matches = listOf(
            sourceMatch(url = "/manga/1", sourceId = 1, sourceName = "MangaDex", confidence = 0.58),
            sourceMatch(url = "/manga/1", sourceId = 2, sourceName = "MangaDex", confidence = 0.60),
            sourceMatch(url = "/manga/1", sourceId = 3, sourceName = "MangaDex", confidence = 0.55),
            sourceMatch(url = "/manga/2", sourceId = 4, sourceName = "OtherSource", confidence = 0.70),
        )

        val result = interactor.deduplicateByUrl(matches)

        result shouldHaveSize 2
        result.first { it.manga.url == "/manga/1" }.confidence shouldBe 0.60
        result.first { it.manga.url == "/manga/2" }.confidence shouldBe 0.70
    }

    @Test
    fun `deduplicateByUrl handles unique URLs`() {
        val matches = listOf(
            sourceMatch(url = "/a", sourceId = 1, sourceName = "A", confidence = 0.9),
            sourceMatch(url = "/b", sourceId = 2, sourceName = "B", confidence = 0.8),
            sourceMatch(url = "/c", sourceId = 3, sourceName = "C", confidence = 0.7),
        )

        val result = interactor.deduplicateByUrl(matches)
        result shouldHaveSize 3
    }

    @Test
    fun `deduplicateByUrl handles empty list`() {
        interactor.deduplicateByUrl(emptyList()) shouldHaveSize 0
    }

    // ── diversifyByProvider ──────────────────────────────────────────────

    @Test
    fun `diversifyByProvider picks one per provider then fills remaining`() {
        val matches = listOf(
            sourceMatch(url = "/a1", sourceId = 1, sourceName = "MangaDex", confidence = 0.90),
            sourceMatch(url = "/a2", sourceId = 2, sourceName = "MangaDex", confidence = 0.85),
            sourceMatch(url = "/b1", sourceId = 3, sourceName = "MangaSee", confidence = 0.80),
            sourceMatch(url = "/c1", sourceId = 4, sourceName = "Batoto", confidence = 0.70),
        )

        val result = interactor.diversifyByProvider(matches, limit = 3)

        result shouldHaveSize 3
        // First three: one per provider sorted by confidence
        result[0].sourceName shouldBe "MangaDex"
        result[0].confidence shouldBe 0.90
        result[1].sourceName shouldBe "MangaSee"
        result[2].sourceName shouldBe "Batoto"
    }

    @Test
    fun `diversifyByProvider returns all when under limit`() {
        val matches = listOf(
            sourceMatch(url = "/a", sourceId = 1, sourceName = "MangaDex", confidence = 0.9),
            sourceMatch(url = "/b", sourceId = 2, sourceName = "MangaDex", confidence = 0.8),
        )

        val result = interactor.diversifyByProvider(matches, limit = 5)
        result shouldHaveSize 2
    }

    @Test
    fun `diversifyByProvider is case-insensitive on provider names`() {
        val matches = listOf(
            sourceMatch(url = "/a", sourceId = 1, sourceName = "MangaDex", confidence = 0.90),
            sourceMatch(url = "/b", sourceId = 2, sourceName = "mangadex", confidence = 0.85),
            sourceMatch(url = "/c", sourceId = 3, sourceName = "MANGADEX", confidence = 0.80),
            sourceMatch(url = "/d", sourceId = 4, sourceName = "OtherSource", confidence = 0.70),
        )

        val result = interactor.diversifyByProvider(matches, limit = 2)

        result shouldHaveSize 2
        result.map { it.sourceName }.toSet() shouldBe setOf("MangaDex", "OtherSource")
    }

    // ── rankResults ─────────────────────────────────────────────────────

    @Test
    fun `rankResults prioritizes sources with chapters over empty sources`() {
        val matches = listOf(
            sourceMatch(url = "/a", sourceId = 1, sourceName = "Dead", confidence = 0.95, chapterCount = 0),
            sourceMatch(url = "/b", sourceId = 2, sourceName = "Alive", confidence = 0.80, chapterCount = 50),
        )

        val result = interactor.rankResults(matches, maxResults = 5)

        result[0].sourceName shouldBe "Alive"
        result[1].sourceName shouldBe "Dead"
    }

    @Test
    fun `rankResults sorts by confidence then chapter count within same availability tier`() {
        val matches = listOf(
            sourceMatch(url = "/a", sourceId = 1, sourceName = "A", confidence = 0.70, chapterCount = 100),
            sourceMatch(url = "/b", sourceId = 2, sourceName = "B", confidence = 0.90, chapterCount = 50),
            sourceMatch(url = "/c", sourceId = 3, sourceName = "C", confidence = 0.90, chapterCount = 200),
        )

        val result = interactor.rankResults(matches, maxResults = 5)

        // B and C tie on confidence (0.90), C has more chapters → C first
        result[0].sourceName shouldBe "C"
        result[1].sourceName shouldBe "B"
        result[2].sourceName shouldBe "A"
    }

    @Test
    fun `rankResults respects maxResults limit`() {
        val matches = (1..10).map {
            sourceMatch(
                url = "/$it",
                sourceId = it.toLong(),
                sourceName = "S$it",
                confidence = 0.5,
                chapterCount = it,
            )
        }

        val result = interactor.rankResults(matches, maxResults = 3)
        result shouldHaveSize 3
    }

    @Test
    fun `rankResults keeps unknown chapter count sources between alive and dead`() {
        val matches = listOf(
            sourceMatch(url = "/a", sourceId = 1, sourceName = "Dead", confidence = 0.90, chapterCount = 0),
            sourceMatch(
                url = "/b",
                sourceId = 2,
                sourceName = "Unknown",
                confidence = 0.85,
                chapterCount = FindContentSource.CHAPTER_COUNT_UNKNOWN,
            ),
            sourceMatch(url = "/c", sourceId = 3, sourceName = "Alive", confidence = 0.80, chapterCount = 50),
        )

        val result = interactor.rankResults(matches, maxResults = 5)

        // Alive first (chapters > 0), then Unknown and Dead
        result[0].sourceName shouldBe "Alive"
    }

    // ── Full pipeline (dedup + diversity + rank) ────────────────────────

    @Test
    fun `full pipeline deduplicates then diversifies identical MangaDex results`() {
        // Simulates the exact issue: 5 identical MangaDex results with same URL
        val matches = (1..5).map {
            sourceMatch(
                url = "/manga/abc-123",
                sourceId = it.toLong(),
                sourceName = "MangaDex",
                confidence = 0.58,
                chapterCount = 0,
            )
        }

        val deduped = interactor.deduplicateByUrl(matches)
        deduped shouldHaveSize 1

        val diverse = interactor.diversifyByProvider(deduped, limit = 5)
        diverse shouldHaveSize 1

        val ranked = interactor.rankResults(diverse, maxResults = 5)
        ranked shouldHaveSize 1
    }

    @Test
    fun `full pipeline promotes source with content over dead duplicate sources`() {
        val matches = listOf(
            // 3 MangaDex duplicates (same URL, different lang variants) - dead
            sourceMatch(url = "/manga/1", sourceId = 1, sourceName = "MangaDex", confidence = 0.58, chapterCount = 0),
            sourceMatch(url = "/manga/1", sourceId = 2, sourceName = "MangaDex", confidence = 0.58, chapterCount = 0),
            sourceMatch(url = "/manga/1", sourceId = 3, sourceName = "MangaDex", confidence = 0.58, chapterCount = 0),
            // Different source with actual content
            sourceMatch(
                url = "/series/xyz",
                sourceId = 4,
                sourceName = "MangaSee",
                confidence = 0.55,
                chapterCount = 42,
            ),
        )

        val deduped = interactor.deduplicateByUrl(matches)
        deduped shouldHaveSize 2

        val ranked = interactor.rankResults(deduped, maxResults = 5)
        // MangaSee should be first despite lower confidence because it has chapters
        ranked[0].sourceName shouldBe "MangaSee"
        ranked[0].chapterCount shouldBe 42
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun sourceMatch(
        url: String = "/manga/test",
        sourceId: Long = 1,
        sourceName: String = "TestSource",
        confidence: Double = 0.5,
        chapterCount: Int = FindContentSource.CHAPTER_COUNT_UNKNOWN,
    ): FindContentSource.SourceMatch {
        val manga = SManga.create().apply {
            this.url = url
            this.title = "Test Manga"
        }
        return FindContentSource.SourceMatch(
            manga = manga,
            source = FakeCatalogueSource(sourceId, sourceName),
            sourceId = sourceId,
            sourceName = sourceName,
            confidence = confidence,
            chapterCount = chapterCount,
        )
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────

private class FakeCatalogueSource(
    override val id: Long,
    override val name: String,
    override val lang: String = "en",
) : CatalogueSource {
    override val supportsLatest: Boolean = false
    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
        MangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
    override fun getFilterList() = FilterList()
    override suspend fun getMangaDetails(manga: SManga) = manga
    override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}

private class FakeSourceManager : SourceManager {
    override val isInitialized = MutableStateFlow(true)
    override val catalogueSources: Flow<List<CatalogueSource>> = flowOf(emptyList())
    override fun get(sourceKey: Long) = null
    override fun getOrStub(sourceKey: Long) = StubSource(sourceKey, "en", "Stub")
    override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()
    override fun getCatalogueSources() = emptyList<CatalogueSource>()
    override fun getStubSources() = emptyList<StubSource>()
}

private class FakeMangaRepository : MangaRepository {
    override suspend fun getFavoritesByCanonicalId(canonicalId: String, excludeMangaId: Long) =
        emptyList<Manga>()
    override suspend fun getMangaById(id: Long): Manga = throw NotImplementedError()
    override suspend fun isMangaFavorite(id: Long): Boolean = throw NotImplementedError()
    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> = throw NotImplementedError()
    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long) = null
    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> =
        throw NotImplementedError()
    override suspend fun getDeadFavorites(deadSinceBefore: Long) = emptyList<Manga>()
    override suspend fun getFavorites() = emptyList<Manga>()
    override suspend fun getReadMangaNotInLibrary() = emptyList<Manga>()
    override suspend fun getLibraryManga() = emptyList<LibraryManga>()
    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> = throw NotImplementedError()
    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> =
        throw NotImplementedError()
    override suspend fun getDuplicateLibraryManga(id: Long, title: String) =
        emptyList<MangaWithChapterCount>()
    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> =
        throw NotImplementedError()
    override suspend fun resetViewerFlags() = false
    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {}
    override suspend fun update(update: MangaUpdate) = false
    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>) = false
    override suspend fun clearMetadataSource(mangaId: Long) = false
    override suspend fun clearCanonicalId(mangaId: Long) = false
    override suspend fun insertNetworkManga(manga: List<Manga>) = emptyList<Manga>()
    override suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Long) {}
    override suspend fun getAllMangaSourceAndUrl(): List<Pair<Long, String>> = emptyList()
}
