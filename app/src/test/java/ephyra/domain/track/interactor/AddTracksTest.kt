package ephyra.domain.track.interactor

import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Unit tests for [AddTracks] canonical ID and alternative titles pipeline.
 */
@Execution(ExecutionMode.CONCURRENT)
class AddTracksTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var addTracks: AddTracks

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
    )

    @BeforeEach
    fun setup() {
        mangaRepository = mockk(relaxed = true)
        addTracks = AddTracks(
            insertTrack = mockk<InsertTrack>(),
            syncChapterProgressWithTrack = mockk<SyncChapterProgressWithTrack>(),
            getChaptersByMangaId = mockk<GetChaptersByMangaId>(),
            getHistory = mockk(relaxed = true),
            trackerManager = mockk(relaxed = true),
            mangaRepository = mangaRepository,
        )
    }

    // ========== setCanonicalIdIfAbsent tests ==========

    @Test
    fun `setCanonicalIdIfAbsent sets MAL prefix for MAL tracker`() = runTest {
        val manga = testManga(id = 1L, canonicalId = null)
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.setCanonicalIdIfAbsent(1L, 1L, 21L) // MAL tracker ID = 1

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.canonicalId shouldBe "mal:21"
    }

    @Test
    fun `setCanonicalIdIfAbsent sets AL prefix for AniList tracker`() = runTest {
        val manga = testManga(id = 1L, canonicalId = null)
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.setCanonicalIdIfAbsent(1L, 2L, 100L) // AniList tracker ID = 2

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.canonicalId shouldBe "al:100"
    }

    @Test
    fun `setCanonicalIdIfAbsent sets MU prefix for MangaUpdates tracker`() = runTest {
        val manga = testManga(id = 1L, canonicalId = null)
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.setCanonicalIdIfAbsent(1L, 7L, 42L) // MangaUpdates tracker ID = 7

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.canonicalId shouldBe "mu:42"
    }

    @Test
    fun `setCanonicalIdIfAbsent does not overwrite existing canonical ID`() = runTest {
        val manga = testManga(id = 1L, canonicalId = "al:50")
        coEvery { mangaRepository.getMangaById(1L) } returns manga

        addTracks.setCanonicalIdIfAbsent(1L, 1L, 21L) // Try MAL after AniList already set

        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `setCanonicalIdIfAbsent skips remoteId zero`() = runTest {
        addTracks.setCanonicalIdIfAbsent(1L, 1L, 0L)

        coVerify(exactly = 0) { mangaRepository.getMangaById(any()) }
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `setCanonicalIdIfAbsent skips negative remoteId`() = runTest {
        addTracks.setCanonicalIdIfAbsent(1L, 2L, -1L)

        coVerify(exactly = 0) { mangaRepository.getMangaById(any()) }
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `setCanonicalIdIfAbsent skips unknown tracker`() = runTest {
        addTracks.setCanonicalIdIfAbsent(1L, 999L, 21L) // Unknown tracker ID

        coVerify(exactly = 0) { mangaRepository.getMangaById(any()) }
        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    // ========== mergeAlternativeTitles tests ==========

    @Test
    fun `mergeAlternativeTitles adds new titles`() = runTest {
        val manga = testManga(title = "Primary Title", alternativeTitles = emptyList())
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.mergeAlternativeTitles(1L, listOf("Alt Title A", "Alt Title B"))

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.alternativeTitles shouldNotBe null
        updateSlot.captured.alternativeTitles!!.size shouldBe 2
        updateSlot.captured.alternativeTitles!! shouldBe listOf("Alt Title A", "Alt Title B")
    }

    @Test
    fun `mergeAlternativeTitles excludes primary title`() = runTest {
        val manga = testManga(title = "My Manga", alternativeTitles = emptyList())
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.mergeAlternativeTitles(1L, listOf("My Manga", "Alt Title"))

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.alternativeTitles!! shouldBe listOf("Alt Title")
    }

    @Test
    fun `mergeAlternativeTitles excludes primary title case-insensitively`() = runTest {
        val manga = testManga(title = "My Manga", alternativeTitles = emptyList())
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.mergeAlternativeTitles(1L, listOf("my manga", "MY MANGA", "Alt"))

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.alternativeTitles!! shouldBe listOf("Alt")
    }

    @Test
    fun `mergeAlternativeTitles deduplicates case-insensitively`() = runTest {
        val manga = testManga(title = "Primary", alternativeTitles = emptyList())
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.mergeAlternativeTitles(1L, listOf("Title A", "title a", "TITLE A"))

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.alternativeTitles!!.size shouldBe 1
        updateSlot.captured.alternativeTitles!![0] shouldBe "Title A" // Keeps first casing
    }

    @Test
    fun `mergeAlternativeTitles filters blank titles`() = runTest {
        val manga = testManga(title = "Primary", alternativeTitles = emptyList())
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.mergeAlternativeTitles(1L, listOf("", "  ", "Valid Title"))

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.alternativeTitles!! shouldBe listOf("Valid Title")
    }

    @Test
    fun `mergeAlternativeTitles preserves existing titles and adds new`() = runTest {
        val manga = testManga(title = "Primary", alternativeTitles = listOf("Existing"))
        coEvery { mangaRepository.getMangaById(1L) } returns manga
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        addTracks.mergeAlternativeTitles(1L, listOf("New Title"))

        updateSlot.isCaptured shouldBe true
        updateSlot.captured.alternativeTitles!! shouldBe listOf("Existing", "New Title")
    }

    @Test
    fun `mergeAlternativeTitles does not update when no new titles`() = runTest {
        val manga = testManga(title = "Primary", alternativeTitles = listOf("Existing"))
        coEvery { mangaRepository.getMangaById(1L) } returns manga

        addTracks.mergeAlternativeTitles(1L, listOf("Existing", "Primary"))

        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }

    @Test
    fun `mergeAlternativeTitles does not duplicate existing titles case-insensitively`() = runTest {
        val manga = testManga(title = "Primary", alternativeTitles = listOf("Existing Title"))
        coEvery { mangaRepository.getMangaById(1L) } returns manga

        addTracks.mergeAlternativeTitles(1L, listOf("existing title", "EXISTING TITLE"))

        coVerify(exactly = 0) { mangaRepository.update(any()) }
    }
}
