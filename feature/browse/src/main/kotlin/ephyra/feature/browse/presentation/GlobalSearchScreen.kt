package ephyra.feature.browse.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import ephyra.core.common.util.system.LocaleHelper
import ephyra.domain.manga.model.Manga
import ephyra.feature.browse.presentation.components.GlobalSearchCardRow
import ephyra.feature.browse.presentation.components.GlobalSearchErrorResultItem
import ephyra.feature.browse.presentation.components.GlobalSearchLoadingResultItem
import ephyra.feature.browse.presentation.components.GlobalSearchResultItem
import ephyra.feature.browse.presentation.components.GlobalSearchToolbar
import ephyra.feature.browse.source.globalsearch.SearchItemResult
import ephyra.feature.browse.source.globalsearch.SearchScreenModel
import ephyra.feature.browse.source.globalsearch.SourceFilter
import ephyra.presentation.core.components.material.Scaffold
import eu.kanade.tachiyomi.source.CatalogueSource

@Composable
fun GlobalSearchScreen(
    state: SearchScreenModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                hideSourceFilter = false,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        GlobalSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    items: Map<CatalogueSource, SearchItemResult>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    fromSourceId: Long? = null,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items.forEach { (source, result) ->
            item(key = source.id, contentType = "search-result") {
                GlobalSearchResultItem(
                    title = fromSourceId?.let {
                        "▶ ${source.name}".takeIf { source.id == fromSourceId }
                    } ?: source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (result) {
                        SearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }

                        is SearchItemResult.Success -> {
                            GlobalSearchCardRow(
                                titles = result.result,
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }

                        is SearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}
