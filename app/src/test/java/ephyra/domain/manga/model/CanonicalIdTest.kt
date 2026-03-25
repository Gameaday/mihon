package ephyra.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Unit tests for [CanonicalId] utilities.
 */
@Execution(ExecutionMode.CONCURRENT)
class CanonicalIdTest {

    @Test
    fun `parse returns prefix and remote ID for valid canonical ID`() {
        CanonicalId.parse("al:21") shouldBe ("al" to 21L)
        CanonicalId.parse("mal:30013") shouldBe ("mal" to 30013L)
        CanonicalId.parse("mu:54321") shouldBe ("mu" to 54321L)
    }

    @Test
    fun `parse returns null for invalid formats`() {
        CanonicalId.parse("") shouldBe null
        CanonicalId.parse("al") shouldBe null
        CanonicalId.parse(":21") shouldBe null
        CanonicalId.parse("al:") shouldBe null
        CanonicalId.parse("al:0") shouldBe null
        CanonicalId.parse("al:-1") shouldBe null
        CanonicalId.parse("al:abc") shouldBe null
    }

    @Test
    fun `toUrl returns correct URL for AniList`() {
        CanonicalId.toUrl("al:21") shouldBe "https://anilist.co/manga/21"
    }

    @Test
    fun `toUrl returns correct URL for MyAnimeList`() {
        CanonicalId.toUrl("mal:30013") shouldBe "https://myanimelist.net/manga/30013"
    }

    @Test
    fun `toUrl returns correct URL for MangaUpdates`() {
        CanonicalId.toUrl("mu:54321") shouldBe "https://www.mangaupdates.com/series.html?id=54321"
    }

    @Test
    fun `toUrl returns null for unknown prefix`() {
        CanonicalId.toUrl("xyz:123") shouldBe null
    }

    @Test
    fun `toUrl returns null for invalid canonical ID`() {
        CanonicalId.toUrl("invalid") shouldBe null
        CanonicalId.toUrl("") shouldBe null
    }

    @Test
    fun `toLabel returns correct label for each tracker`() {
        CanonicalId.toLabel("al:21") shouldBe "AniList"
        CanonicalId.toLabel("mal:30013") shouldBe "MyAnimeList"
        CanonicalId.toLabel("mu:54321") shouldBe "MangaUpdates"
    }

    @Test
    fun `toLabel returns null for unknown prefix`() {
        CanonicalId.toLabel("xyz:123") shouldBe null
    }

    @Test
    fun `toLabel returns null for invalid canonical ID`() {
        CanonicalId.toLabel("") shouldBe null
    }

    @Test
    fun `create returns canonical ID for valid prefix and ID`() {
        CanonicalId.create("al", 21L) shouldBe "al:21"
        CanonicalId.create("mal", 30013L) shouldBe "mal:30013"
        CanonicalId.create("mu", 54321L) shouldBe "mu:54321"
    }

    @Test
    fun `create returns null for unrecognized prefix`() {
        CanonicalId.create("xyz", 123L) shouldBe null
    }

    @Test
    fun `create returns null for invalid remote ID`() {
        CanonicalId.create("al", 0L) shouldBe null
        CanonicalId.create("al", -1L) shouldBe null
    }

    @Test
    fun `fromUrl parses AniList URLs`() {
        CanonicalId.fromUrl("https://anilist.co/manga/21") shouldBe "al:21"
        CanonicalId.fromUrl("https://anilist.co/manga/21/One-Piece") shouldBe "al:21"
        CanonicalId.fromUrl("https://anilist.co/manga/21/") shouldBe "al:21"
    }

    @Test
    fun `fromUrl parses MyAnimeList URLs`() {
        CanonicalId.fromUrl("https://myanimelist.net/manga/30013") shouldBe "mal:30013"
        CanonicalId.fromUrl("https://myanimelist.net/manga/30013/One_Punch-Man") shouldBe "mal:30013"
    }

    @Test
    fun `fromUrl parses MangaUpdates URLs`() {
        CanonicalId.fromUrl("https://www.mangaupdates.com/series.html?id=54321") shouldBe "mu:54321"
        CanonicalId.fromUrl("https://www.mangaupdates.com/series.html?id=54321&foo=bar") shouldBe "mu:54321"
    }

    @Test
    fun `fromUrl returns null for unrecognized URLs`() {
        CanonicalId.fromUrl("https://example.com/manga/123") shouldBe null
        CanonicalId.fromUrl("") shouldBe null
        CanonicalId.fromUrl("not a url") shouldBe null
    }

    @Test
    fun `ALL_PREFIXES contains all known prefixes`() {
        CanonicalId.ALL_PREFIXES shouldBe setOf("al", "mal", "mu", "jf")
    }

    @Test
    fun `create and parse round-trip`() {
        val id = CanonicalId.create("al", 21L)!!
        val (prefix, remoteId) = CanonicalId.parse(id)!!
        prefix shouldBe "al"
        remoteId shouldBe 21L
    }

    @Test
    fun `toUrl and fromUrl round-trip`() {
        val original = "al:21"
        val url = CanonicalId.toUrl(original)!!
        CanonicalId.fromUrl(url) shouldBe original
    }

    // -- Jellyfin (string-based IDs) --

    @Test
    fun `create with string ID for Jellyfin`() {
        CanonicalId.create("jf", "abc123def456") shouldBe "jf:abc123def456"
    }

    @Test
    fun `create with string ID returns null for unrecognized prefix`() {
        CanonicalId.create("xyz", "abc123") shouldBe null
    }

    @Test
    fun `create with string ID returns null for blank ID`() {
        CanonicalId.create("jf", "") shouldBe null
        CanonicalId.create("jf", "  ") shouldBe null
    }

    @Test
    fun `parseString returns prefix and string ID for Jellyfin`() {
        CanonicalId.parseString("jf:abc123def456") shouldBe ("jf" to "abc123def456")
    }

    @Test
    fun `parseString works for numeric IDs too`() {
        CanonicalId.parseString("al:21") shouldBe ("al" to "21")
    }

    @Test
    fun `parseString returns null for invalid formats`() {
        CanonicalId.parseString("") shouldBe null
        CanonicalId.parseString("jf") shouldBe null
        CanonicalId.parseString(":abc") shouldBe null
        CanonicalId.parseString("jf:") shouldBe null
    }

    @Test
    fun `toLabel returns Jellyfin for jf prefix`() {
        CanonicalId.toLabel("jf:abc123") shouldBe "Jellyfin"
    }

    @Test
    fun `toUrl returns null for Jellyfin without server URL`() {
        // Jellyfin URLs are server-relative — returns null without a server URL
        CanonicalId.toUrl("jf:abc123") shouldBe null
    }

    @Test
    fun `toUrl builds Jellyfin web client URL when server URL provided`() {
        // With a server URL, builds the Jellyfin web client details page URL
        CanonicalId.toUrl("jf:abc123-def456", jellyfinServerUrl = "http://192.168.1.100:8096") shouldBe
            "http://192.168.1.100:8096/web/index.html#!/details?id=abc123-def456"
    }

    @Test
    fun `toUrl trims trailing slash from Jellyfin server URL`() {
        CanonicalId.toUrl("jf:item-id", jellyfinServerUrl = "http://server:8096/") shouldBe
            "http://server:8096/web/index.html#!/details?id=item-id"
    }

    @Test
    fun `create and parseString round-trip for Jellyfin`() {
        val id = CanonicalId.create("jf", "abc123def456")!!
        val (prefix, remoteId) = CanonicalId.parseString(id)!!
        prefix shouldBe "jf"
        remoteId shouldBe "abc123def456"
    }

    @Test
    fun `fromUrl parses Jellyfin tracking URLs`() {
        CanonicalId.fromUrl("http://192.168.1.100:8096/Items/abc123-def456") shouldBe "jf:abc123-def456"
        CanonicalId.fromUrl("https://jellyfin.example.com/Items/uuid-value") shouldBe "jf:uuid-value"
        CanonicalId.fromUrl("http://server/Items/id123?fields=Overview") shouldBe "jf:id123"
    }

    @Test
    fun `fromUrl parses Jellyfin URLs with trailing slashes`() {
        CanonicalId.fromUrl("http://server/Items/abc123/") shouldBe "jf:abc123"
    }
}
