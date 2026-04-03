package ephyra.feature.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.util.ioCoroutineScope
import ephyra.domain.extension.service.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ephyra.domain.manga.model.toDomainManga
import ephyra.core.common.preference.toggle
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourceManager

abstract class SearchScreenModel(
    initialState: State = State(),
    private val sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
) : StateScreenModel<SearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()

    // Parse Set<String> source IDs to Set<Long> once at construction time to avoid creating a
    // new String per source on every getEnabledSources()/sortComparator call.
    private val disabledSourceIds = sourcePreferences.disabledSources().get().mapTo(HashSet()) { it.toLong() }
    protected val pinnedSourceIds = sourcePreferences.pinnedSources().get().mapTo(HashSet()) { it.toLong() }

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<CatalogueSource, SearchItemResult> ->
        val sortKeys = HashMap<Long, String>((map.size / 0.75f + 1).toInt())
        map.keys.forEach { sortKeys[it.id] = "${it.name.lowercase()} (${it.lang})" }
        compareBy<CatalogueSource>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { it.id !in pinnedSourceIds },
            { sortKeys.getValue(it.id) },
        )
    }

    init {
        screenModelScope.launch {
            sourcePreferences.globalSearchFilterState().changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<CatalogueSource> {
        val filtered = sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && it.id !in disabledSourceIds }
        val sortKeys = HashMap<Long, String>((filtered.size / 0.75f + 1).toInt())
        filtered.forEach { sortKeys[it.id] = "${it.name.lowercase()} (${it.lang})" }
        return filtered.sortedWith(
            compareBy(
                { it.id !in pinnedSourceIds },
                { sortKeys.getValue(it.id) },
            ),
        )
    }

    private fun getSelectedSources(): List<CatalogueSource> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        // Build O(1) lookup set for enabled source IDs to replace O(N) List.contains
        val enabledIds = enabledSources.mapTo(HashSet(enabledSources.size)) { it.id }
        // Single-pass: find matching extension and collect enabled CatalogueSources in one loop
        val result = mutableListOf<CatalogueSource>()
        for (ext in extensionManager.installedExtensionsFlow.value) {
            if (ext.pkgName != filter) continue
            for (source in ext.sources) {
                if (source is CatalogueSource && source.id in enabledIds) {
                    result.add(source)
                }
            }
        }
        return result
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        sourcePreferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query, source.getFilterList())
                        }

                        val seenUrls = HashSet<String>(page.mangas.size)
                        val domainMangas = page.mangas.mapNotNullTo(ArrayList(page.mangas.size)) { smanga ->
                            if (seenUrls.add(smanga.url)) smanga.toDomainManga(source.id) else null
                        }
                        val titles = networkToLocalManga(domainMangas)

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: PersistentMap<CatalogueSource, SearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: CatalogueSource, result: SearchItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        screenModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            mutableState.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<CatalogueSource, SearchItemResult> = persistentMapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = if (!onlyShowHasResults) {
            items
        } else {
            items.filter { (_, result) ->
                result.isVisible(onlyShowHasResults)
            }
        }
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
