package ephyra.feature.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.Constants
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.source.model.StubSource
import ephyra.feature.browse.extension.details.SourcePreferencesScreen
import ephyra.feature.browse.presentation.BrowseSourceContent
import ephyra.feature.browse.presentation.MissingSourceScreen
import ephyra.feature.browse.presentation.components.BrowseSourceToolbar
import ephyra.feature.browse.presentation.components.RemoveMangaDialog
import ephyra.feature.browse.source.browse.BrowseSourceScreenModel.Listing
import ephyra.feature.category.CategoryScreen
import ephyra.feature.category.components.ChangeCategoryDialog
import ephyra.feature.manga.MangaScreen
import ephyra.feature.manga.presentation.DuplicateMangaDialog
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.feature.webview.WebViewScreen
import ephyra.i18n.MR
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.SearchableScreen
import ephyra.presentation.core.util.AssistContentScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.collectAsLazyPagingItems
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.parameter.parametersOf

data class BrowseSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen(), AssistContentScreen, SearchableScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = koinScreenModel<BrowseSourceScreenModel> { parametersOf(sourceId, listingQuery) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null ->
                    screenModel.onEvent(BrowseSourceScreenEvent.SetToolbarQuery(null))
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? HttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.baseUrl
        }

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .pointerInput(Unit) {},
                ) {
                    BrowseSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = { screenModel.onEvent(BrowseSourceScreenEvent.SetToolbarQuery(it)) },
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(SourcePreferencesScreen(sourceId)) },
                        onSearch = { screenModel.onEvent(BrowseSourceScreenEvent.Search(it)) },
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.onEvent(BrowseSourceScreenEvent.ResetFilters)
                                screenModel.onEvent(BrowseSourceScreenEvent.SetListing(Listing.Popular))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as CatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.onEvent(BrowseSourceScreenEvent.ResetFilters)
                                    screenModel.onEvent(BrowseSourceScreenEvent.SetListing(Listing.Latest))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = { screenModel.onEvent(BrowseSourceScreenEvent.OpenFilterSheet) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = onHelpClick,
                onMangaClick = { navigator.push((MangaScreen(it.id, true))) },
                onMangaLongClick = { manga ->
                    scope.launchIO {
                        val duplicates = screenModel.getDuplicateLibraryManga(manga)
                        when {
                            manga.favorite ->
                                screenModel.onEvent(
                                    BrowseSourceScreenEvent.SetDialog(
                                        BrowseSourceScreenModel.Dialog.RemoveManga(manga),
                                    ),
                                )
                            duplicates.isNotEmpty() ->
                                screenModel.onEvent(
                                    BrowseSourceScreenEvent.SetDialog(
                                        BrowseSourceScreenModel.Dialog.AddDuplicateManga(manga, duplicates),
                                    ),
                                )
                            else -> screenModel.onEvent(BrowseSourceScreenEvent.AddFavorite(manga))
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.onEvent(BrowseSourceScreenEvent.SetDialog(null)) }
        when (val dialog = state.dialog) {
            is BrowseSourceScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = { screenModel.onEvent(BrowseSourceScreenEvent.ResetFilters) },
                    onFilter = { screenModel.onEvent(BrowseSourceScreenEvent.Search(filters = state.filters)) },
                    onUpdate = { screenModel.onEvent(BrowseSourceScreenEvent.SetFilters(it)) },
                )
            }

            is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.onEvent(BrowseSourceScreenEvent.AddFavorite(dialog.manga)) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = {
                        screenModel.onEvent(
                            BrowseSourceScreenEvent.SetDialog(BrowseSourceScreenModel.Dialog.Migrate(dialog.manga, it)),
                        )
                    },
                )
            }

            is BrowseSourceScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }

            is BrowseSourceScreenModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.onEvent(BrowseSourceScreenEvent.ChangeMangaFavorite(dialog.manga))
                    },
                    mangaToRemove = dialog.manga,
                )
            }

            is BrowseSourceScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.onEvent(BrowseSourceScreenEvent.ChangeMangaFavorite(dialog.manga))
                        screenModel.onEvent(BrowseSourceScreenEvent.MoveMangaToCategories(dialog.manga, include))
                    },
                )
            }

            else -> {}
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.onEvent(BrowseSourceScreenEvent.SearchGenre(it.txt))
                        is SearchType.Text -> screenModel.onEvent(BrowseSourceScreenEvent.Search(it.txt))
                    }
                }
        }
    }

    override suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    override suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
