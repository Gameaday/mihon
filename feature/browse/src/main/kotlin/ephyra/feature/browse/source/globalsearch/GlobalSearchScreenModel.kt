package ephyra.feature.browse.source.globalsearch

import eu.kanade.tachiyomi.source.CatalogueSource
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.GetManga

@Factory
class GlobalSearchScreenModel(
    @InjectedParam initialQuery: String = "",
    @InjectedParam initialExtensionFilter: String? = null,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
) : SearchScreenModel(
    initialState = State(searchQuery = initialQuery),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || it.id in pinnedSourceIds }
    }
}
