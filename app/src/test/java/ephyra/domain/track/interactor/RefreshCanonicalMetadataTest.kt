package ephyra.domain.track.interactor

import ephyra.domain.manga.service.CoverCache
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.service.TrackPreferences
import io.kotest.matchers.shouldBe
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
 * Unit tests for [RefreshCanonicalMetadata].
 */
@Execution(ExecutionMode.CONCURRENT)
class RefreshCanonicalMetadataTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var trackerManager: TrackerManager
    private lateinit var trackPreferences: TrackPreferences
    private lateinit var coverCache: CoverCache
    private lateinit var refreshCanonicalMetadata: RefreshCanonicalMetadata
    private lateinit var muTracker: Tracker

    private fun testManga(
        id: Long = 1L,
        title: String = "Test Manga",
        canonicalId: String? = null,
        description: String? = null,
        author: String? = null,
        artist: String? = null,
        thumbnailUrl: String? = null,
        status: Long = 0L,
        alternativeTitles: List<String> = emptyList(),
        genre: List<String>? = null,
    ) = Manga.create().copy(
        id = id,
        title = title,
        canonicalId = canonicalId,
        description = description,
        author = author,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        status = status,
        alternativeTitles = alternativeTitles,
        genre = genre,
        favorite = true,
    )

    private fun testTrackSearch(
        title: String,
        remoteId: Long,
        summary: String = "",
        authors: List<String> = emptyList(),
        artists: List<String> = emptyList(),
        coverUrl: String = "",
        alternativeTitles: List<String> = emptyList(),
        publishingStatus: String = "",
        genres: List<String> = emptyList(),
    ) = TrackSearch(
        remote_id = remoteId,
        title = title,
        summary = summary,
        authors = authors,
        artists = artists,
        cover_url = coverUrl,
        alternative_titles = alternativeTitles,
        publishing_status = publishingStatus,
        genres = genres,
    )

    @BeforeEach
    fun setup() {
        mangaRepository = mockk(relaxed = true)
        trackerManager = mockk(relaxed = true)
        trackPreferences = mockk(relaxed = true)
        coverCache = mockk(relaxed = true)
        muTracker = mockk(relaxed = true)

        // Default: no content source priority fields (all fields prefer authority)
        val contentSourcePriorityPref = mockk<ephyra.core.common.preference.Preference<Long>>(relaxed = true)
        coEvery { contentSourcePriorityPref.get() } returns 0L
        every { trackPreferences.contentSourcePriorityFields() } returns contentSourcePriorityPref

        // MangaUpdates tracker (ID 7)
        every { muTracker.id } returns 7L
        coEvery { muTracker.isLoggedIn() } returns false
        every { trackerManager.get(7L) } returns muTracker

        refreshCanonicalMetadata =
            RefreshCanonicalMetadata(mangaRepository, trackerManager, trackPreferences, coverCache)
    }

    @Test
    fun `returns false when manga has no canonical ID`() = runTest {
        val manga = testManga(canonicalId = null)
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false
    }

    @Test
    fun `returns false for invalid canonical ID format`() = runTest {
        val manga = testManga(canonicalId = "invalid")
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false
    }

    @Test
    fun `returns false for unknown canonical prefix`() = runTest {
        val manga = testManga(canonicalId = "unknown:123")
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false
    }

    @Test
    fun `returns false when tracker not available`() = runTest {
        every { trackerManager.get(7L) } returns null
        val manga = testManga(canonicalId = "mu:12345")
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false
    }

    @Test
    fun `returns false when search returns no matching result`() = runTest {
        val manga = testManga(title = "Test Manga", canonicalId = "mu:12345")
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch("Other Manga", remoteId = 99999L),
        )
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false
    }

    @Test
    fun `overwrites metadata with fresh authority values by default`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Old description",
            author = "Old Author",
        )
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New and improved description",
                authors = listOf("New Author"),
                artists = listOf("New Artist"),
                coverUrl = "https://example.com/new-cover.jpg",
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Authority source overwrites existing fields (it's the source of truth)
        update.description shouldBe "New and improved description"
        update.author shouldBe "New Author"
        update.artist shouldBe "New Artist"
        update.thumbnailUrl shouldBe "https://example.com/new-cover.jpg"
        update.status shouldBe 2L // COMPLETED
    }

    @Test
    fun `skips write when authority values match existing (change detection)`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Same description",
            author = "Same Author",
            artist = "Same Artist",
            thumbnailUrl = "https://example.com/cover.jpg",
            status = 1L,
        )
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Same description",
                authors = listOf("Same Author"),
                artists = listOf("Same Artist"),
                coverUrl = "https://example.com/cover.jpg",
                publishingStatus = "Ongoing",
            ),
        )

        // Change detection: identical values → no DB write → returns false
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false
    }

    @Test
    fun `updates status from authority even when already set`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Same description",
            author = "Same Author",
            status = 1L, // ONGOING — authority says Completed, should overwrite
        )
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Same description",
                authors = listOf("Same Author"),
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        updateSlot.captured.status shouldBe 2L // COMPLETED — authority overwrite
    }

    @Test
    fun `fillOnly mode does not overwrite existing fields`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Old description",
            author = "Old Author",
            status = 1L, // ONGOING
        )
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                authors = listOf("New Author"),
                artists = listOf("New Artist"),
                coverUrl = "https://example.com/new-cover.jpg",
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga, fillOnly = true)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Existing fields NOT overwritten in fill-only mode
        update.description shouldBe null
        update.author shouldBe null
        update.status shouldBe null
        // Missing fields ARE filled
        update.artist shouldBe "New Artist"
        update.thumbnailUrl shouldBe "https://example.com/new-cover.jpg"
    }

    @Test
    fun `fillOnly mode returns false when all fields populated`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Same description",
            author = "Same Author",
            artist = "Same Artist",
            thumbnailUrl = "https://example.com/cover.jpg",
            status = 1L,
        )
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                authors = listOf("New Author"),
                artists = listOf("New Artist"),
                coverUrl = "https://example.com/new-cover.jpg",
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga, fillOnly = true)
        // All fields populated, nothing to fill
        result shouldBe false
    }

    @Test
    fun `merges alternative titles from tracker result`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            alternativeTitles = listOf("Alt 1"),
            description = "Same",
        )
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Same",
                alternativeTitles = listOf("Alt 1", "Alt 2", "Alt 3"),
            ),
        )

        refreshCanonicalMetadata.await(manga)

        // Should have called update for alt titles merge
        coVerify(atLeast = 1) { mangaRepository.update(any<MangaUpdate>()) }
    }

    @Test
    fun `parseCanonicalId parses valid IDs`() {
        RefreshCanonicalMetadata.parseCanonicalId("mu:12345") shouldBe ("mu" to 12345L)
        RefreshCanonicalMetadata.parseCanonicalId("al:21") shouldBe ("al" to 21L)
        RefreshCanonicalMetadata.parseCanonicalId("mal:100") shouldBe ("mal" to 100L)
    }

    @Test
    fun `parseCanonicalId returns null for invalid IDs`() {
        RefreshCanonicalMetadata.parseCanonicalId("invalid") shouldBe null
        RefreshCanonicalMetadata.parseCanonicalId("mu:abc") shouldBe null
        RefreshCanonicalMetadata.parseCanonicalId("") shouldBe null
        RefreshCanonicalMetadata.parseCanonicalId(":123") shouldBe null
        RefreshCanonicalMetadata.parseCanonicalId("mu:0") shouldBe null
        RefreshCanonicalMetadata.parseCanonicalId("mu:-1") shouldBe null
    }

    @Test
    fun `uses direct ID lookup when tracker supports it`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Old description",
        )
        // Direct ID lookup returns the matching result
        coEvery { muTracker.search("id:12345") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        // Should have used direct lookup, not title search
        coVerify(exactly = 1) { muTracker.search("id:12345") }
        coVerify(exactly = 0) { muTracker.search("Test Manga") }
    }

    @Test
    fun `falls back to title search when direct ID lookup returns empty`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Old description",
        )
        // Direct ID lookup returns empty
        coEvery { muTracker.search("id:12345") } returns emptyList()
        // Title search returns the match
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                publishingStatus = "Ongoing",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        // Should have tried direct lookup first, then title search
        coVerify(exactly = 1) { muTracker.search("id:12345") }
        coVerify(exactly = 1) { muTracker.search("Test Manga") }
    }

    @Test
    fun `falls back to title search when direct ID lookup throws`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Old description",
        )
        // Direct ID lookup throws
        coEvery { muTracker.search("id:12345") } throws RuntimeException("Not supported")
        // Title search returns the match
        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                publishingStatus = "Ongoing",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        coVerify(exactly = 1) { muTracker.search("Test Manga") }
    }

    @Test
    fun `maps various publishing status strings`() = runTest {
        // Test that status changes are detected for different status strings
        val statuses = listOf(
            "ongoing" to 1L,
            "publishing" to 1L,
            "completed" to 2L,
            "finished" to 2L,
            "cancelled" to 5L,
            "hiatus" to 6L,
            "on hiatus" to 6L,
        )

        for ((statusStr, expectedCode) in statuses) {
            // Fresh mocks for each iteration to avoid capture conflicts
            val repo = mockk<MangaRepository>(relaxed = true)
            val tm = mockk<TrackerManager>(relaxed = true)
            val tracker = mockk<Tracker>(relaxed = true)
            every { tracker.id } returns 7L
            coEvery { tracker.isLoggedIn() } returns false
            every { tm.get(7L) } returns tracker

            val tp = mockk<TrackPreferences>(relaxed = true)
            val csPref = mockk<ephyra.core.common.preference.Preference<Long>>(relaxed = true)
            coEvery { csPref.get() } returns 0L
            every { tp.contentSourcePriorityFields() } returns csPref

            val cc = mockk<CoverCache>(relaxed = true)
            val refresh = RefreshCanonicalMetadata(repo, tm, tp, cc)

            val manga = testManga(
                title = "Test Manga",
                canonicalId = "mu:12345",
                status = 0L, // Unknown -> should detect change
            )
            coEvery { tracker.search("Test Manga") } returns listOf(
                testTrackSearch(
                    title = "Test Manga",
                    remoteId = 12345L,
                    publishingStatus = statusStr,
                ),
            )

            val result = refresh.await(manga)
            result shouldBe true

            val updateSlot = slot<MangaUpdate>()
            coVerify { repo.update(capture(updateSlot)) }
            updateSlot.captured.status shouldBe expectedCode
        }
    }

    // --- Per-field locking (Jellyfin-style) ---

    @Test
    fun `locked description is not overwritten even in default overwrite mode`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "User-customized description",
        ).copy(lockedFields = ephyra.domain.manga.model.LockedField.DESCRIPTION)

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Authority description (should be ignored)",
                authors = listOf("New Author"),
                publishingStatus = "Ongoing",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Description locked — must NOT be overwritten
        update.description shouldBe null
        // Unlocked fields still get updated
        update.author shouldBe "New Author"
        update.status shouldBe 1L
    }

    @Test
    fun `locked cover is not overwritten`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            thumbnailUrl = "https://example.com/my-custom-cover.jpg",
        ).copy(lockedFields = ephyra.domain.manga.model.LockedField.COVER)

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                coverUrl = "https://example.com/authority-cover.jpg",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Cover locked — must NOT be overwritten
        update.thumbnailUrl shouldBe null
        // Unlocked fields still get updated
        update.description shouldBe "New description"
    }

    @Test
    fun `multiple locked fields are all respected`() = runTest {
        val locked = ephyra.domain.manga.model.LockedField.DESCRIPTION or
            ephyra.domain.manga.model.LockedField.AUTHOR or
            ephyra.domain.manga.model.LockedField.STATUS
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Custom desc",
            author = "Custom Author",
            status = 1L,
        ).copy(lockedFields = locked)

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Authority desc",
                authors = listOf("Authority Author"),
                artists = listOf("Authority Artist"),
                coverUrl = "https://example.com/cover.jpg",
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Locked fields — NOT overwritten
        update.description shouldBe null
        update.author shouldBe null
        update.status shouldBe null
        // Unlocked fields — overwritten
        update.artist shouldBe "Authority Artist"
        update.thumbnailUrl shouldBe "https://example.com/cover.jpg"
    }

    @Test
    fun `returns false when all fields locked and all populated`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            description = "Locked desc",
            author = "Locked author",
            artist = "Locked artist",
            thumbnailUrl = "https://example.com/locked.jpg",
            status = 1L,
        ).copy(lockedFields = ephyra.domain.manga.model.LockedField.ALL)

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New desc",
                authors = listOf("New author"),
                artists = listOf("New artist"),
                coverUrl = "https://example.com/new.jpg",
                publishingStatus = "Completed",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        // All fields locked — no changes to make
        result shouldBe false
    }

    @Test
    fun `genres from authority are merged into manga`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            genre = listOf("Action"),
        )

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                genres = listOf("Action", "Adventure", "Fantasy"),
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.genre shouldBe listOf("Action", "Adventure", "Fantasy")
    }

    @Test
    fun `locked genres are not overwritten`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            genre = listOf("Custom Genre"),
        ).copy(lockedFields = ephyra.domain.manga.model.LockedField.GENRE)

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Some summary",
                genres = listOf("Action", "Adventure"),
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Genre is locked — should not appear in update
        update.genre shouldBe null
        // Description is not locked — should be updated
        update.description shouldBe "Some summary"
    }

    @Test
    fun `fillOnly mode does not overwrite existing genres`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            genre = listOf("Existing Genre"),
        )

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "Summary",
                genres = listOf("Action", "Adventure"),
            ),
        )

        val result = refreshCanonicalMetadata.await(manga, fillOnly = true)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Genre already has values — fillOnly skips it
        update.genre shouldBe null
    }

    @Test
    fun `fillOnly mode fills empty genres from authority`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            genre = null,
        )

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                genres = listOf("Action", "Adventure"),
            ),
        )

        val result = refreshCanonicalMetadata.await(manga, fillOnly = true)
        result shouldBe true

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.genre shouldBe listOf("Action", "Adventure")
    }

    @Test
    fun `overwrite mode updates title from authority when not locked`() = runTest {
        val manga = testManga(
            title = "Old Title",
            canonicalId = "mu:12345",
        )

        coEvery { muTracker.search("Old Title") } returns listOf(
            testTrackSearch(
                title = "New Authority Title",
                remoteId = 12345L,
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updates = mutableListOf<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updates)) }
        val metadataUpdate = updates.first()
        metadataUpdate.title shouldBe "New Authority Title"
    }

    @Test
    fun `locked TITLE field prevents title overwrite`() = runTest {
        val manga = testManga(
            title = "My Custom Title",
            canonicalId = "mu:12345",
        ).copy(lockedFields = ephyra.domain.manga.model.LockedField.TITLE)

        coEvery { muTracker.search("My Custom Title") } returns listOf(
            testTrackSearch(
                title = "Authority Title",
                remoteId = 12345L,
                summary = "New description",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        val updates = mutableListOf<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updates)) }
        val metadataUpdate = updates.first()
        metadataUpdate.title shouldBe null // title not updated because locked
        metadataUpdate.description shouldBe "New description" // other fields still updated
    }

    @Test
    fun `fillOnly mode does not overwrite existing title`() = runTest {
        val manga = testManga(
            title = "Existing Title",
            canonicalId = "mu:12345",
            description = null,
        )

        coEvery { muTracker.search("Existing Title") } returns listOf(
            testTrackSearch(
                title = "Authority Title",
                remoteId = 12345L,
                summary = "A description",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga, fillOnly = true)
        result shouldBe true

        val updates = mutableListOf<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updates)) }
        val metadataUpdate = updates.first()
        metadataUpdate.title shouldBe null // title not updated because already exists
        metadataUpdate.description shouldBe "A description"
    }

    @Test
    fun `skips DB write when authority values match existing values`() = runTest {
        val manga = testManga(
            title = "Same Title",
            canonicalId = "mu:12345",
            description = "Same description",
            author = "Author A",
            artist = "Artist B",
        )

        coEvery { muTracker.search("Same Title") } returns listOf(
            testTrackSearch(
                title = "Same Title",
                remoteId = 12345L,
                summary = "Same description",
                authors = listOf("Author A"),
                artists = listOf("Artist B"),
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe false // No changes detected — values are identical
    }

    @Test
    fun `invalidates cover cache and sets coverLastModified when thumbnail URL changes`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            thumbnailUrl = "https://old.example.com/cover.jpg",
        )

        every { coverCache.deleteFromCache(any<Manga>(), any()) } returns 1

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                coverUrl = "https://new.example.com/cover.jpg",
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        // Cover cache should be cleared for the old cover
        coVerify { coverCache.deleteFromCache(any<Manga>(), any()) }

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.thumbnailUrl shouldBe "https://new.example.com/cover.jpg"
        // coverLastModified must be set to a positive timestamp so Coil invalidates its cache
        (update.coverLastModified != null && update.coverLastModified!! > 0) shouldBe true
    }

    @Test
    fun `does not touch cover cache when thumbnail URL is unchanged`() = runTest {
        val manga = testManga(
            title = "Test Manga",
            canonicalId = "mu:12345",
            thumbnailUrl = "https://example.com/cover.jpg",
        )

        coEvery { muTracker.search("Test Manga") } returns listOf(
            testTrackSearch(
                title = "Test Manga",
                remoteId = 12345L,
                summary = "New description",
                coverUrl = "https://example.com/cover.jpg", // same URL
            ),
        )

        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true

        // Cover cache must NOT be touched when the URL is the same
        coVerify(exactly = 0) { coverCache.deleteFromCache(any<Manga>(), any()) }

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.thumbnailUrl shouldBe null // no URL change → null in update
        update.coverLastModified shouldBe null
    }
}
