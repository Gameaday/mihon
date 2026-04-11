package ephyra.feature.migration.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.LocaleHelper
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.feature.browse.migration.search.MigrateSearchScreen
import ephyra.feature.browse.presentation.components.SourceIcon
import ephyra.feature.migration.list.MigrationListScreen
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.FastScrollLazyColumn
import ephyra.presentation.core.components.Pill
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.shouldExpandFAB
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

class MigrationConfigScreen(private val mangaIds: Collection<Long>) : Screen() {

    constructor(mangaId: Long) : this(listOf(mangaId))

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<ScreenModel>()
        val state by screenModel.state.collectAsStateWithLifecycle()

        var migrationSheetOpen by rememberSaveable { mutableStateOf(false) }

        fun continueMigration(openSheet: Boolean, extraSearchQuery: String?) {
            val mangaId = mangaIds.singleOrNull()
            if (mangaId == null && openSheet) {
                migrationSheetOpen = true
                return
            }
            val screen = if (mangaId == null) {
                MigrationListScreen(mangaIds, extraSearchQuery)
            } else {
                MigrateSearchScreen(mangaId)
            }
            navigator.replace(screen)
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val (selectedSources, availableSources) = state.sources.partition { it.isSelected }
        val showLanguage by remember(state) {
            derivedStateOf {
                state.sources.distinctBy { it.source.lang }.size > 1
            }
        }

        val lazyListState = rememberLazyListState()
        Scaffold(
            topBar = {
                AppBar(
                    title = null,
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectAllLabel),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = { screenModel.toggleSelection(ScreenModel.SelectionConfig.All) },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectNoneLabel),
                                    icon = Icons.Outlined.Deselect,
                                    onClick = { screenModel.toggleSelection(ScreenModel.SelectionConfig.None) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectEnabledLabel),
                                    onClick = { screenModel.toggleSelection(ScreenModel.SelectionConfig.Enabled) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectPinnedLabel),
                                    onClick = { screenModel.toggleSelection(ScreenModel.SelectionConfig.Pinned) },
                                ),
                            ),
                        )
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.migrationConfigScreen_continueButtonText)) },
                    icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    onClick = {
                        screenModel.saveSources()
                        continueMigration(openSheet = true, extraSearchQuery = null)
                    },
                    expanded = lazyListState.shouldExpandFAB(),
                )
            },
        ) { contentPadding ->
            val reorderableState = rememberReorderableLazyListState(lazyListState, contentPadding) { from, to ->
                val fromIndex = selectedSources.indexOfFirst { it.id == from.key }
                val toIndex = selectedSources.indexOfFirst { it.id == to.key }
                if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState
                screenModel.orderSource(fromIndex, toIndex)
            }

            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = contentPadding,
            ) {
                listOf(selectedSources, availableSources).fastForEachIndexed { listIndex, sources ->
                    val selectedSourceList = listIndex == 0
                    if (sources.isNotEmpty()) {
                        val headerPrefix = if (selectedSourceList) "selected" else "available"
                        item("$headerPrefix-header") {
                            Text(
                                text = stringResource(
                                    resource = if (selectedSourceList) {
                                        MR.strings.migrationConfigScreen_selectedHeader
                                    } else {
                                        MR.strings.migrationConfigScreen_availableHeader
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(MaterialTheme.padding.medium)
                                    .animateItem(),
                            )
                        }
                    }
                    itemsIndexed(
                        items = sources,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        SourceItemContainer(
                            firstItem = index == 0,
                            lastItem = index == (sources.size - 1),
                            source = item,
                            showLanguage = showLanguage,
                            dragEnabled = selectedSourceList && sources.size > 1,
                            state = reorderableState,
                            key = { if (selectedSourceList) it.id else "available-${it.id}" },
                            onClick = { screenModel.toggleSelection(item.id) },
                        )
                    }
                }
            }
        }

        if (migrationSheetOpen) {
            MigrationConfigScreenSheet(
                preferences = screenModel.sourcePreferences,
                onDismissRequest = { migrationSheetOpen = false },
                onStartMigration = { extraSearchQuery ->
                    migrationSheetOpen = false
                    continueMigration(openSheet = false, extraSearchQuery = extraSearchQuery)
                },
            )
        }
    }

    @Composable
    private fun LazyItemScope.SourceItemContainer(
        firstItem: Boolean,
        lastItem: Boolean,
        source: MigrationSource,
        showLanguage: Boolean,
        dragEnabled: Boolean,
        state: ReorderableLazyListState,
        key: (MigrationSource) -> Any,
        onClick: () -> Unit,
    ) {
        val shape = remember(firstItem, lastItem) {
            val top = if (firstItem) 12.dp else 0.dp
            val bottom = if (lastItem) 12.dp else 0.dp
            RoundedCornerShape(top, top, bottom, bottom)
        }

        ReorderableItem(
            state = state,
            key = key(source),
            enabled = dragEnabled,
        ) { _ ->
            ElevatedCard(
                shape = shape,
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium)
                    .animateItem(),
            ) {
                SourceItem(
                    source = source,
                    showLanguage = showLanguage,
                    dragEnabled = dragEnabled,
                    scope = this@ReorderableItem,
                    onClick = onClick,
                )
            }
        }

        if (!lastItem) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium))
        }
    }

    @Composable
    private fun SourceItem(
        source: MigrationSource,
        showLanguage: Boolean,
        dragEnabled: Boolean,
        scope: ReorderableCollectionItemScope,
        onClick: () -> Unit,
    ) {
        ListItem(
            headlineContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceIcon(source = source.source)
                    Text(
                        text = source.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (showLanguage) {
                        Pill(
                            text = LocaleHelper.getShortDisplayName(source.shortLanguage, uppercase = true),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            trailingContent = if (dragEnabled) {
                {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = null,
                        modifier = with(scope) {
                            Modifier.draggableHandle()
                        },
                    )
                }
            } else {
                null
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            modifier = Modifier.clickable(onClick = onClick),
        )
    }

    internal class ScreenModel(
        val sourcePreferences: SourcePreferences,
        private val sourceManager: SourceManager,
    ) : StateScreenModel<ScreenModel.State>(State()) {

        private val sourcesComparator = { includedSources: List<Long> ->
            val rankMap = includedSources.withIndex().associate { (i, id) -> id to i }
            compareBy<MigrationSource>(
                { !it.isSelected },
                { rankMap.getOrDefault(it.id, Int.MAX_VALUE) },
                { with(it) { "$name ($shortLanguage)" } },
            )
        }

        init {
            screenModelScope.launchIO {
                initSources()
                mutableState.update { it.copy(isLoading = false) }
            }
        }

        private fun updateSources(action: (List<MigrationSource>) -> List<MigrationSource>) {
            mutableState.update { state ->
                val updatedSources = action(state.sources)
                val includedSources = updatedSources.mapNotNull { it.id.takeIf { _ -> it.isSelected } }
                state.copy(sources = updatedSources.sortedWith(sourcesComparator(includedSources)))
            }
            saveSources()
        }

        private suspend fun initSources() {
            val languages = sourcePreferences.enabledLanguages().get()
            val pinnedSources = sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() }
            val includedSources = sourcePreferences.migrationSources().get()
            val disabledSources = sourcePreferences.disabledSources().get()
                .mapNotNull { it.toLongOrNull() }
            val sources = sourceManager.getCatalogueSources()
                .asSequence()
                .filterIsInstance<HttpSource>()
                .filter { it.lang in languages }
                .map {
                    val source = Source(
                        id = it.id,
                        lang = it.lang,
                        name = it.name,
                        supportsLatest = false,
                        isStub = false,
                    )
                    MigrationSource(
                        source = source,
                        isSelected = when {
                            includedSources.isNotEmpty() -> source.id in includedSources
                            pinnedSources.isNotEmpty() -> source.id in pinnedSources
                            else -> source.id !in disabledSources
                        },
                    )
                }
                .toList()

            mutableState.update { state ->
                state.copy(sources = sources.sortedWith(sourcesComparator(includedSources)))
            }
        }

        fun toggleSelection(id: Long) {
            updateSources { sources ->
                sources.map { source ->
                    source.copy(isSelected = if (source.source.id == id) !source.isSelected else source.isSelected)
                }
            }
        }

        fun toggleSelection(config: SelectionConfig) {
            val pinnedSources = runBlocking { sourcePreferences.pinnedSources().get() }.mapNotNull { it.toLongOrNull() }
            val disabledSources = runBlocking {
                sourcePreferences.disabledSources().get()
            }.mapNotNull { it.toLongOrNull() }
            val isSelected: (Long) -> Boolean = {
                when (config) {
                    SelectionConfig.All -> true
                    SelectionConfig.None -> false
                    SelectionConfig.Pinned -> it in pinnedSources
                    SelectionConfig.Enabled -> it !in disabledSources
                }
            }
            updateSources { sources ->
                sources.map { source ->
                    source.copy(isSelected = isSelected(source.source.id))
                }
            }
        }

        fun orderSource(from: Int, to: Int) {
            updateSources {
                it.toMutableList()
                    .apply {
                        add(to, removeAt(from))
                    }
                    .toList()
            }
        }

        fun saveSources() {
            state.value.sources
                .filter { source -> source.isSelected }
                .map { source -> source.source.id }
                .let { sources -> sourcePreferences.migrationSources().set(sources) }
        }

        data class State(
            val isLoading: Boolean = true,
            val sources: List<MigrationSource> = emptyList(),
        )

        enum class SelectionConfig {
            All,
            None,
            Pinned,
            Enabled,
        }
    }

    data class MigrationSource(
        val source: Source,
        val isSelected: Boolean,
    ) {
        val id: Long
            inline get() = source.id

        val name: String
            inline get() = source.name

        val shortLanguage: String = LocaleHelper.getShortDisplayName(source.lang)
    }
}
