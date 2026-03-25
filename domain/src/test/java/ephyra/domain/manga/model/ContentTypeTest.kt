package ephyra.domain.manga.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentTypeTest {

    // --- fromPublishingType ---

    @Test
    fun `fromPublishingType maps manga variants to MANGA`() {
        listOf("Manga", "Manhwa", "Manhua", "Webtoon", "Comic", "Oneshot", "Doujinshi", "OEL")
            .forEach { type ->
                assertEquals(ContentType.MANGA, ContentType.fromPublishingType(type), type)
            }
    }

    @Test
    fun `fromPublishingType maps novel variants to NOVEL`() {
        listOf("Novel", "Light Novel", "light_novel", "Web Novel", "web_novel")
            .forEach { type ->
                assertEquals(ContentType.NOVEL, ContentType.fromPublishingType(type), type)
            }
    }

    @Test
    fun `fromPublishingType maps book variants to BOOK`() {
        listOf("Artbook", "Art Book", "art_book")
            .forEach { type ->
                assertEquals(ContentType.BOOK, ContentType.fromPublishingType(type), type)
            }
    }

    @Test
    fun `fromPublishingType returns UNKNOWN for unrecognized types`() {
        listOf("Unknown", "Other", "", "Random")
            .forEach { type ->
                assertEquals(ContentType.UNKNOWN, ContentType.fromPublishingType(type), type)
            }
    }

    @Test
    fun `fromPublishingType is case-insensitive`() {
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("MANGA"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("manga"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Manga"))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("LIGHT NOVEL"))
    }

    @Test
    fun `fromPublishingType trims whitespace`() {
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("  manga  "))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType(" novel "))
    }

    // --- fromValue ---

    @Test
    fun `fromValue resolves known values`() {
        assertEquals(ContentType.UNKNOWN, ContentType.fromValue(0))
        assertEquals(ContentType.MANGA, ContentType.fromValue(1))
        assertEquals(ContentType.NOVEL, ContentType.fromValue(2))
        assertEquals(ContentType.BOOK, ContentType.fromValue(3))
    }

    @Test
    fun `fromValue defaults to UNKNOWN for invalid values`() {
        assertEquals(ContentType.UNKNOWN, ContentType.fromValue(99))
        assertEquals(ContentType.UNKNOWN, ContentType.fromValue(-1))
    }

    // --- isLikelyWebtoon ---

    @Test
    fun `isLikelyWebtoon returns true for webtoon genre`() {
        assertTrue(ContentType.isLikelyWebtoon(listOf("Action", "Webtoon", "Romance")))
    }

    @Test
    fun `isLikelyWebtoon returns true for long strip genre`() {
        assertTrue(ContentType.isLikelyWebtoon(listOf("Action", "Long Strip")))
        assertTrue(ContentType.isLikelyWebtoon(listOf("Long-Strip")))
        assertTrue(ContentType.isLikelyWebtoon(listOf("Longstrip")))
    }

    @Test
    fun `isLikelyWebtoon returns false for full color genre alone`() {
        assertFalse(ContentType.isLikelyWebtoon(listOf("Full Color", "Fantasy")))
        assertFalse(ContentType.isLikelyWebtoon(listOf("Full Colour")))
    }

    @Test
    fun `isLikelyWebtoon returns true for manhwa genre`() {
        assertTrue(ContentType.isLikelyWebtoon(listOf("Manhwa")))
    }

    @Test
    fun `isLikelyWebtoon returns true for manhua genre`() {
        assertTrue(ContentType.isLikelyWebtoon(listOf("Manhua")))
    }

    @Test
    fun `isLikelyWebtoon is case-insensitive`() {
        assertTrue(ContentType.isLikelyWebtoon(listOf("WEBTOON")))
        assertTrue(ContentType.isLikelyWebtoon(listOf("long strip")))
        assertTrue(ContentType.isLikelyWebtoon(listOf("MANHWA")))
    }

    @Test
    fun `isLikelyWebtoon returns false for standard manga genres`() {
        assertFalse(ContentType.isLikelyWebtoon(listOf("Action", "Adventure", "Fantasy")))
    }

    @Test
    fun `isLikelyWebtoon returns false for null or empty`() {
        assertFalse(ContentType.isLikelyWebtoon(null))
        assertFalse(ContentType.isLikelyWebtoon(emptyList()))
    }

    // --- isWebtoonPublishingType ---

    @Test
    fun `isWebtoonPublishingType returns true for webtoon types`() {
        assertTrue(ContentType.isWebtoonPublishingType("Webtoon"))
        assertTrue(ContentType.isWebtoonPublishingType("Manhwa"))
        assertTrue(ContentType.isWebtoonPublishingType("Manhua"))
    }

    @Test
    fun `isWebtoonPublishingType returns false for manga`() {
        assertFalse(ContentType.isWebtoonPublishingType("Manga"))
    }

    @Test
    fun `isWebtoonPublishingType is case-insensitive`() {
        assertTrue(ContentType.isWebtoonPublishingType("WEBTOON"))
        assertTrue(ContentType.isWebtoonPublishingType("webtoon"))
    }
}
