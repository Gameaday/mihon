package ephyra.feature.browse.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import ephyra.feature.browse.source.globalsearch.SearchItemResult
import ephyra.feature.browse.source.globalsearch.SearchScreenModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.source.service.SourceManager
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.source.service.SourceManager

@Factory
class MigrateSearchScreenModel(
    @InjectedParam val mangaId: Long,
    private val getManga: GetManga,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
) : SearchScreenModel(
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
) {

    private val migrationSources by lazy { sourcePreferences.migrationSources().get() }
    private val migrationSourceRank by lazy { migrationSources.withIndex().associate { (i, id) -> id to i } }

    override val sortComparator = { map: Map<CatalogueSource, SearchItemResult> ->
        compareBy<CatalogueSource>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { migrationSourceRank.getOrDefault(it.id, Int.MAX_VALUE) },
        )
    }

    init {
        screenModelScope.launch {
            val manga = getManga.await(mangaId)!!
            mutableState.update {
                it.copy(
                    from = manga,
                    searchQuery = manga.title,
                )
            }
            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return migrationSources.mapNotNull { sourceManager.get(it) as? CatalogueSource }
    }
}
