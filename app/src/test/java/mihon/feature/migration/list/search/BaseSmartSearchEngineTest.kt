package mihon.feature.migration.list.search

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
        found!!.title shouldBe "One Piece"
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
        found!!.title shouldBe "Shingeki no Kyojin"
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
        found!!.title shouldBe "One Piece"
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
        found!!.title shouldBe "one piece"
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
        found!!.title shouldBe "One Piece - Special"
        deepSearchCalled shouldBe false
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
}
