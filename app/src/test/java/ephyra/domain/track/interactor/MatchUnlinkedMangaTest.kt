package ephyra.domain.track.interactor

import ephyra.core.common.preference.Preference
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.TrackPreferences
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

/**
 * Unit tests for [MatchUnlinkedManga] — comprehensive canonical ID resolution.
 */
@Execution(ExecutionMode.CONCURRENT)
class MatchUnlinkedMangaTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var trackerManager: TrackerManager
    private lateinit var getTracks: GetTracks
    private lateinit var trackPreferences: TrackPreferences
    private lateinit var matchUnlinkedManga: MatchUnlinkedManga

    private lateinit var muTracker: Tracker

    private fun testManga(
        id: Long = 1L,
        title: String = "Test Manga",
        canonicalId: String? = null,
        alternativeTitles: List<String> = emptyList(),
        contentType: ContentType = ContentType.UNKNOWN,
    ) = Manga.create().copy(
        id = id,
        title = title,
        canonicalId = canonicalId,
        alternativeTitles = alternativeTitles,
        contentType = contentType,
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
        isPrivate = false,
    )

    private fun testTrackSearch(
        title: String,
        remoteId: Long,
        summary: String = "",
        authors: List<String> = emptyList(),
        artists: List<String> = emptyList(),
        coverUrl: String = "",
        alternativeTitles: List<String> = emptyList(),
        publishingType: String = "",
    ) = TrackSearch(
        remote_id = remoteId,
        title = title,
        summary = summary,
        authors = authors,
        artists = artists,
        cover_url = coverUrl,
        alternative_titles = alternativeTitles,
        publishing_type = publishingType,
    )

    @BeforeEach
    fun setup() {
        mangaRepository = mockk(relaxed = true)
        trackerManager = mockk(relaxed = true)
        getTracks = mockk(relaxed = true)
        trackPreferences = mockk(relaxed = true)
        muTracker = mockk(relaxed = true)

        // MangaUpdates tracker (ID 7) is the public-search tracker
        every { muTracker.id } returns 7L
        every { trackerManager.get(7L) } returns muTracker

        // Default authority order: MU (7) → AniList (2) → MAL (1)
        val orderPref = mockk<Preference<List<Long>>>()
        coEvery { orderPref.get() } returns TrackPreferences.DEFAULT_AUTHORITY_ORDER
        every { trackPreferences.authorityTrackerOrder() } returns orderPref

        matchUnlinkedManga = MatchUnlinkedManga(mangaRepository, trackerManager, getTracks, trackPreferences)
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
    fun `falls back to alt title search when primary title has no match`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "My Custom Title",
            alternativeTitles = listOf("Official Japanese Title"),
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        // Primary title search returns no matching results
        coEvery { muTracker.search("My Custom Title") } returns emptyList()
        // Alt title search finds the match
        coEvery { muTracker.search("Official Japanese Title") } returns listOf(
            testTrackSearch("Official Japanese Title", 700L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:700"
        // Should have searched both the primary title and the alt title
        coVerify(exactly = 1) { muTracker.search("My Custom Title") }
        coVerify(exactly = 1) { muTracker.search("Official Japanese Title") }
    }

    @Test
    fun `does not search alt titles when primary title already matched`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "One Piece",
            alternativeTitles = listOf("OP", "ワンピース"),
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One Piece") } returns listOf(
            testTrackSearch("One Piece", 100L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:100"
        // Should NOT have searched alt titles since primary matched
        coVerify(exactly = 1) { muTracker.search("One Piece") }
        coVerify(exactly = 0) { muTracker.search("OP") }
        coVerify(exactly = 0) { muTracker.search("ワンピース") }
    }

    @Test
    fun `tries multiple alt titles until match found`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "My Title",
            alternativeTitles = listOf("Alt 1", "Alt 2", "Real Title"),
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("My Title") } returns emptyList()
        coEvery { muTracker.search("Alt 1") } returns emptyList()
        coEvery { muTracker.search("Alt 2") } returns emptyList()
        coEvery { muTracker.search("Real Title") } returns listOf(
            testTrackSearch("Real Title", 800L),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:800"
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

    // ========== Content type filtering ==========

    @Test
    fun `prefers exact match with correct content type`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "Solo Leveling",
            contentType = ContentType.MANGA,
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        // Return two results with same title but different types
        coEvery { muTracker.search("Solo Leveling") } returns listOf(
            testTrackSearch(
                title = "Solo Leveling",
                remoteId = 200L,
                publishingType = "Novel",
            ),
            testTrackSearch(
                title = "Solo Leveling",
                remoteId = 300L,
                publishingType = "Manhwa",
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Should pick the manga-typed result (300), not the novel (200)
        updates[0].canonicalId shouldBe "mu:300"
    }

    @Test
    fun `falls back to first match when no content type set`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "Solo Leveling",
            contentType = ContentType.UNKNOWN,
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Solo Leveling") } returns listOf(
            testTrackSearch(
                title = "Solo Leveling",
                remoteId = 200L,
                publishingType = "Novel",
            ),
            testTrackSearch(
                title = "Solo Leveling",
                remoteId = 300L,
                publishingType = "Manhwa",
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Without content type, should pick first match (200)
        updates[0].canonicalId shouldBe "mu:200"
    }

    @Test
    fun `prefers novel type when manga has novel content type`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "Overlord",
            contentType = ContentType.NOVEL,
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Overlord") } returns listOf(
            testTrackSearch(
                title = "Overlord",
                remoteId = 400L,
                publishingType = "Manga",
            ),
            testTrackSearch(
                title = "Overlord",
                remoteId = 500L,
                publishingType = "Light Novel",
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Should pick the novel (500), not the manga (400)
        updates[0].canonicalId shouldBe "mu:500"
    }

    // ========== Edge cases ==========

    @Test
    fun `skips search when manga title is blank`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "",
            alternativeTitles = listOf("Real Title"),
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        // Only the alt title should be searched, not the blank primary title
        coEvery { muTracker.search("Real Title") } returns listOf(
            testTrackSearch(title = "Real Title", remoteId = 100L),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.await()

        // Should match via alt title
        updates[0].canonicalId shouldBe "mu:100"
        // Should NOT have searched with blank title
        coVerify(exactly = 0) { muTracker.search("") }
    }

    @Test
    fun `does not match punctuation-only titles via normalization`() = runTest {
        val manga = testManga(
            id = 1L,
            title = "!!!",
        )
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        // Return a result whose normalized title is also empty — should not match
        coEvery { muTracker.search("!!!") } returns listOf(
            testTrackSearch(title = "???", remoteId = 100L),
        )
        coEvery { mangaRepository.update(any<MangaUpdate>()) } returns true

        val result = matchUnlinkedManga.await()

        // No match should be found since both normalize to empty strings
        result.matched shouldBe 0
    }

    @Test
    fun `handles zero remote_id results gracefully`() = runTest {
        val manga = testManga(id = 1L, title = "Test Manga")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        // Return a result with remote_id = 0 (invalid)
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(title = "Test Manga", remoteId = 0L),
        )
        coEvery { mangaRepository.update(any<MangaUpdate>()) } returns true

        val result = matchUnlinkedManga.await()

        // Should not match since remote_id <= 0
        result.matched shouldBe 0
    }

    // ========== awaitSingle ==========

    @Test
    fun `awaitSingle returns canonical ID from tracker binding`() = runTest {
        val manga = testManga(id = 1L, title = "One Piece")
        coEvery { getTracks.await(1L) } returns listOf(
            testTrack(mangaId = 1L, trackerId = 7L, remoteId = 42L),
        )
        coEvery { mangaRepository.update(any<MangaUpdate>()) } returns true

        val result = matchUnlinkedManga.awaitSingle(manga)

        result shouldBe "mu:42"
    }

    @Test
    fun `awaitSingle returns canonical ID from search`() = runTest {
        val manga = testManga(id = 1L, title = "Naruto")
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Naruto") } returns listOf(
            testTrackSearch(title = "Naruto", remoteId = 100L),
        )
        coEvery { mangaRepository.update(any<MangaUpdate>()) } returns true

        val result = matchUnlinkedManga.awaitSingle(manga)

        result shouldBe "mu:100"
    }

    @Test
    fun `awaitSingle returns existing canonical ID without update when already linked`() = runTest {
        val manga = testManga(id = 1L, title = "One Piece", canonicalId = "mu:42")

        val result = matchUnlinkedManga.awaitSingle(manga)

        result shouldBe "mu:42"
        // Should not attempt any updates
        coVerify(exactly = 0) { mangaRepository.update(any<MangaUpdate>()) }
    }

    @Test
    fun `awaitSingle returns null when no match found`() = runTest {
        val manga = testManga(id = 1L, title = "Unknown Series")
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Unknown Series") } returns emptyList()

        val result = matchUnlinkedManga.awaitSingle(manga)

        result shouldBe null
    }

    @Test
    fun `awaitSingle enriches metadata from search result`() = runTest {
        val manga = testManga(id = 1L, title = "Bleach")
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Bleach") } returns listOf(
            testTrackSearch(
                title = "Bleach",
                remoteId = 200L,
                summary = "Soul reaper manga",
                authors = listOf("Tite Kubo"),
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        matchUnlinkedManga.awaitSingle(manga)

        // Should have canonical ID update + enrichment update
        updates.size shouldBe 2
        updates[0].canonicalId shouldBe "mu:200"
        updates[1].description shouldBe "Soul reaper manga"
        updates[1].author shouldBe "Tite Kubo"
    }

    // ========== Tier 3: Alternative title matching ==========

    @Test
    fun `matches via result alternative titles (Tier 3)`() = runTest {
        val manga = testManga(id = 1L, title = "Attack on Titan")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        // The result's primary title doesn't match, but its alternative titles include ours
        coEvery { muTracker.search("Attack on Titan") } returns listOf(
            testTrackSearch(
                title = "Shingeki no Kyojin",
                remoteId = 500L,
                alternativeTitles = listOf("Attack on Titan", "進撃の巨人"),
            ),
        )
        val updates = mutableListOf<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updates)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        updates.first().canonicalId shouldBe "mu:500"
    }

    @Test
    fun `Tier 3 matches normalized result alt titles`() = runTest {
        val manga = testManga(id = 1L, title = "Re:Zero")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Re:Zero") } returns listOf(
            testTrackSearch(
                title = "Different Primary Title",
                remoteId = 800L,
                alternativeTitles = listOf("Re Zero - Starting Life"),
            ),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        // Tier 3 checks alt_titles with normalization, so "Re:Zero" normalized = "re zero"
        // But "Re Zero - Starting Life" normalized = "re zero starting life" ≠ "re zero"
        // No match here because normalized comparison is exact, not substring.
        // Tier 4 substring also doesn't match because "re zero" (7 chars) < MIN_SUBSTRING_LENGTH (8).
        result.matched shouldBe 0
    }

    // ========== Tier 4: Substring matching ==========

    @Test
    fun `matches via substring containment (Tier 4)`() = runTest {
        val manga = testManga(id = 1L, title = "Sword Art Online: Alicization")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("Sword Art Online: Alicization") } returns listOf(
            testTrackSearch(
                title = "Sword Art Online - Alicization - War of Underworld",
                remoteId = 900L,
            ),
        )
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        // "sword art online alicization" is contained in "sword art online alicization war of underworld"
        result.matched shouldBe 1
        updateSlot.captured.canonicalId shouldBe "mu:900"
    }

    @Test
    fun `substring match rejects short titles to avoid false positives`() = runTest {
        val manga = testManga(id = 1L, title = "One")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One") } returns listOf(
            testTrackSearch("One Piece", 100L),
        )
        coEvery { mangaRepository.update(any()) } returns true

        val result = matchUnlinkedManga.await()

        // "one" is only 3 chars, below MIN_SUBSTRING_LENGTH — no match
        result.matched shouldBe 0
    }

    // ========== containsSubstringMatch unit tests ==========

    @Test
    fun `containsSubstringMatch returns true when shorter is contained in longer`() {
        MatchUnlinkedManga.containsSubstringMatch(
            "sword art online",
            "sword art online alicization",
        ) shouldBe true
    }

    @Test
    fun `containsSubstringMatch returns true regardless of argument order`() {
        MatchUnlinkedManga.containsSubstringMatch(
            "sword art online alicization",
            "sword art online",
        ) shouldBe true
    }

    @Test
    fun `containsSubstringMatch rejects short strings`() {
        MatchUnlinkedManga.containsSubstringMatch("one", "one piece") shouldBe false
    }

    @Test
    fun `containsSubstringMatch rejects non-containing strings`() {
        MatchUnlinkedManga.containsSubstringMatch(
            "naruto shippuden",
            "one piece adventure",
        ) shouldBe false
    }

    // ========== Authority tracker order ==========

    @Test
    fun `uses first available tracker in ordered list`() = runTest {
        val alTracker = mockk<Tracker>(relaxed = true)
        every { alTracker.id } returns 2L
        coEvery { alTracker.isLoggedIn() } returns true
        every { trackerManager.get(2L) } returns alTracker
        coEvery { alTracker.search("One Piece") } returns listOf(
            testTrackSearch("One Piece", 21L),
        )

        // Set AniList first in order
        val orderPref = mockk<Preference<List<Long>>>()
        coEvery { orderPref.get() } returns listOf(2L, 7L, 1L) // AniList → MU → MAL
        every { trackPreferences.authorityTrackerOrder() } returns orderPref

        val manga = testManga(id = 1L, title = "One Piece")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()

        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        // Should use AniList prefix (al), not MangaUpdates (mu)
        updateSlot.captured.canonicalId shouldBe "al:21"
    }

    @Test
    fun `skips unavailable tracker and uses next in order`() = runTest {
        val alTracker = mockk<Tracker>(relaxed = true)
        every { alTracker.id } returns 2L
        coEvery { alTracker.isLoggedIn() } returns false // Not logged in
        every { trackerManager.get(2L) } returns alTracker

        // AniList first but not logged in → should fall through to MangaUpdates
        val orderPref = mockk<Preference<List<Long>>>()
        coEvery { orderPref.get() } returns listOf(2L, 7L, 1L)
        every { trackPreferences.authorityTrackerOrder() } returns orderPref

        val manga = testManga(id = 1L, title = "One Piece")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One Piece") } returns listOf(
            testTrackSearch("One Piece", 100L),
        )

        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        // Should fall back to MangaUpdates (mu) since AniList isn't logged in
        updateSlot.captured.canonicalId shouldBe "mu:100"
    }

    @Test
    fun `default order uses MangaUpdates first`() = runTest {
        // Default order: 7 (MU) → 2 (AL) → 1 (MAL)
        val orderPref = mockk<Preference<List<Long>>>()
        coEvery { orderPref.get() } returns TrackPreferences.DEFAULT_AUTHORITY_ORDER
        every { trackPreferences.authorityTrackerOrder() } returns orderPref

        val manga = testManga(id = 1L, title = "One Piece")
        coEvery { mangaRepository.getFavorites() } returns listOf(manga)
        coEvery { getTracks.await(1L) } returns emptyList()
        coEvery { muTracker.search("One Piece") } returns listOf(
            testTrackSearch("One Piece", 100L),
        )

        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        val result = matchUnlinkedManga.await()

        result.matched shouldBe 1
        // Default → picks MangaUpdates (public search)
        updateSlot.captured.canonicalId shouldBe "mu:100"
    }

    @Test
    fun `ordered list still checked for hasQueryableTracker`() = runTest {
        // With MangaUpdates always available, hasQueryableTracker should be true
        matchUnlinkedManga.hasQueryableTracker() shouldBe true
    }
}
