package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Unit tests for [RefreshCanonicalMetadata].
 */
@Execution(ExecutionMode.CONCURRENT)
class RefreshCanonicalMetadataTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var trackerManager: TrackerManager
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
    ): TrackSearch {
        val ts = TrackSearch()
        ts.title = title
        ts.remote_id = remoteId
        ts.summary = summary
        ts.authors = authors
        ts.artists = artists
        ts.cover_url = coverUrl
        ts.alternative_titles = alternativeTitles
        ts.publishing_status = publishingStatus
        return ts
    }

    @BeforeEach
    fun setup() {
        mangaRepository = mockk(relaxed = true)
        trackerManager = mockk(relaxed = true)
        muTracker = mockk(relaxed = true)

        // MangaUpdates tracker (ID 7)
        every { muTracker.id } returns 7L
        every { muTracker.isLoggedIn } returns false
        every { trackerManager.get(7L) } returns muTracker

        refreshCanonicalMetadata = RefreshCanonicalMetadata(mangaRepository, trackerManager)
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
    fun `returns true even when values are same since authority always writes`() = runTest {
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

        // Authority always overwrites, so even identical data counts as an update
        val result = refreshCanonicalMetadata.await(manga)
        result shouldBe true
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
            every { tracker.isLoggedIn } returns false
            every { tm.get(7L) } returns tracker

            val refresh = RefreshCanonicalMetadata(repo, tm)

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
}
