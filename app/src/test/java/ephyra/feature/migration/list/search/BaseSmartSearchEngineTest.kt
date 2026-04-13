package ephyra.feature.migration.list.search

import ephyra.domain.manga.interactor.BaseSmartSearchEngine
import ephyra.domain.manga.interactor.SearchAction
import ephyra.domain.manga.interactor.SearchEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Tests for the enhanced BaseSmartSearchEngine with alternative title matching.
 * Uses a concrete TestSearchEngine to test the abstract base class.
 */
@Execution(ExecutionMode.CONCURRENT)
class BaseSmartSearchEngineTest {

    /** Simple result type for testing. */
    data class TestResult(
        val title: String,
        val altTitles: List<String> = emptyList(),
    )

    /** Concrete implementation for testing with configurable alt titles. */
    class TestSearchEngine : BaseSmartSearchEngine<TestResult>() {
        override fun getTitle(result: TestResult) = result.title
        override fun getAlternativeTitles(result: TestResult) = result.altTitles

        // Expose protected methods for testing
        suspend fun testRegularSearch(
            searchAction: SearchAction<TestResult>,
            title: String,
        ) = regularSearch(searchAction, title)

        suspend fun testDeepSearch(
            searchAction: SearchAction<TestResult>,
            title: String,
        ) = deepSearch(searchAction, title)

        suspend fun testMultiTitleSearch(
            searchAction: SearchAction<TestResult>,
            primaryTitle: String,
            alternativeTitles: List<String> = emptyList(),
            deepSearchFallback: Boolean = true,
        ) = multiTitleSearch(searchAction, primaryTitle, alternativeTitles, deepSearchFallback)
    }

    private val engine = TestSearchEngine()

    @Test
    fun `regularSearch finds exact match`() = runTest {
        val results = listOf(TestResult("One Piece"), TestResult("Two Piece"))
        val found = engine.testRegularSearch({ results }, "One Piece")
        found shouldNotBe null
        found!!.title shouldBe "One Piece"
    }

    @Test
    fun `regularSearch returns null for no match when multiple candidates`() = runTest {
        // With multiple candidates, distance is actually calculated and poor matches are filtered
        val results = listOf(TestResult("Totally Different Manga"), TestResult("Another One"))
        val found = engine.testRegularSearch({ results }, "xyzzy nonexistent")
        found shouldBe null
    }

    @Test
    fun `regularSearch picks best match from candidates with alt titles`() = runTest {
        // "Shingeki no Kyojin" has alt title "Attack on Titan"
        val results = listOf(
            TestResult("Some Other Manga"),
            TestResult("Shingeki no Kyojin", altTitles = listOf("Attack on Titan", "進撃の巨人")),
        )
        // Searching for "Attack on Titan" should match via alt title
        val found = engine.testRegularSearch({ results }, "Attack on Titan")
        found shouldNotBe null
        found!!.title shouldBe "Shingeki no Kyojin"
    }

    @Test
    fun `multiTitleSearch finds match with primary title`() = runTest {
        val results = listOf(TestResult("One Piece"))
        val found = engine.testMultiTitleSearch({ results }, "One Piece")
        found shouldNotBe null
        found!!.entry.title shouldBe "One Piece"
    }

    @Test
    fun `multiTitleSearch finds match with alternative title when primary fails`() = runTest {
        // Source uses romaji title, but we search with English
        val results = listOf(TestResult("Shingeki no Kyojin"))

        // Primary title doesn't match well
        val found = engine.testMultiTitleSearch(
            { query ->
                // Only return results when querying the romaji name
                if (query.contains("Shingeki") || query.contains("shingeki")) results else emptyList()
            },
            primaryTitle = "Attack on Titan",
            alternativeTitles = listOf("Shingeki no Kyojin", "進撃の巨人"),
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "Shingeki no Kyojin"
    }

    @Test
    fun `multiTitleSearch returns null when nothing matches`() = runTest {
        val found = engine.testMultiTitleSearch(
            { emptyList() },
            primaryTitle = "Nonexistent Manga",
            alternativeTitles = listOf("Also Nonexistent"),
        )
        found shouldBe null
    }

    @Test
    fun `multiTitleSearch prefers primary title over alt title`() = runTest {
        // Both primary and alt match, but primary should be tried first
        var queriedTitles = mutableListOf<String>()
        val primaryResult = TestResult("One Piece")
        val found = engine.testMultiTitleSearch(
            { query ->
                queriedTitles.add(query)
                listOf(primaryResult)
            },
            primaryTitle = "One Piece",
            alternativeTitles = listOf("OP", "ワンピース"),
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "One Piece"
        // Primary title should be searched first
        queriedTitles.first() shouldBe "One Piece"
    }

    @Test
    fun `multiTitleSearch falls back to deep search when titles fail`() = runTest {
        // No exact match on primary or alt titles, but deep search finds it
        val results = listOf(TestResult("one piece"))
        var searchCount = 0
        val found = engine.testMultiTitleSearch(
            { query ->
                searchCount++
                // Only return results for cleaned deep-search queries (lowercase, simplified)
                if (query.lowercase() == "one piece" || query == "piece" || query == "one") {
                    results
                } else {
                    emptyList()
                }
            },
            primaryTitle = "One Piece [Special Edition]",
            alternativeTitles = listOf("AAAA BBBB CCCC"),
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "one piece"
        // Should have attempted more than just primary + 1 alt title (deep search generates multiple queries)
        (searchCount > 2) shouldBe true
    }

    @Test
    fun `multiTitleSearch skips deep search when deepSearchFallback is false`() = runTest {
        var searchCount = 0
        val found = engine.testMultiTitleSearch(
            { query ->
                searchCount++
                emptyList() // Nothing matches
            },
            primaryTitle = "One Piece [Special Edition]",
            alternativeTitles = listOf("AAAA BBBB CCCC"),
            deepSearchFallback = false,
        )
        found shouldBe null
        // Should only have tried primary + 1 alt title (no deep search queries)
        searchCount shouldBe 2
    }

    @Test
    fun `multiTitleSearch returns near-match before attempting deep search`() = runTest {
        // Primary title search returns a near-match (not exact), alt titles return nothing
        val nearMatch = TestResult("One Piece - Special")
        var deepSearchCalled = false
        val found = engine.testMultiTitleSearch(
            { query ->
                if (query == "One Piece") {
                    listOf(nearMatch, TestResult("Something Else"))
                } else if (query.lowercase().let { it == "one piece" || it == "piece" || it == "one" }) {
                    // Deep search queries would hit here
                    deepSearchCalled = true
                    listOf(TestResult("one piece"))
                } else {
                    emptyList()
                }
            },
            primaryTitle = "One Piece",
            alternativeTitles = listOf("ZZZZ Nonexistent"),
        )
        found shouldNotBe null
        // Should return the near-match from primary search rather than doing deep search
        found!!.entry.title shouldBe "One Piece - Special"
        deepSearchCalled shouldBe false
    }

    @Test
    fun `multiTitleSearch cross-evaluates candidates against all known titles`() = runTest {
        // Source returns "Shingeki no Kyojin" for "Attack on Titan" query but not for "Shingeki" query.
        // Without cross-evaluation: similarity("Attack on Titan", "Shingeki no Kyojin") < threshold → filtered
        // With cross-evaluation: similarity("Shingeki no Kyojin", "Shingeki no Kyojin") = 1.0 → found in step 1
        val result = TestResult("Shingeki no Kyojin")
        var searchCount = 0
        val found = engine.testMultiTitleSearch(
            { query ->
                searchCount++
                when {
                    query == "Attack on Titan" -> listOf(result, TestResult("Other Manga"))
                    else -> emptyList()
                }
            },
            primaryTitle = "Attack on Titan",
            alternativeTitles = listOf("Shingeki no Kyojin"),
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "Shingeki no Kyojin"
        // Found in step 1 (primary search) via cross-evaluation — no need to search alt title
        searchCount shouldBe 1
    }

    @Test
    fun `multiTitleSearch deduplicates similar alt titles`() = runTest {
        var searchCount = 0
        engine.testMultiTitleSearch(
            { query ->
                searchCount++
                emptyList()
            },
            primaryTitle = "One Piece",
            alternativeTitles = listOf("one piece", "ONE PIECE", "Really Different Title"),
            deepSearchFallback = false,
        )
        // "one piece" and "ONE PIECE" are nearly identical to "One Piece" → skipped
        // Only primary + "Really Different Title" should be searched
        searchCount shouldBe 2
    }

    @Test
    fun `multiTitleSearch deep search tries alt title variants`() = runTest {
        // Both regular searches fail, but deep search with alt title variant succeeds
        var searchCount = 0
        val found = engine.testMultiTitleSearch(
            { query ->
                searchCount++
                // Only cleaned deep-search queries containing "shingeki" match
                if (!query.contains("!") && query.contains("shingeki")) {
                    listOf(TestResult("shingeki no kyojin"))
                } else {
                    emptyList()
                }
            },
            primaryTitle = "ZZZZ Attack [Season 1]",
            alternativeTitles = listOf("Shingeki no Kyojin!"),
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "shingeki no kyojin"
        // Regular searches (2) + deep search queries should total more than 2
        (searchCount > 2) shouldBe true
    }

    @Test
    fun `deepSearch handles cleaned titles`() = runTest {
        val results = listOf(TestResult("one piece"))
        val found = engine.testDeepSearch({ results }, "One Piece [Chapter 1000]")
        found shouldNotBe null
        found!!.title shouldBe "one piece"
    }

    @Test
    fun `EXACT_MATCH_THRESHOLD is 0_9`() {
        BaseSmartSearchEngine.EXACT_MATCH_THRESHOLD shouldBe 0.9
    }

    @Test
    fun `MIN_ELIGIBLE_THRESHOLD is 0_4`() {
        BaseSmartSearchEngine.MIN_ELIGIBLE_THRESHOLD shouldBe 0.4
    }

    @Test
    fun `DEDUP_SIMILARITY_THRESHOLD is 0_9`() {
        BaseSmartSearchEngine.DEDUP_SIMILARITY_THRESHOLD shouldBe 0.9
    }

    @Test
    fun `multiTitleSearch with empty primary title returns null`() = runTest {
        val found = engine.testMultiTitleSearch(
            { emptyList() },
            primaryTitle = "",
            alternativeTitles = emptyList(),
            deepSearchFallback = false,
        )
        found shouldBe null
    }

    @Test
    fun `multiTitleSearch with blank alt titles skips them`() = runTest {
        var searchCount = 0
        engine.testMultiTitleSearch(
            { query ->
                searchCount++
                emptyList()
            },
            primaryTitle = "One Piece",
            alternativeTitles = listOf("", "  ", "   "),
            deepSearchFallback = false,
        )
        // Only primary title should be searched since all alt titles are blank
        searchCount shouldBe 1
    }

    @Test
    fun `multiTitleSearch near-match returned before deep search`() = runTest {
        // Primary title search returns a near-match (> 0.4 but < 0.9 similarity)
        val nearMatch = TestResult("One Piece (2024)")
        var deepSearchCalled = false
        val found = engine.testMultiTitleSearch(
            { query ->
                if (query.contains("deep") || query.length < 5) {
                    deepSearchCalled = true
                    emptyList()
                } else {
                    listOf(nearMatch)
                }
            },
            primaryTitle = "One Piece",
            alternativeTitles = emptyList(),
            deepSearchFallback = false,
        )
        // Near-match should be returned — similarity("One Piece", "One Piece (2024)") > MIN_ELIGIBLE
        found shouldNotBe null
        found!!.entry.title shouldBe "One Piece (2024)"
    }

    @Test
    fun `regularSearch returns null for empty search results`() = runTest {
        val found = engine.testRegularSearch(
            { emptyList() },
            "Non-existent Manga",
        )
        found shouldBe null
    }

    @Test
    fun `multiTitleSearch cross-title matches alt title of result`() = runTest {
        // When we search for "Shingeki no Kyojin" and result has "Attack on Titan" as primary
        // but "Shingeki no Kyojin" as an alt title, cross-title evaluation should match
        val result = TestResult(
            title = "Attack on Titan",
            altTitles = listOf("Shingeki no Kyojin"),
        )
        val found = engine.testMultiTitleSearch(
            { listOf(result) },
            primaryTitle = "Shingeki no Kyojin",
            deepSearchFallback = false,
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "Attack on Titan"
    }

    @Test
    fun `multiTitleSearch with known alt title matches even without primary match`() = runTest {
        // Search for "Attack on Titan" with alt "Shingeki no Kyojin" — source has
        // "Shingeki no Kyojin" as its primary title. Cross-eval against all titles should match.
        val result = TestResult(title = "Shingeki no Kyojin")
        val found = engine.testMultiTitleSearch(
            { listOf(result) },
            primaryTitle = "Attack on Titan",
            alternativeTitles = listOf("Shingeki no Kyojin"),
            deepSearchFallback = false,
        )
        found shouldNotBe null
        found!!.entry.title shouldBe "Shingeki no Kyojin"
    }

    @Test
    fun `multiTitleSearch primary title empty still searches alt titles`() = runTest {
        // Edge case: primary title is blank but alt titles are provided
        val result = TestResult(title = "Naruto")
        val searchQueries = mutableListOf<String>()
        val found = engine.testMultiTitleSearch(
            { query ->
                searchQueries.add(query)
                if (query == "Naruto") listOf(result) else emptyList()
            },
            primaryTitle = "",
            alternativeTitles = listOf("Naruto"),
            deepSearchFallback = false,
        )
        // Should still search alt titles
        found shouldNotBe null
        found!!.entry.title shouldBe "Naruto"
    }

    @Test
    fun `multiTitleSearch deduplicates case-only alt title variants`() = runTest {
        // Alt titles very similar to primary should be skipped to avoid redundant API calls
        var searchCount = 0
        engine.testMultiTitleSearch(
            { query ->
                searchCount++
                emptyList()
            },
            primaryTitle = "Naruto",
            // "NARUTO" is nearly identical to "Naruto" — should be deduplicated
            alternativeTitles = listOf("NARUTO"),
            deepSearchFallback = false,
        )
        // Only primary title searched; "NARUTO" deduplicated against "Naruto"
        searchCount shouldBe 1
    }
}
