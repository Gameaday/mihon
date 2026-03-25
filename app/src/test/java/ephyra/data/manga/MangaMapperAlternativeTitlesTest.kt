package ephyra.data.manga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaMapperAlternativeTitlesTest {

    // --- JSON parsing (new format) ---

    @Test
    fun `parses JSON array format`() {
        val json = """["Title A","Title B","Title C"]"""
        assertEquals(
            listOf("Title A", "Title B", "Title C"),
            MangaMapper.parseAlternativeTitles(json),
        )
    }

    @Test
    fun `parses JSON array with special characters`() {
        val json = """["Re:Zero","Title with | pipe","Title with \"quotes\""]"""
        assertEquals(
            listOf("Re:Zero", "Title with | pipe", "Title with \"quotes\""),
            MangaMapper.parseAlternativeTitles(json),
        )
    }

    @Test
    fun `filters blank entries from JSON`() {
        val json = """["Title A","","  ","Title B"]"""
        assertEquals(
            listOf("Title A", "Title B"),
            MangaMapper.parseAlternativeTitles(json),
        )
    }

    @Test
    fun `empty JSON array returns empty list`() {
        assertEquals(
            emptyList<String>(),
            MangaMapper.parseAlternativeTitles("[]"),
        )
    }

    // --- Pipe-separated parsing (legacy format) ---

    @Test
    fun `parses legacy pipe-separated format`() {
        assertEquals(
            listOf("Title A", "Title B", "Title C"),
            MangaMapper.parseAlternativeTitles("Title A|Title B|Title C"),
        )
    }

    @Test
    fun `filters blank entries from pipe-separated`() {
        assertEquals(
            listOf("Title A", "Title B"),
            MangaMapper.parseAlternativeTitles("Title A||Title B|"),
        )
    }

    @Test
    fun `single title without pipe returns list of one`() {
        assertEquals(
            listOf("My Title"),
            MangaMapper.parseAlternativeTitles("My Title"),
        )
    }

    // --- Null/blank handling ---

    @Test
    fun `null returns empty list`() {
        assertEquals(emptyList<String>(), MangaMapper.parseAlternativeTitles(null))
    }

    @Test
    fun `blank string returns empty list`() {
        assertEquals(emptyList<String>(), MangaMapper.parseAlternativeTitles(""))
    }

    @Test
    fun `whitespace-only string returns empty list`() {
        assertEquals(emptyList<String>(), MangaMapper.parseAlternativeTitles("   "))
    }

    // --- Serialization (always JSON) ---

    @Test
    fun `serializes to JSON array`() {
        val result = MangaMapper.serializeAlternativeTitles(listOf("Title A", "Title B"))
        assertEquals("""["Title A","Title B"]""", result)
    }

    @Test
    fun `serializes titles with pipe character safely`() {
        val result = MangaMapper.serializeAlternativeTitles(listOf("Title|With|Pipes"))
        assertEquals("""["Title|With|Pipes"]""", result)
        // Verify round-trip
        assertEquals(
            listOf("Title|With|Pipes"),
            MangaMapper.parseAlternativeTitles(result),
        )
    }

    @Test
    fun `serializes null to null`() {
        assertEquals(null, MangaMapper.serializeAlternativeTitles(null))
    }

    @Test
    fun `serializes empty list to null`() {
        assertEquals(null, MangaMapper.serializeAlternativeTitles(emptyList()))
    }

    // --- Round-trip tests ---

    @Test
    fun `round-trip preserves titles with special characters`() {
        val titles = listOf("Re:Zero", "Title|Pipe", "Quotes \"here\"", "日本語タイトル")
        val serialized = MangaMapper.serializeAlternativeTitles(titles)
        val deserialized = MangaMapper.parseAlternativeTitles(serialized)
        assertEquals(titles, deserialized)
    }

    @Test
    fun `legacy pipe-separated can be re-serialized as JSON`() {
        // Simulates migration: read old format, write new format
        val legacy = "Attack on Titan|Shingeki no Kyojin|進撃の巨人"
        val parsed = MangaMapper.parseAlternativeTitles(legacy)
        val serialized = MangaMapper.serializeAlternativeTitles(parsed)
        val reParsed = MangaMapper.parseAlternativeTitles(serialized)
        assertEquals(parsed, reParsed)
    }
}
