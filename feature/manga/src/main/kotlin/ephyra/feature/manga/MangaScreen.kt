package ephyra.feature.manga

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinInject
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.data.cache.CoverCache
import ephyra.presentation.core.util.system.toast
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.model.toSManga
import ephyra.feature.manga.notes.MangaNotesScreen
import ephyra.feature.manga.presentation.ChapterSettingsDialog
import ephyra.feature.manga.presentation.DuplicateMangaDialog
import ephyra.feature.manga.presentation.EditCoverAction
import ephyra.feature.manga.presentation.MangaScreen
import ephyra.feature.manga.presentation.components.CoverSearchDialog
import ephyra.feature.manga.presentation.components.DeleteChaptersDialog
import ephyra.feature.manga.presentation.components.EditMetadataDialog
import ephyra.feature.manga.presentation.components.MangaCoverDialog
import ephyra.feature.manga.presentation.components.ScanlatorFilterDialog
import ephyra.feature.manga.presentation.components.SetIntervalDialog
import ephyra.feature.manga.track.TrackInfoDialogHomeScreen
import ephyra.feature.migration.config.MigrationConfigScreen
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.presentation.category.components.ChangeCategoryDialog
import ephyra.presentation.components.NavigatorAdaptiveSheet
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.AssistContentScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.isTabletUi
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.parameter.parametersOf

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current

        val basePreferences = koinInject<BasePreferences>()
        val coverCache = koinInject<CoverCache>()

        val screenModel = koinScreenModel<MangaScreenModel> {
            parametersOf(lifecycleOwner.lifecycle, mangaId, fromSource)
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is MangaScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenModel.State.Success
        val isHttpSource = remember { successState.source is HttpSource }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = successState.chapterSwipeStartAction,
            chapterSwipeEndAction = successState.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { openChapter(context, it) },
            onDownloadChapter = if (!successState.source.isLocalOrStub()) {
                { items, action -> screenModel.onEvent(MangaScreenEvent.RunChapterDownloadActions(items, action)) }
            } else null,
            onAddToLibraryClicked = {
                screenModel.onEvent(MangaScreenEvent.ToggleFavorite())
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = if (isHttpSource) {
                {
                    openMangaInWebView(
                        navigator,
                        screenModel.manga,
                        screenModel.source,
                    )
                }
            } else null,
            onWebViewLongClicked = if (isHttpSource) {
                {
                    copyMangaUrl(
                        context,
                        screenModel.manga,
                        screenModel.source,
                    )
                }
            } else null,
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.onEvent(MangaScreenEvent.ShowTrackDialog)
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = { screenModel.onEvent(MangaScreenEvent.ShowSettingsDialog) },
            onRefresh = { screenModel.onEvent(MangaScreenEvent.FetchAllFromSource(manualFetch = true)) },
            onContinueReading = { continueReading(context, screenModel.getNextUnreadChapter()) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = { screenModel.onEvent(MangaScreenEvent.ShowCoverDialog) },
            onShareClicked = if (isHttpSource) {
                { shareManga(context, screenModel.manga, screenModel.source) }
            } else null,
            onDownloadActionClicked = if (!successState.source.isLocalOrStub()) {
                { screenModel.onEvent(MangaScreenEvent.RunDownloadAction(it)) }
            } else null,
            onEditCategoryClicked = if (successState.manga.favorite) {
                { screenModel.onEvent(MangaScreenEvent.ShowChangeCategoryDialog) }
            } else null,
            onEditFetchIntervalClicked = if (successState.manga.favorite) {
                { screenModel.onEvent(MangaScreenEvent.ShowSetFetchIntervalDialog) }
            } else null,
            onMigrateClicked = if (successState.manga.favorite) {
                { navigator.push(MigrationConfigScreen(successState.manga.id)) }
            } else null,
            onEditNotesClicked = { navigator.push(MangaNotesScreen(manga = successState.manga)) },
            onEditMetadataClicked = if (successState.manga.favorite || successState.manga.canonicalId != null) {
                { screenModel.onEvent(MangaScreenEvent.ShowEditMetadataDialog) }
            } else null,
            onMultiBookmarkClicked = { ch, b -> screenModel.onEvent(MangaScreenEvent.BookmarkChapters(ch, b)) },
            onMultiMarkAsReadClicked = { ch, b -> screenModel.onEvent(MangaScreenEvent.MarkChaptersRead(ch, b)) },
            onMarkPreviousAsReadClicked = { screenModel.onEvent(MangaScreenEvent.MarkPreviousChapterRead(it)) },
            onMultiDeleteClicked = { screenModel.onEvent(MangaScreenEvent.ShowDeleteChapterDialog(it)) },
            onChapterSwipe = { ch, sw -> screenModel.onEvent(MangaScreenEvent.ChapterSwipe(ch, sw)) },
            onChapterSelected = { item, selected, fromLongPress ->
                screenModel.onEvent(
                    MangaScreenEvent.ToggleSelection(
                        item,
                        selected,
                        fromLongPress,
                    ),
                )
            },
            onAllChapterSelected = { screenModel.onEvent(MangaScreenEvent.ToggleAllSelection(it)) },
            onInvertSelection = { screenModel.onEvent(MangaScreenEvent.InvertSelection) },
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = { screenModel.onEvent(MangaScreenEvent.DismissDialog) }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.onEvent(
                            MangaScreenEvent.MoveMangaToCategoriesAndAddToLibrary(
                                dialog.manga,
                                include,
                            ),
                        )
                    },
                )
            }

            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.onEvent(MangaScreenEvent.ToggleAllSelection(false))
                        screenModel.onEvent(MangaScreenEvent.DeleteChapters(dialog.chapters))
                    },
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.onEvent(MangaScreenEvent.ToggleFavorite(checkDuplicate = false)) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.onEvent(MangaScreenEvent.ShowMigrateDialog(it)) },
                )
            }

            is MangaScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }

            MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                basePreferences = basePreferences,
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = { screenModel.onEvent(MangaScreenEvent.SetDownloadedFilter(it)) },
                onUnreadFilterChanged = { screenModel.onEvent(MangaScreenEvent.SetUnreadFilter(it)) },
                onBookmarkedFilterChanged = { screenModel.onEvent(MangaScreenEvent.SetBookmarkedFilter(it)) },
                onSortModeChanged = { screenModel.onEvent(MangaScreenEvent.SetSorting(it)) },
                onDisplayModeChanged = { screenModel.onEvent(MangaScreenEvent.SetDisplayMode(it)) },
                onSetAsDefault = { screenModel.onEvent(MangaScreenEvent.SetCurrentSettingsAsDefault(it)) },
                onResetToDefault = { screenModel.onEvent(MangaScreenEvent.ResetToDefaultSettings) },
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )

            MangaScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                        canonicalId = successState.manga.canonicalId,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }

            MangaScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { MangaCoverScreenModel(successState.manga.id) }
                val manga by sm.state.collectAsStateWithLifecycle()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    var showCoverSearch by remember { mutableStateOf(false) }
                    if (showCoverSearch) {
                        val coverSearchSm = rememberScreenModel {
                            CoverSearchScreenModel(
                                mangaTitle = manga!!.title,
                                currentSourceId = successState.source.id,
                            )
                        }
                        val coverSearchState by coverSearchSm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { coverSearchSm.search() }
                        CoverSearchDialog(
                            state = coverSearchState,
                            onCoverSelected = { cover ->
                                sm.setCoverFromUrl(context, cover.thumbnailUrl, cover.sourceId)
                                showCoverSearch = false
                            },
                            onSetAsMetadataSource = { cover ->
                                screenModel.onEvent(MangaScreenEvent.SetMetadataSource(cover.sourceId, cover.mangaUrl))
                                showCoverSearch = false
                            },
                            onRefresh = { coverSearchSm.refresh() },
                            onDismissRequest = { showCoverSearch = false },
                        )
                    } else {
                        MangaCoverDialog(
                            manga = manga!!,
                            snackbarHostState = sm.snackbarHostState,
                            isCustomCover = remember(manga) { manga!!.hasCustomCover(coverCache) },
                            onShareClick = { sm.shareCover(context) },
                            onSaveClick = { sm.saveCover(context) },
                            onEditClick = {
                                when (it) {
                                    EditCoverAction.EDIT -> getContent.launch("image/*")
                                    EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                                    EditCoverAction.SEARCH -> {
                                        showCoverSearch = true
                                    }
                                }
                            },
                            onDismissRequest = onDismissRequest,
                        )
                    }
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }

            is MangaScreenModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int ->
                        screenModel.onEvent(
                            MangaScreenEvent.SetFetchInterval(
                                dialog.manga,
                                interval,
                            ),
                        )
                    }
                        .takeIf { successState.isUpdateIntervalEnabled },
                )
            }

            MangaScreenModel.Dialog.EditMetadata -> {
                val manga = successState.manga
                val authorityLabel = remember(manga.canonicalId) {
                    manga.canonicalId?.let { ephyra.domain.manga.model.CanonicalId.toLabel(it) }
                }
                EditMetadataDialog(
                    title = manga.title,
                    author = manga.author,
                    artist = manga.artist,
                    description = manga.description,
                    status = manga.status,
                    genres = manga.genre ?: emptyList(),
                    lockedFields = manga.lockedFields,
                    hasAuthority = manga.canonicalId != null,
                    authorityLabel = authorityLabel,
                    onSaveTitle = { screenModel.onEvent(MangaScreenEvent.EditTitle(it)) },
                    onSaveAuthor = { screenModel.onEvent(MangaScreenEvent.EditAuthor(it)) },
                    onSaveArtist = { screenModel.onEvent(MangaScreenEvent.EditArtist(it)) },
                    onSaveDescription = { screenModel.onEvent(MangaScreenEvent.EditDescription(it)) },
                    onSaveStatus = { screenModel.onEvent(MangaScreenEvent.EditStatus(it)) },
                    onSaveGenres = { screenModel.onEvent(MangaScreenEvent.EditGenres(it)) },
                    onToggleLock = { screenModel.onEvent(MangaScreenEvent.ToggleLockedField(it)) },
                    onSetAllLocks = { mask -> screenModel.onEvent(MangaScreenEvent.SetLockedFields(mask)) },
                    onIdentify = if (manga.canonicalId == null) {
                        {
                            screenModel.onEvent(MangaScreenEvent.DismissDialog)
                            screenModel.onEvent(MangaScreenEvent.ResolveCanonicalId)
                        }
                    } else {
                        {
                            screenModel.onEvent(MangaScreenEvent.DismissDialog)
                            screenModel.onEvent(MangaScreenEvent.RefreshFromAuthority)
                        }
                    },
                    onUnlinkAuthority = if (manga.canonicalId != null) {
                        {
                            screenModel.onEvent(MangaScreenEvent.DismissDialog)
                            screenModel.onEvent(MangaScreenEvent.UnlinkAuthority)
                        }
                    } else {
                        null
                    },
                    onDismissRequest = onDismissRequest,
                )
            }
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = { screenModel.onEvent(MangaScreenEvent.SetExcludedScanlators(it)) },
            )
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: Source?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                previousController.search(query)
            }

            is BrowseSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: Source) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }
}
