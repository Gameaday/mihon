package ephyra.domain.manga.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContentTypeTest {

    @Test
    fun `fromValue resolves known values`() {
        assertEquals(ContentType.UNKNOWN, ContentType.fromValue(0))
        assertEquals(ContentType.MANGA, ContentType.fromValue(1))
        assertEquals(ContentType.NOVEL, ContentType.fromValue(2))
        assertEquals(ContentType.BOOK, ContentType.fromValue(3))
    }

    @Test
    fun `fromValue returns UNKNOWN for invalid values`() {
        assertEquals(ContentType.UNKNOWN, ContentType.fromValue(-1))
        assertEquals(ContentType.UNKNOWN, ContentType.fromValue(999))
    }

    @Test
    fun `fromPublishingType maps manga variants`() {
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Manga"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("manga"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Manhwa"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Manhua"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Webtoon"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Oneshot"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("one_shot"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("One Shot"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Doujinshi"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("OEL"))
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("Comic"))
    }

    @Test
    fun `fromPublishingType maps novel variants`() {
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("Novel"))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("Light Novel"))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("light_novel"))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("Web Novel"))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("web_novel"))
    }

    @Test
    fun `fromPublishingType maps book variants`() {
        assertEquals(ContentType.BOOK, ContentType.fromPublishingType("Artbook"))
        assertEquals(ContentType.BOOK, ContentType.fromPublishingType("art book"))
    }

    @Test
    fun `fromPublishingType returns UNKNOWN for unrecognized types`() {
        assertEquals(ContentType.UNKNOWN, ContentType.fromPublishingType(""))
        assertEquals(ContentType.UNKNOWN, ContentType.fromPublishingType("Movie"))
        assertEquals(ContentType.UNKNOWN, ContentType.fromPublishingType("Anime"))
        assertEquals(ContentType.UNKNOWN, ContentType.fromPublishingType("null"))
    }

    @Test
    fun `fromPublishingType is case-insensitive and trims whitespace`() {
        assertEquals(ContentType.MANGA, ContentType.fromPublishingType("  MANGA  "))
        assertEquals(ContentType.NOVEL, ContentType.fromPublishingType("  LIGHT NOVEL  "))
        assertEquals(ContentType.BOOK, ContentType.fromPublishingType("  ARTBOOK  "))
    }

    @Test
    fun `value round-trips through fromValue`() {
        for (type in ContentType.entries) {
            assertEquals(type, ContentType.fromValue(type.value))
        }
    }
}
