package tachiyomi.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * Unit tests for [GenerateAuthorityChapters] interactor.
 */
@Execution(ExecutionMode.CONCURRENT)
class GenerateAuthorityChaptersTest {

    private lateinit var chapterRepository: ChapterRepository
    private lateinit var generateAuthorityChapters: GenerateAuthorityChapters

    @BeforeEach
    fun setup() {
        chapterRepository = mockk(relaxed = true)
        generateAuthorityChapters = GenerateAuthorityChapters(chapterRepository)
    }

    @Test
    fun `generates correct number of chapters`() = runTest {
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns emptyList()
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 1).toLong()) }
        }

        val count = generateAuthorityChapters.await(mangaId = 1L, totalChapters = 10)
        count shouldBe 10

        val chaptersSlot = slot<List<Chapter>>()
        coVerify { chapterRepository.addAll(capture(chaptersSlot)) }

        val chapters = chaptersSlot.captured
        chapters.size shouldBe 10
        chapters.map { it.chapterNumber } shouldBe (1..10).map { it.toDouble() }
    }

    @Test
    fun `marks chapters as read up to lastChapterRead`() = runTest {
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns emptyList()
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 1).toLong()) }
        }

        generateAuthorityChapters.await(mangaId = 1L, totalChapters = 10, lastChapterRead = 5)

        val chaptersSlot = slot<List<Chapter>>()
        coVerify { chapterRepository.addAll(capture(chaptersSlot)) }

        val chapters = chaptersSlot.captured
        chapters.filter { it.read }.size shouldBe 5
        chapters.filter { it.read }.all { it.chapterNumber <= 5.0 } shouldBe true
        chapters.filter { !it.read }.all { it.chapterNumber > 5.0 } shouldBe true
    }

    @Test
    fun `returns zero for zero totalChapters`() = runTest {
        val count = generateAuthorityChapters.await(mangaId = 1L, totalChapters = 0)
        count shouldBe 0
        coVerify(exactly = 0) { chapterRepository.addAll(any()) }
    }

    @Test
    fun `returns zero for negative totalChapters`() = runTest {
        val count = generateAuthorityChapters.await(mangaId = 1L, totalChapters = -5)
        count shouldBe 0
        coVerify(exactly = 0) { chapterRepository.addAll(any()) }
    }

    @Test
    fun `skips existing chapters by number`() = runTest {
        val existingChapters = listOf(
            Chapter.create().copy(id = 100L, mangaId = 1L, chapterNumber = 1.0, name = "Chapter 1"),
            Chapter.create().copy(id = 101L, mangaId = 1L, chapterNumber = 2.0, name = "Chapter 2"),
        )
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns existingChapters
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 200).toLong()) }
        }

        val count = generateAuthorityChapters.await(mangaId = 1L, totalChapters = 5)
        count shouldBe 3 // Only chapters 3, 4, 5 should be new

        val chaptersSlot = slot<List<Chapter>>()
        coVerify { chapterRepository.addAll(capture(chaptersSlot)) }

        val newChapters = chaptersSlot.captured
        newChapters.map { it.chapterNumber } shouldBe listOf(3.0, 4.0, 5.0)
    }

    @Test
    fun `marks existing unread chapters as read when within read range`() = runTest {
        val existingChapters = listOf(
            Chapter.create().copy(id = 100L, mangaId = 1L, chapterNumber = 1.0, read = false),
            Chapter.create().copy(id = 101L, mangaId = 1L, chapterNumber = 2.0, read = true),
            Chapter.create().copy(id = 102L, mangaId = 1L, chapterNumber = 3.0, read = false),
        )
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns existingChapters
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 200).toLong()) }
        }

        generateAuthorityChapters.await(mangaId = 1L, totalChapters = 5, lastChapterRead = 3)

        // Should update chapters 1 and 3 (unread within range), not chapter 2 (already read)
        val updatesSlot = slot<List<ChapterUpdate>>()
        coVerify { chapterRepository.updateAll(capture(updatesSlot)) }

        val updates = updatesSlot.captured
        updates.size shouldBe 2
        updates.map { it.id } shouldBe listOf(100L, 102L)
        updates.all { it.read == true } shouldBe true
    }

    @Test
    fun `chapters have correct names`() = runTest {
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns emptyList()
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 1).toLong()) }
        }

        generateAuthorityChapters.await(mangaId = 1L, totalChapters = 3)

        val chaptersSlot = slot<List<Chapter>>()
        coVerify { chapterRepository.addAll(capture(chaptersSlot)) }

        val chapters = chaptersSlot.captured
        chapters.map { it.name } shouldBe listOf("Chapter 1", "Chapter 2", "Chapter 3")
    }

    @Test
    fun `chapters use authority URL scheme`() = runTest {
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns emptyList()
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 1).toLong()) }
        }

        generateAuthorityChapters.await(mangaId = 1L, totalChapters = 2)

        val chaptersSlot = slot<List<Chapter>>()
        coVerify { chapterRepository.addAll(capture(chaptersSlot)) }

        val chapters = chaptersSlot.captured
        chapters[0].url shouldBe "authority://chapter/1"
        chapters[1].url shouldBe "authority://chapter/2"
    }

    @Test
    fun `returns zero when all chapters already exist`() = runTest {
        val existingChapters = (1..5).map {
            Chapter.create().copy(id = it.toLong(), mangaId = 1L, chapterNumber = it.toDouble())
        }
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns existingChapters

        val count = generateAuthorityChapters.await(mangaId = 1L, totalChapters = 5)
        count shouldBe 0
        coVerify(exactly = 0) { chapterRepository.addAll(any()) }
    }

    @Test
    fun `handles lastChapterRead of zero`() = runTest {
        coEvery { chapterRepository.getChapterByMangaId(1L) } returns emptyList()
        coEvery { chapterRepository.addAll(any()) } answers {
            firstArg<List<Chapter>>().mapIndexed { i, ch -> ch.copy(id = (i + 1).toLong()) }
        }

        generateAuthorityChapters.await(mangaId = 1L, totalChapters = 5, lastChapterRead = 0)

        val chaptersSlot = slot<List<Chapter>>()
        coVerify { chapterRepository.addAll(capture(chaptersSlot)) }

        val chapters = chaptersSlot.captured
        chapters.all { !it.read } shouldBe true
    }
}
