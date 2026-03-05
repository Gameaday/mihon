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
     * 1. Regular search with primary title (1 API call)
     * 2. Regular search with each alt title (1 API call each, stop on exact match)
     * 3. Return best near-match if one was found above threshold
     * 4. Deep search fallback (multiple API calls, only if [deepSearchFallback] is true)
     *
     * Near-matches are tracked across all title searches so a 0.8 similarity match
     * from step 1 can be returned in step 3 without wasting API calls on deep search.
     */
    protected suspend fun multiTitleSearch(
        searchAction: SearchAction<T>,
        primaryTitle: String,
        alternativeTitles: List<String> = emptyList(),
        deepSearchFallback: Boolean = true,
    ): T? {
        var bestNearMatch: SearchEntry<T>? = null

        // Step 1: Try exact match on primary title first (cheapest, 1 API call)
        val primaryMatch = regularSearch(searchAction, primaryTitle)
        if (primaryMatch != null) {
            val similarity = bestTitleSimilarity(primaryTitle, primaryMatch)
            if (similarity >= EXACT_MATCH_THRESHOLD) return primaryMatch
            if (similarity >= eligibleThreshold &&
                (bestNearMatch == null || similarity > bestNearMatch.distance)
            ) {
                bestNearMatch = SearchEntry(primaryMatch, similarity)
            }
        }

        // Step 2: Try each alternative title with regular search (1 API call each)
        for (altTitle in alternativeTitles) {
            val match = regularSearch(searchAction, altTitle)
            if (match != null) {
                val similarity = bestTitleSimilarity(altTitle, match)
                if (similarity >= EXACT_MATCH_THRESHOLD) return match
                if (similarity >= eligibleThreshold &&
                    (bestNearMatch == null || similarity > bestNearMatch.distance)
                ) {
                    bestNearMatch = SearchEntry(match, similarity)
                }
            }
        }

        // Step 3: Return best near-match before attempting expensive deep search
        if (bestNearMatch != null) return bestNearMatch.entry

        // Step 4: Deep search fallback (only if enabled — multiple API calls)
        if (deepSearchFallback) {
            return deepSearch(searchAction, primaryTitle)
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

        private val titleRegex = Regex("[^a-zA-Z0-9- ]")
        private val titleCyrillicRegex = Regex("[^\\p{L}0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
        private val chapterRefCyrillicRegexp = Regex("""((- часть|- глава) \d*)""")
    }
}

data class SearchEntry<T>(val entry: T, val distance: Double)
