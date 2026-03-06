package mihon.feature.migration.list.search

import com.aallam.similarity.NormalizedLevenshtein
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.util.Locale

typealias SearchAction<T> = suspend (String) -> List<T>

abstract class BaseSmartSearchEngine<T>(
    private val extraSearchParams: String? = null,
    private val eligibleThreshold: Double = MIN_ELIGIBLE_THRESHOLD,
) {
    private val normalizedLevenshtein = NormalizedLevenshtein()

    protected abstract fun getTitle(result: T): String

    /**
     * Returns alternative/cross titles for a search result, if available.
     * Override to provide alt titles from sources that support them.
     * Used for enhanced matching: if the primary title doesn't match well,
     * we try each alternative title and use the best match.
     */
    protected open fun getAlternativeTitles(result: T): List<String> = emptyList()

    protected suspend fun regularSearch(searchAction: SearchAction<T>, title: String): T? {
        return baseSearch(searchAction, listOf(title)) {
            bestTitleSimilarity(title, it)
        }
    }

    protected suspend fun deepSearch(searchAction: SearchAction<T>, title: String): T? {
        val cleanedTitle = cleanDeepSearchTitle(title)

        val queries = getDeepSearchQueries(cleanedTitle)

        return baseSearch(searchAction, queries) {
            bestCleanedTitleSimilarity(cleanedTitle, it)
        }
    }

    /**
     * Searches across all provided titles (primary + alternatives) and returns the best match.
     * Implements a tiered fallback strategy to minimize API calls:
     *
     * 1. Regular search with primary title, cross-evaluated against all known titles (1 API call)
     * 2. Regular search with each unique alt title (1 API call each, stop on exact match)
     * 3. Return best near-match if one was found above threshold
     * 4. Deep search with all title variants (multiple API calls, only if [deepSearchFallback] is true)
     *
     * Cross-evaluation: each candidate is scored against ALL known titles (primary + alt),
     * so a search for "Attack on Titan" can match "Shingeki no Kyojin" if that's a known alt title.
     * Near-matches are tracked across all title searches and returned before expensive deep search.
     * Similar alt titles are deduplicated to avoid redundant API calls.
     */
    protected suspend fun multiTitleSearch(
        searchAction: SearchAction<T>,
        primaryTitle: String,
        alternativeTitles: List<String> = emptyList(),
        deepSearchFallback: Boolean = true,
    ): SearchEntry<T>? {
        val allTitles = listOf(primaryTitle) + alternativeTitles

        // Dedup: skip alt titles nearly identical to primary or each other
        val uniqueAltTitles = filterDistinctTitles(primaryTitle, alternativeTitles)
        var bestNearMatch: SearchEntry<T>? = null

        // Step 1: Regular search with primary title, cross-evaluated against all titles (1 API call)
        val primaryMatch = crossTitleSearch(searchAction, primaryTitle, allTitles)
        if (primaryMatch != null) {
            val similarity = bestOverallSimilarity(allTitles, primaryMatch)
            if (similarity >= EXACT_MATCH_THRESHOLD) return SearchEntry(primaryMatch, similarity)
            if (similarity >= eligibleThreshold &&
                (bestNearMatch == null || similarity > bestNearMatch.distance)
            ) {
                bestNearMatch = SearchEntry(primaryMatch, similarity)
            }
        }

        // Step 2: Regular search with each unique alt title (1 API call each, stop on exact match)
        for (altTitle in uniqueAltTitles) {
            val match = crossTitleSearch(searchAction, altTitle, allTitles)
            if (match != null) {
                val similarity = bestOverallSimilarity(allTitles, match)
                if (similarity >= EXACT_MATCH_THRESHOLD) return SearchEntry(match, similarity)
                if (similarity >= eligibleThreshold &&
                    (bestNearMatch == null || similarity > bestNearMatch.distance)
                ) {
                    bestNearMatch = SearchEntry(match, similarity)
                }
            }
        }

        // Step 3: Return best near-match before attempting expensive deep search
        if (bestNearMatch != null) return bestNearMatch

        // Step 4: Deep search with all title variants (fail-fast on exact match)
        if (deepSearchFallback) {
            val deepResult = deepSearchMultipleTitles(searchAction, allTitles)
            if (deepResult != null) return deepResult
        }

        return null
    }

    /**
     * Computes the best similarity between the search title and a candidate's primary + alt titles.
     * This means if a source result has alt titles that match our search term better than
     * its primary title, we still find it.
     */
    private fun bestTitleSimilarity(searchTitle: String, candidate: T): Double {
        val primarySimilarity = normalizedLevenshtein.similarity(searchTitle, getTitle(candidate))
        val altTitles = getAlternativeTitles(candidate)
        if (altTitles.isEmpty()) return primarySimilarity

        val bestAltSimilarity = altTitles.maxOfOrNull { altTitle ->
            normalizedLevenshtein.similarity(searchTitle, altTitle)
        } ?: 0.0

        return maxOf(primarySimilarity, bestAltSimilarity)
    }

    /**
     * Computes the best cleaned-title similarity for deep search matching.
     */
    private fun bestCleanedTitleSimilarity(cleanedSearchTitle: String, candidate: T): Double {
        val cleanedPrimary = cleanDeepSearchTitle(getTitle(candidate))
        val primarySimilarity = normalizedLevenshtein.similarity(cleanedSearchTitle, cleanedPrimary)
        val altTitles = getAlternativeTitles(candidate)
        if (altTitles.isEmpty()) return primarySimilarity

        val bestAltSimilarity = altTitles.maxOfOrNull { altTitle ->
            val cleanedAlt = cleanDeepSearchTitle(altTitle)
            normalizedLevenshtein.similarity(cleanedSearchTitle, cleanedAlt)
        } ?: 0.0

        return maxOf(primarySimilarity, bestAltSimilarity)
    }

    /**
     * Evaluates a candidate against all known search titles (primary + alternatives).
     * For each search title, checks against the candidate's primary + alt titles.
     * Returns the maximum similarity across all combinations.
     */
    private fun bestOverallSimilarity(allSearchTitles: List<String>, candidate: T): Double {
        return allSearchTitles.maxOf { searchTitle ->
            bestTitleSimilarity(searchTitle, candidate)
        }
    }

    /**
     * Searches with [queryTitle] but evaluates candidates against [allTitles].
     * This enables cross-title matching: a search for "Attack on Titan" can match
     * a result titled "Shingeki no Kyojin" if that's one of our known alternative titles.
     */
    private suspend fun crossTitleSearch(
        searchAction: SearchAction<T>,
        queryTitle: String,
        allTitles: List<String>,
    ): T? {
        return baseSearch(searchAction, listOf(queryTitle)) {
            bestOverallSimilarity(allTitles, it)
        }
    }

    /**
     * Deep search across multiple title variants, trying each sequentially.
     * Stops immediately on an exact match; otherwise returns the best near-match.
     * Deduplicates cleaned titles to avoid redundant API calls.
     */
    private suspend fun deepSearchMultipleTitles(
        searchAction: SearchAction<T>,
        allTitles: List<String>,
    ): SearchEntry<T>? {
        val cleanedTitles = allTitles
            .map { cleanDeepSearchTitle(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (cleanedTitles.isEmpty()) return null

        var bestResult: SearchEntry<T>? = null

        for (cleanedTitle in cleanedTitles) {
            val queries = getDeepSearchQueries(cleanedTitle)
            val result = baseSearch(searchAction, queries) { candidate ->
                cleanedTitles.maxOf { ct -> bestCleanedTitleSimilarity(ct, candidate) }
            }
            if (result != null) {
                val similarity = cleanedTitles.maxOf { ct ->
                    bestCleanedTitleSimilarity(ct, result)
                }
                if (similarity >= EXACT_MATCH_THRESHOLD) return SearchEntry(result, similarity)
                if (bestResult == null || similarity > bestResult.distance) {
                    bestResult = SearchEntry(result, similarity)
                }
            }
        }

        return bestResult
    }

    /**
     * Filters alternative titles to remove those nearly identical to the primary title
     * or to each other (case-insensitive), avoiding redundant API calls.
     */
    private fun filterDistinctTitles(primaryTitle: String, alternativeTitles: List<String>): List<String> {
        val primaryLower = primaryTitle.lowercase(Locale.getDefault())
        val accepted = mutableListOf<String>()
        val acceptedLower = mutableListOf<String>()

        for (alt in alternativeTitles) {
            val altLower = alt.lowercase(Locale.getDefault())
            if (altLower.isBlank()) continue
            if (normalizedLevenshtein.similarity(primaryLower, altLower) >= DEDUP_SIMILARITY_THRESHOLD) continue
            if (acceptedLower.any {
                    normalizedLevenshtein.similarity(it, altLower) >= DEDUP_SIMILARITY_THRESHOLD
                }
            ) {
                continue
            }
            accepted.add(alt)
            acceptedLower.add(altLower)
        }

        return accepted
    }

    private suspend fun baseSearch(
        searchAction: SearchAction<T>,
        queries: List<String>,
        calculateDistance: (T) -> Double,
    ): T? {
        val eligibleManga = supervisorScope {
            queries.map { query ->
                async(Dispatchers.Default) {
                    val builtQuery = if (!extraSearchParams.isNullOrBlank()) {
                        "$query $extraSearchParams"
                    } else {
                        query
                    }

                    val candidates = searchAction(builtQuery)
                    candidates
                        .map {
                            val distance = if (queries.size > 1 || candidates.size > 1) {
                                calculateDistance(it)
                            } else {
                                1.0
                            }
                            SearchEntry(it, distance)
                        }
                        .filter { it.distance >= eligibleThreshold }
                }
            }
                .flatMap { it.await() }
        }

        return eligibleManga.maxByOrNull { it.distance }?.entry
    }

    private fun cleanDeepSearchTitle(title: String): String {
        val preTitle = title.lowercase(Locale.getDefault())

        // Remove text in brackets
        var cleanedTitle = removeTextInBrackets(preTitle, true)
        if (cleanedTitle.length <= 5) { // Title is suspiciously short, try parsing it backwards
            cleanedTitle = removeTextInBrackets(preTitle, false)
        }

        // Strip chapter reference RU
        cleanedTitle = cleanedTitle.replace(chapterRefCyrillicRegexp, " ").trim()

        // Strip non-special characters
        val cleanedTitleEng = cleanedTitle.replace(titleRegex, " ")

        // Do not strip foreign language letters if cleanedTitle is too short
        cleanedTitle = if (cleanedTitleEng.length <= 5) {
            cleanedTitle.replace(titleCyrillicRegex, " ")
        } else {
            cleanedTitleEng
        }

        // Strip splitters and consecutive spaces
        cleanedTitle = cleanedTitle.trim().replace(" - ", " ").replace(consecutiveSpacesRegex, " ").trim()

        return cleanedTitle
    }

    private fun removeTextInBrackets(text: String, readForward: Boolean): String {
        val openingChars = if (readForward) "([<{" else ")]}>"
        val closingChars = if (readForward) ")]}>" else "([<{"
        var depth = 0

        return buildString {
            for (char in (if (readForward) text else text.reversed())) {
                when (char) {
                    in openingChars -> depth++
                    in closingChars -> if (depth > 0) depth-- // Avoid depth going negative on mismatched closing
                    else -> if (depth == 0) {
                        // If reading backward, the result is reversed, so prepend
                        if (readForward) append(char) else insert(0, char)
                    }
                }
            }
        }
    }

    private fun getDeepSearchQueries(cleanedTitle: String): List<String> {
        val splitCleanedTitle = cleanedTitle.split(" ")
        val splitSortedByLargest = splitCleanedTitle.sortedByDescending { it.length }

        if (splitCleanedTitle.isEmpty()) {
            return emptyList()
        }

        // Search cleaned title
        // Search two largest words
        // Search largest word
        // Search first two words
        // Search first word
        val searchQueries = listOf(
            listOf(cleanedTitle),
            splitSortedByLargest.take(2),
            splitSortedByLargest.take(1),
            splitCleanedTitle.take(2),
            splitCleanedTitle.take(1),
        )

        return searchQueries
            .map { it.joinToString(" ").trim() }
            .distinct()
    }

    companion object {
        const val MIN_ELIGIBLE_THRESHOLD = 0.4
        const val EXACT_MATCH_THRESHOLD = 0.9
        const val DEDUP_SIMILARITY_THRESHOLD = 0.9

        private val titleRegex = Regex("[^a-zA-Z0-9- ]")
        private val titleCyrillicRegex = Regex("[^\\p{L}0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
        private val chapterRefCyrillicRegexp = Regex("""((- часть|- глава) \d*)""")
    }
}

data class SearchEntry<T>(val entry: T, val distance: Double)
