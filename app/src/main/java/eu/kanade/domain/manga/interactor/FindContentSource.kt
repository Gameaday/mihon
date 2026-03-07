package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavoritesByCanonicalId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * Reusable interactor for finding the best content source(s) for a given manga.
 *
 * This is the core "find a content source" API that can be called from:
 * - **Discover flow**: After adding an authority manga, auto-find a source to pair with.
 * - **MatchUnlinkedManga**: After resolving canonical IDs, find sources for authority-only entries.
 * - **Library migration**: Already uses SmartSourceSearchEngine directly.
 * - **Any future feature** that needs to pair a manga identity with content.
 *
 * Search strategy (per source):
 * 1. **Canonical ID** — free local DB lookup, 0 API calls.
 * 2. **Smart title search** — tiered: primary title → alt titles → near-match → optional deep search.
 *
 * Results are returned ranked by confidence (1.0 = canonical ID match, 0.0–1.0 = title similarity).
 * Only the top result per source is included.
 */
class FindContentSource(
    private val sourceManager: SourceManager,
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId,
) {

    /**
     * A content source match with confidence score.
     * @param manga The manga as it exists on the matched source.
     * @param source The catalogue source that hosts this manga.
     * @param confidence Match confidence: 1.0 = canonical ID, 0.4–0.9 = title similarity.
     */
    data class SourceMatch(
        val manga: SManga,
        val source: CatalogueSource,
        val sourceId: Long,
        val sourceName: String,
        val confidence: Double,
    )

    /**
     * Finds the best content source(s) for the given manga across all enabled sources.
     *
     * @param manga The manga to find sources for (should have title, alt titles, optional canonicalId).
     * @param maxResults Maximum number of source matches to return (default 5).
     * @param deepSearch Whether to use deep search fallback (more API calls, better recall).
     * @param onProgress Optional callback: (sourcesSearched, totalSources).
     * @return List of [SourceMatch] sorted by confidence descending.
     */
    suspend fun findSources(
        manga: Manga,
        maxResults: Int = MAX_RESULTS,
        deepSearch: Boolean = false,
        onProgress: (suspend (searched: Int, total: Int) -> Unit)? = null,
    ): List<SourceMatch> = withIOContext {
        val catalogueSources = sourceManager.getCatalogueSources()
            .filter { it.id > 0 && it.id != manga.source } // Skip invalid IDs and current source
        if (catalogueSources.isEmpty()) return@withIOContext emptyList()

        val total = catalogueSources.size
        val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
        val searchedCount = java.util.concurrent.atomic.AtomicInteger(0)

        val results = supervisorScope {
            catalogueSources.map { source ->
                async {
                    semaphore.withPermit {
                        try {
                            searchSource(manga, source, deepSearch)
                        } catch (e: Exception) {
                            logcat(LogPriority.DEBUG, e) {
                                "Source search failed: ${source.name} for '${manga.title}'"
                            }
                            null
                        } finally {
                            val current = searchedCount.incrementAndGet()
                            onProgress?.invoke(current, total)
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        results
            .sortedByDescending { it.confidence }
            .take(maxResults)
    }

    /**
     * Finds the single best content source match for the given manga.
     * Convenience method that returns the highest-confidence result or null.
     */
    suspend fun findBestSource(
        manga: Manga,
        deepSearch: Boolean = false,
    ): SourceMatch? = findSources(manga, maxResults = 1, deepSearch = deepSearch).firstOrNull()

    /**
     * Searches a single source for the manga, trying canonical ID first, then smart title search.
     */
    private suspend fun searchSource(
        manga: Manga,
        source: CatalogueSource,
        deepSearch: Boolean,
    ): SourceMatch? {
        // Tier 1: Canonical ID lookup (free, 0 API calls)
        val canonicalMatch = findByCanonicalId(manga, source.id)
        if (canonicalMatch != null) {
            return SourceMatch(
                manga = canonicalMatch.toSManga(),
                source = source,
                sourceId = source.id,
                sourceName = source.name,
                confidence = 1.0,
            )
        }

        // Tier 2+: Smart title search using the same engine as migration
        val engine = SmartSourceSearchEngine(extraSearchParams = null)
        val searchResult = engine.multiTitleSearch(
            source = source,
            primaryTitle = manga.title,
            alternativeTitles = manga.alternativeTitles,
            deepSearchFallback = deepSearch,
        )

        if (searchResult != null) {
            val (matchedManga, confidence) = searchResult
            return SourceMatch(
                manga = matchedManga.toSManga(),
                source = source,
                sourceId = source.id,
                sourceName = source.name,
                confidence = confidence,
            )
        }

        return null
    }

    /**
     * Checks if a manga with the same canonical ID exists on the target source (local DB only).
     */
    private suspend fun findByCanonicalId(manga: Manga, targetSourceId: Long): Manga? {
        val canonicalId = manga.canonicalId ?: return null
        return try {
            getFavoritesByCanonicalId.await(canonicalId, manga.id)
                .firstOrNull { it.source == targetSourceId }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Canonical ID lookup failed for '${manga.title}'" }
            null
        }
    }

    /**
     * Converts a domain Manga to SManga for the SourceMatch return type.
     * This lets callers get the source-native representation for further operations.
     */
    private fun Manga.toSManga(): SManga {
        return SManga.create().apply {
            url = this@toSManga.url
            title = this@toSManga.title
            thumbnail_url = this@toSManga.thumbnailUrl
            description = this@toSManga.description
            author = this@toSManga.author
            artist = this@toSManga.artist
            status = this@toSManga.status.toInt()
        }
    }

    companion object {
        /** Maximum concurrent source searches to avoid overwhelming network. */
        private const val MAX_CONCURRENT_SEARCHES = 5

        /** Default maximum results to return. */
        private const val MAX_RESULTS = 5
    }
}
