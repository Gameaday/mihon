package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track

/**
 * Unit tests for [MatchUnlinkedManga] — comprehensive canonical ID resolution.
 */
@Execution(ExecutionMode.CONCURRENT)
class MatchUnlinkedMangaTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var trackerManager: TrackerManager
    private lateinit var getTracks: GetTracks
    private lateinit var matchUnlinkedManga: MatchUnlinkedManga

    private lateinit var muTracker: Tracker

    private fun testManga(
        id: Long = 1L,
        title: String = "Test Manga",
        canonicalId: String? = null,
        alternativeTitles: List<String> = emptyList(),
    ) = Manga.create().copy(
        id = id,
        title = title,
        canonicalId = canonicalId,
        alternativeTitles = alternativeTitles,
        favorite = true,
    )

    private fun testTrack(
        mangaId: Long,
        trackerId: Long,
        remoteId: Long,
    ) = Track(
        id = 0L,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = null,
        title = "",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    private fun testTrackSearch(
        title: String,
        remoteId: Long,
        summary: String = "",
        authors: List<String> = emptyList(),
        artists: List<String> = emptyList(),
        coverUrl: String = "",
        alternativeTitles: List<String> = emptyList(),
    ): TrackSearch {
        val ts = TrackSearch()
        ts.title = title
        ts.remote_id = remoteId
        ts.summary = summary
        ts.authors = authors
        ts.artists = artists
        ts.cover_url = coverUrl
        ts.alternative_titles = alternativeTitles
        return ts
    }

    @BeforeEach
    fun setup() {
        mangaRepository = mockk(relaxed = true)
        trackerManager = mockk(relaxed = true)
        getTracks = mockk(relaxed = true)
        muTracker = mockk(relaxed = true)

        // MangaUpdates tracker (ID 7) is the public-search tracker
        every { muTracker.id } returns 7L
        every { trackerManager.get(7L) } returns muTracker

        matchUnlinkedManga = MatchUnlinkedManga(mangaRepository, trackerManager, getTracks)
    }

    // ========== Empty / all-linked scenarios ==========

    @Test
    fun `returns zero result when no favorites`() = runTest {
        coEvery { mangaRepository.getFavorites() } returns emptyList()

        val result = matchUnlinkedManga.await()

        result shouldBe MatchUnlinkedManga.MatchResult(0, 0, 0)
    }

    @Test
    fun `returns zero result when all favorites already have canonical IDs`() = runTest {
        coEvery { mangaRepository.getFavorites() } returns listOf(
            testManga(id = 1L, canonicalId = "mu:100"),
            testManga(id = 2L, canonicalId = "al:200"),
        )

        val result = matchUnlinkedManga.await()

        result shouldBe MatchUnlinkedManga.MatchResult(0, 0, 0)
    }

    // ========== Tracker binding resolution (Phase 1) ==========

    @Test
    fun `resolves from tracker binding before API search`() = runTest {
        val manga = testManga(id = 1L, title = "One Piece")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns listOf(
            testTrack(mangaId = 1L, trackerId = 1L, remoteId = 21L), // MAL
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 1
        result.matched shouldBe 0
        result.total shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mal:21"
        // Should NOT call tracker.search since binding was found
        coVerify(exactly = 0) { muTracker.search(any()) }
    }

    @Test
    fun `resolves AniList binding`() = runTest {
        val manga = testManga(id = 1L, title = "Attack on Titan")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns listOf(
            testTrack(mangaId = 1L, trackerId = 2L, remoteId = 100L), // AniList
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 1
        updateSlot.captured.canonicalId shouldBe "al:100"
    }

    @Test
    fun `skips tracker binding with zero remoteId`() = runTest {
        val manga = testManga(id = 1L, title = "Test")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns listOf(
            testTrack(mangaId = 1L, trackerId = 1L, remoteId = 0L), // Zero = not yet synced
        )
        coEvery { muTracker.search("Test") } returns emptyList()

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 0
        result.matched shouldBe 0
    }

    @Test
    fun `skips non-authoritative tracker binding`() = runTest {
        val manga = testManga(id = 1L, title = "Test")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns listOf(
            testTrack(mangaId = 1L, trackerId = 3L, remoteId = 50L), // Kitsu (not authoritative)
        )
        coEvery { muTracker.search("Test") } returns emptyList()

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 0
        // Should fall through to API search
        coVerify(exactly = 1) { muTracker.search("Test") }
    }

    // ========== Public API search (Phase 2) ==========

    @Test
    fun `matches exact title from API search`() = runTest {
        val manga = testManga(id = 1L, title = "One Piece")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One Piece") } returns listOf(
            testTrackSearch("One Piece", 12345L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 0
        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:12345"
    }

    @Test
    fun `matches case-insensitive title`() = runTest {
        val manga = testManga(id = 1L, title = "one piece")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("one piece") } returns listOf(
            testTrackSearch("One Piece", 12345L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:12345"
    }

    @Test
    fun `matches via alternative title`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "Attack on Titan",
            alternativeTitles = listOf("Shingeki no Kyojin"),
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Attack on Titan") } returns listOf(
            testTrackSearch("Shingeki no Kyojin", 500L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:500"
    }

    @Test
    fun `matches via normalized title (strips punctuation)`() = runTest {
        val manga = testManga(id = 1L, title = "Re:Zero")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Re:Zero") } returns listOf(
            testTrackSearch("Re Zero", 600L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:600"
    }

    @Test
    fun `returns no match when no results`() = runTest {
        val manga = testManga(id = 1L, title = "Unknown Manga")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Unknown Manga") } returns emptyList()

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 0
        result.matched shouldBe 0
        result.total shouldBe 1
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `skips result with zero remoteId`() = runTest {
        val manga = testManga(id = 1L, title = "Test")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Test") } returns listOf(
            testTrackSearch("Test", 0L), // zero remote_id
        )

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 0
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    // ========== Progress callback ==========

    @Test
    fun `reports progress for each manga`() = runTest {
        coEvery { mangaRepository.getFavorites() } returns listOf(
            testManga(id = 1L, title = "A"),
            testManga(id = 2L, title = "B"),
            testManga(id = 3L, title = "C"),
        )
        coEvery { getTracks.await(any()) } returns emptyList()
        coEvery { muTracker.search(any()) } returns emptyList()

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        matchUnlinkedManga.await { current, total ->
            progressUpdates.add(current to total)
        }

        progressUpdates shouldBe listOf(1 to 3, 2 to 3, 3 to 3)
    }

    // ========== Mixed scenarios ==========

    @Test
    fun `processes multiple manga with mixed resolution`() = runTest {
        val manga1 = testManga(id = 1L, title = "Tracked Manga")
        val manga2 = testManga(id = 2L, title = "Searchable Manga")
        val manga3 = testManga(id = 3L, title = "Unknown Manga")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga1, manga2, manga3)

        // manga1: has tracker binding
        coEvery { getTracks.await(1L) } returns listOf(
            testTrack(mangaId = 1L, trackerId = 7L, remoteId = 100L),
        )
        // manga2: no tracker but found via search
        coEvery { getTracks.await(2L) } returns emptyList()
        coEvery { muTracker.search("Searchable Manga") } returns listOf(
            testTrackSearch("Searchable Manga", 200L),
        )
        // manga3: nothing found
        coEvery { getTracks.await(3L) } returns emptyList()
        coEvery { muTracker.search("Unknown Manga") } returns emptyList()

        coEvery { mangaRepository.update(any()) } returns true

        val result = matchUnlinkedManga.await()

        result.linked shouldBe 1
        result.matched shouldBe 1
        result.total shouldBe 3
    }

    // ========== normalizeTitle ==========

    @Test
    fun `normalizeTitle strips punctuation`() {
        MatchUnlinkedManga.normalizeTitle("Re:Zero") shouldBe "re zero"
    }

    @Test
    fun `normalizeTitle collapses whitespace`() {
        MatchUnlinkedManga.normalizeTitle("One   Piece") shouldBe "one piece"
    }

    @Test
    fun `normalizeTitle lowercases`() {
        MatchUnlinkedManga.normalizeTitle("ONE PIECE") shouldBe "one piece"
    }

    @Test
    fun `normalizeTitle handles mixed punctuation and spaces`() {
        MatchUnlinkedManga.normalizeTitle("Jujutsu Kaisen (TV)") shouldBe "jujutsu kaisen tv"
    }

    @Test
    fun `normalizeTitle preserves unicode letters`() {
        MatchUnlinkedManga.normalizeTitle("進撃の巨人") shouldBe "進撃の巨人"
    }

    // ========== Metadata enrichment ==========

    @Test
    fun `enriches missing description from search result`() = runTest {
        val manga = testManga(id = 1L, title = "One Piece", canonicalId = null)
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One Piece") } returns listOf(
            testTrackSearch(
                title = "One Piece",
                remoteId = 100L,
                summary = "A pirate adventure",
                authors = listOf("Eiichiro Oda"),
                artists = listOf("Eiichiro Oda"),
                coverUrl = "https://example.com/cover.jpg",
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Should have canonical ID update and metadata enrichment update
        updates.size shouldBe 2
        updates[0].canonicalId shouldBe "mu:100"
        updates[1].description shouldBe "A pirate adventure"
        updates[1].author shouldBe "Eiichiro Oda"
        updates[1].artist shouldBe "Eiichiro Oda"
        updates[1].thumbnailUrl shouldBe "https://example.com/cover.jpg"
    }

    @Test
    fun `does not overwrite existing metadata with authoritative data`() = runTest {
        val manga = Manga.create().copy(
            id = 1L,
            title = "One Piece",
            description = "My custom description",
            author = "Custom Author",
            artist = "Custom Artist",
            thumbnailUrl = "https://existing.com/cover.jpg",
            favorite = true,
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One Piece") } returns listOf(
            testTrackSearch(
                title = "One Piece",
                remoteId = 100L,
                summary = "Different description",
                authors = listOf("Different Author"),
                coverUrl = "https://different.com/cover.jpg",
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Should only have canonical ID update, no metadata overwrite
        updates.size shouldBe 1
        updates[0].canonicalId shouldBe "mu:100"
    }

    @Test
    fun `merges alternative titles from search result`() = runTest {
        val manga = testManga(id = 1L, title = "Attack on Titan")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Attack on Titan") } returns listOf(
            testTrackSearch(
                title = "Attack on Titan",
                remoteId = 200L,
                alternativeTitles = listOf("Shingeki no Kyojin", "進撃の巨人"),
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Should have canonical ID update and alt titles update
        val altTitlesUpdate = updates.find { it.alternativeTitles != null }
        altTitlesUpdate shouldNotBe null
        altTitlesUpdate!!.alternativeTitles shouldBe listOf("Shingeki no Kyojin", "進撃の巨人")
    }
}
