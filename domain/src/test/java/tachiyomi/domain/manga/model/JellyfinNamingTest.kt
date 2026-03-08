package tachiyomi.domain.manga.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JellyfinNamingTest {

    // --- sanitize ---

    @Test
    fun `sanitize removes invalid characters`() {
        assertEquals("Title_Name", JellyfinNaming.sanitize("Title:Name"))
        assertEquals("A_B_C", JellyfinNaming.sanitize("A\\B/C"))
        assertEquals("Test_File", JellyfinNaming.sanitize("Test*File"))
    }

    @Test
    fun `sanitize collapses multiple underscores`() {
        assertEquals("A_B", JellyfinNaming.sanitize("A:::B"))
    }

    @Test
    fun `sanitize trims whitespace and trailing dots`() {
        assertEquals("Title", JellyfinNaming.sanitize("  Title.  "))
    }

    @Test
    fun `sanitize returns Unknown for blank input`() {
        assertEquals("Unknown", JellyfinNaming.sanitize("   "))
    }

    // --- seriesDirName ---

    @Test
    fun `seriesDirName basic title`() {
        assertEquals("One Piece", JellyfinNaming.seriesDirName("One Piece"))
    }

    @Test
    fun `seriesDirName sanitizes special characters`() {
        assertEquals("Re_Zero", JellyfinNaming.seriesDirName("Re:Zero"))
    }

    // --- chapterFileName ---

    @Test
    fun `chapterFileName with chapter only`() {
        assertEquals(
            "One Piece Ch. 001",
            JellyfinNaming.chapterFileName("One Piece", chapterNumber = 1.0),
        )
    }

    @Test
    fun `chapterFileName with volume and chapter`() {
        assertEquals(
            "Naruto Vol. 01 Ch. 001",
            JellyfinNaming.chapterFileName("Naruto", chapterNumber = 1.0, volumeNumber = 1),
        )
    }

    @Test
    fun `chapterFileName pads chapter numbers`() {
        assertEquals(
            "Title Ch. 042",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 42.0),
        )
    }

    @Test
    fun `chapterFileName handles decimal chapters`() {
        assertEquals(
            "Title Ch. 010.5",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 10.5),
        )
    }

    @Test
    fun `chapterFileName handles multi-digit decimal chapters`() {
        assertEquals(
            "Title Ch. 010.25",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 10.25),
        )
    }

    @Test
    fun `chapterFileName volume only`() {
        assertEquals(
            "Title Vol. 03",
            JellyfinNaming.chapterFileName("Title", volumeNumber = 3),
        )
    }

    @Test
    fun `chapterFileName with chapter title`() {
        assertEquals(
            "Title Ch. 001 - The Beginning",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 1.0, chapterTitle = "The Beginning"),
        )
    }

    @Test
    fun `chapterFileName skips redundant chapter title`() {
        assertEquals(
            "Title Ch. 001",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 1.0, chapterTitle = "Chapter 1"),
        )
    }

    @Test
    fun `chapterFileName skips redundant chapter title case insensitive`() {
        assertEquals(
            "Title Ch. 001",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 1.0, chapterTitle = "CHAPTER 1"),
        )
    }

    @Test
    fun `chapterFileName no chapter or volume`() {
        assertEquals(
            "Title",
            JellyfinNaming.chapterFileName("Title"),
        )
    }

    @Test
    fun `chapterFileName large chapter number`() {
        assertEquals(
            "Title Ch. 1234",
            JellyfinNaming.chapterFileName("Title", chapterNumber = 1234.0),
        )
    }

    // --- libraryRootName ---

    @Test
    fun `libraryRootName for each content type`() {
        assertEquals("Manga", JellyfinNaming.libraryRootName(ContentType.MANGA))
        assertEquals("Novels", JellyfinNaming.libraryRootName(ContentType.NOVEL))
        assertEquals("Books", JellyfinNaming.libraryRootName(ContentType.BOOK))
        assertEquals("Manga", JellyfinNaming.libraryRootName(ContentType.UNKNOWN))
    }

    // --- buildPath ---

    @Test
    fun `buildPath returns correct segments`() {
        val path = JellyfinNaming.buildPath(
            seriesTitle = "One Piece",
            contentType = ContentType.MANGA,
            chapterNumber = 1.0,
        )
        assertEquals(listOf("Manga", "One Piece", "One Piece Ch. 001.cbz"), path)
    }

    @Test
    fun `buildPath with volume`() {
        val path = JellyfinNaming.buildPath(
            seriesTitle = "Naruto",
            contentType = ContentType.MANGA,
            chapterNumber = 5.0,
            volumeNumber = 1,
        )
        assertEquals(listOf("Manga", "Naruto", "Naruto Vol. 01 Ch. 005.cbz"), path)
    }

    @Test
    fun `buildPath for novel`() {
        val path = JellyfinNaming.buildPath(
            seriesTitle = "Sword Art Online",
            contentType = ContentType.NOVEL,
            volumeNumber = 3,
        )
        assertEquals(listOf("Novels", "Sword Art Online", "Sword Art Online Vol. 03.cbz"), path)
    }

    // --- parseChapterFilename ---

    @Test
    fun `parseChapterFilename with volume and chapter`() {
        val result = JellyfinNaming.parseChapterFilename("One Piece Vol. 01 Ch. 001.cbz")
        assertNotNull(result)
        assertEquals("One Piece", result!!.seriesTitle)
        assertEquals(1, result.volumeNumber)
        assertEquals(1.0, result.chapterNumber)
        assertNull(result.chapterTitle)
    }

    @Test
    fun `parseChapterFilename with chapter only`() {
        val result = JellyfinNaming.parseChapterFilename("Naruto Ch. 042.cbz")
        assertNotNull(result)
        assertEquals("Naruto", result!!.seriesTitle)
        assertNull(result.volumeNumber)
        assertEquals(42.0, result.chapterNumber)
    }

    @Test
    fun `parseChapterFilename with volume only`() {
        val result = JellyfinNaming.parseChapterFilename("Title Vol. 05.cbz")
        assertNotNull(result)
        assertEquals("Title", result!!.seriesTitle)
        assertEquals(5, result.volumeNumber)
        assertNull(result.chapterNumber)
    }

    @Test
    fun `parseChapterFilename with chapter title`() {
        val result = JellyfinNaming.parseChapterFilename("Title Ch. 001 - The Beginning.cbz")
        assertNotNull(result)
        assertEquals("Title", result!!.seriesTitle)
        assertEquals(1.0, result.chapterNumber)
        assertEquals("The Beginning", result.chapterTitle)
    }

    @Test
    fun `parseChapterFilename with decimal chapter`() {
        val result = JellyfinNaming.parseChapterFilename("Title Vol. 02 Ch. 10.5.cbz")
        assertNotNull(result)
        assertEquals("Title", result!!.seriesTitle)
        assertEquals(2, result.volumeNumber)
        assertEquals(10.5, result.chapterNumber)
    }

    @Test
    fun `parseChapterFilename returns null for unrecognized format`() {
        val result = JellyfinNaming.parseChapterFilename("random_file.cbz")
        assertNull(result)
    }

    @Test
    fun `parseChapterFilename without extension`() {
        val result = JellyfinNaming.parseChapterFilename("Title Ch. 005")
        assertNotNull(result)
        assertEquals("Title", result!!.seriesTitle)
        assertEquals(5.0, result.chapterNumber)
    }

    // --- round-trip ---

    @Test
    fun `chapterFileName output can be parsed back`() {
        val fileName = JellyfinNaming.chapterFileName("One Piece", chapterNumber = 42.0, volumeNumber = 5)
        val parsed = JellyfinNaming.parseChapterFilename("$fileName.cbz")
        assertNotNull(parsed)
        assertEquals("One Piece", parsed!!.seriesTitle)
        assertEquals(5, parsed.volumeNumber)
        assertEquals(42.0, parsed.chapterNumber)
    }

    @Test
    fun `chapterFileName with title can be parsed back`() {
        val fileName = JellyfinNaming.chapterFileName(
            "Naruto",
            chapterNumber = 1.0,
            chapterTitle = "Enter Naruto",
        )
        val parsed = JellyfinNaming.parseChapterFilename("$fileName.cbz")
        assertNotNull(parsed)
        assertEquals("Naruto", parsed!!.seriesTitle)
        assertEquals(1.0, parsed.chapterNumber)
        assertEquals("Enter Naruto", parsed.chapterTitle)
    }
}
