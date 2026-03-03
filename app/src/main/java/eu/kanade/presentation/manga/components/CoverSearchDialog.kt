package eu.kanade.presentation.manga.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.CoverResult
import eu.kanade.tachiyomi.ui.manga.CoverSearchScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CoverSearchDialog(
    state: CoverSearchScreenModel.State,
    onCoverSelected: (CoverResult) -> Unit,
    onSetAsMetadataSource: ((CoverResult) -> Unit)?,
    onRefresh: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(text = stringResource(MR.strings.action_search_cover))
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(MR.strings.action_close),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !state.isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(MR.strings.action_webview_refresh),
                            )
                        }
                    },
                )
                if (state.isLoading && state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.progress / state.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { contentPadding ->
        if (state.results.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(MR.strings.no_results_found),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    top = contentPadding.calculateTopPadding() + MaterialTheme.padding.small,
                    bottom = contentPadding.calculateBottomPadding() + MaterialTheme.padding.small,
                ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(
                    items = state.results,
                    key = { it.thumbnailUrl },
                ) { cover ->
                    CoverSearchItem(
                        cover = cover,
                        onClick = { onCoverSelected(cover) },
                        onLongClick = onSetAsMetadataSource?.let { callback -> { callback(cover) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverSearchItem(
    cover: CoverResult,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onLongClick != null) {
                        { showMenu = true }
                    } else {
                        null
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = cover.thumbnailUrl,
                contentDescription = cover.mangaTitle,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
            Text(
                text = if (cover.sourceCount > 1) {
                    stringResource(MR.strings.cover_search_source_count, cover.sourceName, cover.sourceCount - 1)
                } else {
                    cover.sourceName
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
            )
            Text(
                text = cover.mangaTitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }
        if (onLongClick != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_set_as_cover)) },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_set_metadata_source)) },
                    onClick = {
                        showMenu = false
                        onLongClick()
                    },
                )
            }
        }
    }
}
