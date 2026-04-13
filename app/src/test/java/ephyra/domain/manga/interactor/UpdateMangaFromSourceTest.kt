package ephyra.domain.manga.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.download.DownloadManager
import ephyra.data.cache.CoverCache
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.model.LockedField
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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
 * Unit tests for [UpdateManga.awaitUpdateFromSource] change detection.
 *
 * Verifies that no DB write occurs when remote values match local values,
 * reducing unnecessary writes and preventing visual flicker (e.g. cover reload).
 */
@Execution(ExecutionMode.CONCURRENT)
class UpdateMangaFromSourceTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var fetchInterval: FetchInterval
    private lateinit var coverCache: CoverCache
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var downloadManager: DownloadManager
    private lateinit var trackPreferences: TrackPreferences
    private lateinit var updateManga: UpdateManga

    private fun testManga(
        id: Long = 1L,
        title: String = "Test Manga",
        author: String? = null,
        artist: String? = null,
        description: String? = null,
        genre: List<String>? = null,
        status: Long = 0L,
        thumbnailUrl: String? = null,
        updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized: Boolean = true,
        favorite: Boolean = true,
        canonicalId: String? = null,
        lockedFields: Long = 0L,
    ) = Manga.create().copy(
        id = id,
        title = title,
        author = author,
        artist = artist,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        updateStrategy = updateStrategy,
        initialized = initialized,
        favorite = favorite,
        canonicalId = canonicalId,
        lockedFields = lockedFields,
    )

    private fun testSManga(
        title: String = "Test Manga",
        author: String? = null,
        artist: String? = null,
        description: String? = null,
        genre: String? = null,
        status: Int = 0,
        thumbnailUrl: String? = null,
        updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    ): SManga = SManga.create().apply {
        this.title = title
        this.author = author
        this.artist = artist
        this.description = description
        this.genre = genre
        this.status = status
        this.thumbnail_url = thumbnailUrl
        this.update_strategy = updateStrategy
    }

    @BeforeEach
    fun setup() {
        mangaRepository = mockk(relaxed = true)
        fetchInterval = mockk(relaxed = true)
        coverCache = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        libraryPreferences = mockk(relaxed = true)
        trackPreferences = mockk(relaxed = true)

        // Default: allow title updates for favorites
        val updateTitlesPref = mockk<Preference<Boolean>>(relaxed = true)
        coEvery { updateTitlesPref.get() } returns true
        every { libraryPreferences.updateMangaTitles() } returns updateTitlesPref

        // Default: no content source priority fields
        val csPriorityPref = mockk<Preference<Long>>(relaxed = true)
        coEvery { csPriorityPref.get() } returns 0L
        every { trackPreferences.contentSourcePriorityFields() } returns csPriorityPref

        coEvery { mangaRepository.update(any()) } returns true

        updateManga = UpdateManga(mangaRepository, fetchInterval, coverCache, libraryPreferences, downloadManager, trackPreferences)
    }

    @Test
    fun `skips DB write when all fields match existing values`() = runTest {
        val manga = testManga(
            title = "My Manga",
            author = "Author A",
            artist = "Artist B",
            description = "A great manga",
            genre = listOf("Action", "Comedy"),
            status = 1L,
            thumbnailUrl = "https://example.com/cover.jpg",
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
        )
        val remote = testSManga(
            title = "My Manga",
            author = "Author A",
            artist = "Artist B",
            description = "A great manga",
            genre = "Action, Comedy",
            status = 1,
            thumbnailUrl = "https://example.com/cover.jpg",
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        )

        val result = updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        result shouldBe true
        // No DB write should occur — all values are identical
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `skips DB write on manual fetch when all fields match`() = runTest {
        val manga = testManga(
            title = "My Manga",
            author = "Author A",
            artist = "Artist B",
            description = "A great manga",
            genre = listOf("Action"),
            status = 1L,
            thumbnailUrl = "https://example.com/cover.jpg",
            initialized = true,
        )
        val remote = testSManga(
            title = "My Manga",
            author = "Author A",
            artist = "Artist B",
            description = "A great manga",
            genre = "Action",
            status = 1,
            thumbnailUrl = "https://example.com/cover.jpg",
        )

        val result = updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = true,
        )

        result shouldBe true
        // Even on manual fetch, identical values should not trigger a write
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `writes only changed fields`() = runTest {
        val manga = testManga(
            title = "Old Title",
            author = "Same Author",
            artist = "Same Artist",
            description = "Same description",
            genre = listOf("Action"),
            status = 1L,
            thumbnailUrl = "https://example.com/cover.jpg",
            initialized = true,
        )
        val remote = testSManga(
            title = "New Title",
            author = "Same Author",
            artist = "Same Artist",
            description = "Same description",
            genre = "Action",
            status = 1,
            thumbnailUrl = "https://example.com/cover.jpg",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Only title should be set (changed)
        update.title shouldBe "New Title"
        // Unchanged fields should be null (not written)
        update.author shouldBe null
        update.artist shouldBe null
        update.description shouldBe null
        update.genre shouldBe null
        update.status shouldBe null
        update.thumbnailUrl shouldBe null
        update.coverLastModified shouldBe null
        update.updateStrategy shouldBe null
        update.initialized shouldBe null
    }

    @Test
    fun `does not invalidate cover cache when thumbnail URL unchanged`() = runTest {
        val manga = testManga(
            thumbnailUrl = "https://example.com/cover.jpg",
            initialized = true,
        )
        val remote = testSManga(
            thumbnailUrl = "https://example.com/cover.jpg",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = true,
        )

        // Cover cache should not be touched when URL is unchanged
        coVerify(exactly = 0) { coverCache.deleteFromCache(any<Manga>(), any()) }
    }

    @Test
    fun `invalidates cover cache when thumbnail URL changes`() = runTest {
        val manga = testManga(
            thumbnailUrl = "https://example.com/old-cover.jpg",
            initialized = true,
        )
        val remote = testSManga(
            thumbnailUrl = "https://example.com/new-cover.jpg",
        )

        every { coverCache.deleteFromCache(any<Manga>(), any()) } returns 1

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.thumbnailUrl shouldBe "https://example.com/new-cover.jpg"
        (update.coverLastModified != null) shouldBe true
    }

    @Test
    fun `respects locked fields`() = runTest {
        val manga = testManga(
            author = "Locked Author",
            description = "Locked Description",
            lockedFields = LockedField.AUTHOR or LockedField.DESCRIPTION,
            initialized = true,
        )
        val remote = testSManga(
            author = "New Author",
            description = "New Description",
            artist = "New Artist",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        // Locked fields should not be written
        update.author shouldBe null
        update.description shouldBe null
        // Unlocked changed field should be written
        update.artist shouldBe "New Artist"
    }

    @Test
    fun `sets initialized when manga is not yet initialized`() = runTest {
        val manga = testManga(
            initialized = false,
            author = "Same",
        )
        val remote = testSManga(
            author = "Same",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.initialized shouldBe true
        // Author is same, so not written
        update.author shouldBe null
    }

    @Test
    fun `skips initialized when already initialized`() = runTest {
        val manga = testManga(
            initialized = true,
            author = "New",
        )
        val remote = testSManga(
            author = "Changed",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.initialized shouldBe null
        update.author shouldBe "Changed"
    }

    @Test
    fun `detects updateStrategy change`() = runTest {
        val manga = testManga(
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
        )
        val remote = testSManga(
            updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE,
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        val updateSlot = slot<MangaUpdate>()
        coVerify { mangaRepository.update(capture(updateSlot)) }
        val update = updateSlot.captured
        update.updateStrategy shouldBe UpdateStrategy.ONLY_FETCH_ONCE
    }

    @Test
    fun `skips updateStrategy when unchanged`() = runTest {
        val manga = testManga(
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
            author = "Same",
        )
        val remote = testSManga(
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            author = "Same",
        )

        val result = updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        result shouldBe true
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `renames manga download folder when title changes`() = runTest {
        val manga = testManga(
            title = "Old Title",
            initialized = true,
        )
        val remote = testSManga(
            title = "New Title",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        coVerify { downloadManager.renameManga(manga, "New Title") }
    }

    @Test
    fun `does not rename manga download folder when title unchanged`() = runTest {
        val manga = testManga(
            title = "Same Title",
            initialized = true,
        )
        val remote = testSManga(
            title = "Same Title",
        )

        updateManga.awaitUpdateFromSource(
            manga,
            remote,
            manualFetch = false,
        )

        coVerify(exactly = 0) { downloadManager.renameManga(any(), any()) }
    }
}
