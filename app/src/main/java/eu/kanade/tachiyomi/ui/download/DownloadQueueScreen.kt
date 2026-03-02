package eu.kanade.tachiyomi.ui.download

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

object DownloadQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DownloadQueueScreenModel() }
        val downloadList by screenModel.state.collectAsState()
        val downloadCount by remember { derivedStateOf { downloadList.size } }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var fabExpanded by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    fabExpanded = available.y >= 0
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPreFling(available)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(MR.strings.label_download_queue),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, false),
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (downloadCount > 0) {
                                val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                                Pill(
                                    text = "$downloadCount",
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = MaterialTheme.colorScheme.onBackground
                                        .copy(alpha = pillAlpha),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        if (downloadList.isNotEmpty()) {
                            var sortExpanded by remember { mutableStateOf(false) }
                            val onDismissRequest = { sortExpanded = false }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = onDismissRequest,
                            ) {
                                NestedMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_order_by_upload_date)) },
                                    children = { closeMenu ->
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_newest)) },
                                            onClick = {
                                                screenModel.reorderQueue(
                                                    { it.chapter.dateUpload },
                                                    true,
                                                )
                                                closeMenu()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_oldest)) },
                                            onClick = {
                                                screenModel.reorderQueue(
                                                    { it.chapter.dateUpload },
                                                    false,
                                                )
                                                closeMenu()
                                            },
                                        )
                                    },
                                )
                                NestedMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_order_by_chapter_number)) },
                                    children = { closeMenu ->
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_asc)) },
                                            onClick = {
                                                screenModel.reorderQueue(
                                                    { it.chapter.chapterNumber },
                                                    false,
                                                )
                                                closeMenu()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_desc)) },
                                            onClick = {
                                                screenModel.reorderQueue(
                                                    { it.chapter.chapterNumber },
                                                    true,
                                                )
                                                closeMenu()
                                            },
                                        )
                                    },
                                )
                            }

                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_sort),
                                        icon = Icons.AutoMirrored.Outlined.Sort,
                                        onClick = { sortExpanded = true },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_cancel_all),
                                        onClick = { screenModel.clearQueue() },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                val isRunning by screenModel.isDownloaderRunning.collectAsState()
                SmallExtendedFloatingActionButton(
                    text = {
                        val id = if (isRunning) MR.strings.action_pause else MR.strings.action_resume
                        Text(text = stringResource(id))
                    },
                    icon = {
                        val icon = if (isRunning) Icons.Outlined.Pause else Icons.Filled.PlayArrow
                        Icon(imageVector = icon, contentDescription = null)
                    },
                    onClick = {
                        if (isRunning) {
                            screenModel.pauseDownloads()
                        } else {
                            screenModel.startDownloads()
                        }
                    },
                    expanded = fabExpanded,
                    modifier = Modifier.animateFloatingActionButton(
                        visible = downloadList.isNotEmpty(),
                        alignment = Alignment.BottomEnd,
                    ),
                )
            },
        ) { contentPadding ->
            if (downloadList.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.information_no_downloads,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            val listState = rememberLazyListState()

            // Flatten: for display, group by source with a header before each group
            val flatItems = remember(downloadList) {
                buildList {
                    downloadList
                        .groupBy { it.source.id }
                        .forEach { (_, group) ->
                            add(DownloadListDisplayItem.Header(group.first().source.name, group.size))
                            group.forEach { add(DownloadListDisplayItem.Item(it)) }
                        }
                }
            }

            // Only item rows are reorderable (not headers); track by chapter id
            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                // Only allow reordering item rows (not headers)
                val fromItem = flatItems.getOrNull(from.index) as? DownloadListDisplayItem.Item ?: return@rememberReorderableLazyListState
                val toItem = flatItems.getOrNull(to.index) as? DownloadListDisplayItem.Item ?: return@rememberReorderableLazyListState

                // Compute new queue order from current flat item list, with from/to swapped
                val downloads = flatItems
                    .filterIsInstance<DownloadListDisplayItem.Item>()
                    .map { it.download }
                    .toMutableList()
                val fromIdx = downloads.indexOf(fromItem.download)
                val toIdx = downloads.indexOf(toItem.download)
                if (fromIdx != -1 && toIdx != -1) {
                    downloads.add(toIdx, downloads.removeAt(fromIdx))
                    screenModel.reorder(downloads)
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.nestedScroll(nestedScrollConnection),
            ) {
                itemsIndexed(
                    items = flatItems,
                    key = { _, item ->
                        when (item) {
                            is DownloadListDisplayItem.Header -> "header_${item.sourceName}"
                            is DownloadListDisplayItem.Item -> item.download.chapter.id
                        }
                    },
                ) { index, displayItem ->
                    when (displayItem) {
                        is DownloadListDisplayItem.Header -> {
                            if (index > 0) HorizontalDivider()
                            Text(
                                text = "${displayItem.sourceName} (${displayItem.count})",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        is DownloadListDisplayItem.Item -> {
                            ReorderableItem(reorderableState, key = displayItem.download.chapter.id) { isDragging ->
                                DownloadQueueItem(
                                    download = displayItem.download,
                                    isDragging = isDragging,
                                    onMoveToTop = {
                                        val downloads = downloadList.toMutableList()
                                        val idx = downloads.indexOf(displayItem.download)
                                        if (idx > 0) {
                                            downloads.add(0, downloads.removeAt(idx))
                                            screenModel.reorder(downloads)
                                        }
                                    },
                                    onMoveToBottom = {
                                        val downloads = downloadList.toMutableList()
                                        val idx = downloads.indexOf(displayItem.download)
                                        if (idx < downloads.lastIndex) {
                                            downloads.add(downloads.removeAt(idx))
                                            screenModel.reorder(downloads)
                                        }
                                    },
                                    onMoveSeriesTop = {
                                        val mangaId = displayItem.download.manga.id
                                        val (series, others) = downloadList.partition { it.manga.id == mangaId }
                                        screenModel.reorder(series + others)
                                    },
                                    onMoveSeriesBottom = {
                                        val mangaId = displayItem.download.manga.id
                                        val (series, others) = downloadList.partition { it.manga.id == mangaId }
                                        screenModel.reorder(others + series)
                                    },
                                    onCancel = { screenModel.cancel(listOf(displayItem.download)) },
                                    onCancelSeries = {
                                        val mangaId = displayItem.download.manga.id
                                        screenModel.cancel(downloadList.filter { it.manga.id == mangaId })
                                    },
                                    dragHandle = {
                                        IconButton(
                                            onClick = { /* Drag handle — touch events handled by reorderable */ },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DragHandle,
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface DownloadListDisplayItem {
    data class Header(val sourceName: String, val count: Int) : DownloadListDisplayItem
    data class Item(val download: Download) : DownloadListDisplayItem
}

@Composable
private fun DownloadQueueItem(
    download: Download,
    isDragging: Boolean,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onMoveSeriesTop: () -> Unit,
    onMoveSeriesBottom: () -> Unit,
    onCancel: () -> Unit,
    onCancelSeries: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val progress by download.progressFlow.collectAsState(download.progress)
    val pages = download.pages
    val downloadedImages = download.downloadedImages

    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dragHandle()

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = download.manga.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = download.chapter.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pages != null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }

        if (pages != null) {
            Text(
                text = "$downloadedImages/${pages.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_top)) },
                    onClick = { onMoveToTop(); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_top_all_for_series)) },
                    onClick = { onMoveSeriesTop(); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_bottom)) },
                    onClick = { onMoveToBottom(); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_bottom_all_for_series)) },
                    onClick = { onMoveSeriesBottom(); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_cancel)) },
                    onClick = { onCancel(); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.cancel_all_for_series)) },
                    onClick = { onCancelSeries(); menuExpanded = false },
                )
            }
        }
    }
}
