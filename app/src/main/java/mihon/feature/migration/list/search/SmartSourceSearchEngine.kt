package mihon.feature.migration.list.search

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga

class SmartSourceSearchEngine(extraSearchParams: String?) : BaseSmartSearchEngine<SManga>(extraSearchParams) {

    override fun getTitle(result: SManga) = result.title

    suspend fun regularSearch(source: CatalogueSource, title: String): Manga? {
        return regularSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }
    }

    suspend fun deepSearch(source: CatalogueSource, title: String): Manga? {
        return deepSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }
    }

    /**
     * Enhanced search that tries multiple known titles for a manga.
     * Implements tiered fallback: primary title → alt titles → near-match → deep search.
     * Returns a pair of (Manga, matchConfidence) where confidence is 0.0-1.0.
     *
     * @param deepSearchFallback If true, falls back to deep search when no title matches.
     *   Set to false to limit to title-based matching only (fewer API calls).
     */
    suspend fun multiTitleSearch(
        source: CatalogueSource,
        primaryTitle: String,
        alternativeTitles: List<String> = emptyList(),
        deepSearchFallback: Boolean = true,
    ): Pair<Manga, Double>? {
        return multiTitleSearch(makeSearchAction(source), primaryTitle, alternativeTitles, deepSearchFallback)?.let {
            it.entry.toDomainManga(source.id) to it.distance
        }
    }

    private fun makeSearchAction(source: CatalogueSource): SearchAction<SManga> = { query ->
        source.getSearchManga(1, query, source.getFilterList()).mangas
    }
}
