package eu.kanade.tachiyomi.ui.browse.source.authority

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.manga.interactor.FindContentSource
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.MotionTokens

/**
 * Creates the Search sub-tab inside the top-level Discover tab.
 *
 * This is the authority-first search experience: users search tracker databases
 * (MAL, AniList, MangaUpdates) and add results directly to their library.
 * The search is one part of the broader Discover flow — the tab is designed
 * to accommodate future discovery features (suggestions, recommendations)
 * alongside the search.
 */
@Composable
fun Screen.discoverTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AuthoritySearchScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_search,
        actions = persistentListOf(),
        content = { contentPadding, _ ->
            DiscoverContent(
                state = state,
                availableTrackers = screenModel.availableTrackers,
                onSelectTracker = screenModel::selectTracker,
                onSearch = screenModel::search,
                onAddToLibrary = screenModel::addToLibrary,
                onSelectResult = screenModel::selectResult,
                contentPadding = contentPadding,
            )

            // Detail sheet for viewing full result metadata
            val selectedResult = state.selectedResult
            if (selectedResult != null) {
                val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[selectedResult.tracker_id]
                val canonicalId = if (prefix != null) "$prefix:${selectedResult.remote_id}" else null
                val isAdded = canonicalId != null && canonicalId in state.addedCanonicalIds
                DiscoverDetailSheet(
                    result = selectedResult,
                    isAdded = isAdded,
                    onAdd = {
                        screenModel.addToLibrary(selectedResult)
                        screenModel.dismissDetail()
                    },
                    onDismiss = screenModel::dismissDetail,
                )
            }

            // "Find content source?" prompt shown after adding an authority manga
            val sourcePrompt = state.sourcePromptManga
            if (sourcePrompt != null) {
                FindSourceDialog(
                    mangaTitle = sourcePrompt.title,
                    sourceMatches = sourcePrompt.sourceMatches,
                    isSearching = sourcePrompt.isSearching,
                    onSelectSource = { match ->
                        screenModel.dismissSourcePrompt()
                        navigator.push(
                            eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen(
                                match.sourceId,
                                match.manga.title,
                            ),
                        )
                    },
                    onManualSearch = {
                        screenModel.dismissSourcePrompt()
                        navigator.push(GlobalSearchScreen(sourcePrompt.title))
                    },
                    onDismiss = screenModel::dismissSourcePrompt,
                )
            }

            // "Merge with existing?" prompt when library has unpaired matches
            val mergePrompt = state.mergePrompt
            if (mergePrompt != null) {
                MergeWithExistingDialog(
                    resultTitle = mergePrompt.result.title,
                    candidates = mergePrompt.candidates,
                    onMerge = screenModel::mergeWithExisting,
                    onSkip = screenModel::skipMerge,
                    onDismiss = screenModel::dismissMergePrompt,
                )
            }
        },
    )
}

@Composable
private fun DiscoverContent(
    state: AuthoritySearchState,
    availableTrackers: List<eu.kanade.tachiyomi.data.track.Tracker>,
    onSelectTracker: (eu.kanade.tachiyomi.data.track.Tracker) -> Unit,
    onSearch: (String) -> Unit,
    onAddToLibrary: (TrackSearch) -> Unit,
    onSelectResult: (TrackSearch) -> Unit,
    contentPadding: PaddingValues,
) {
    if (availableTrackers.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.discover_no_trackers,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Search bar — rounded pill shape, Material 3 Expressive
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            placeholder = { Text(stringResource(MR.strings.discover_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )

        // Tracker filter chips
        if (availableTrackers.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.extraSmall,
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                availableTrackers.forEach { tracker ->
                    FilterChip(
                        selected = tracker == state.selectedTracker,
                        onClick = { onSelectTracker(tracker) },
                        label = { Text(tracker.name) },
                    )
                }
            }
        }

        // Animated content area — search results or landing state
        // Derive a stable display state to avoid unnecessary transitions
        val displayState = when {
            state.isSearching -> DiscoverDisplayState.LOADING
            state.results.isEmpty() && state.query.isBlank() -> DiscoverDisplayState.LANDING
            state.results.isEmpty() -> DiscoverDisplayState.NO_RESULTS
            else -> DiscoverDisplayState.RESULTS
        }
        AnimatedContent(
            targetState = displayState,
            transitionSpec = {
                fadeIn(tween(MotionTokens.DURATION_MEDIUM)) togetherWith
                    fadeOut(tween(MotionTokens.DURATION_SHORT))
            },
            label = "discover_results",
            modifier = Modifier.weight(1f),
        ) { currentDisplayState ->
            when (currentDisplayState) {
                DiscoverDisplayState.LOADING -> LoadingScreen()
                DiscoverDisplayState.LANDING -> {
                    EmptyScreen(stringResource(MR.strings.discover_empty_state))
                }
                DiscoverDisplayState.NO_RESULTS -> {
                    EmptyScreen(stringResource(MR.strings.no_results_found))
                }
                DiscoverDisplayState.RESULTS -> {
                    ScrollbarLazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.padding.small,
                        ),
                    ) {
                        items(
                            state.results,
                            key = { "${it.tracker_id}:${it.remote_id}" },
                        ) { result ->
                            val prefix =
                                AddTracks.TRACKER_CANONICAL_PREFIXES[result.tracker_id]
                            val canonicalId = if (prefix != null) {
                                "$prefix:${result.remote_id}"
                            } else {
                                null
                            }
                            val isAdded = canonicalId != null &&
                                canonicalId in state.addedCanonicalIds
                            DiscoverResultCard(
                                result = result,
                                isAdded = isAdded,
                                onAdd = { onAddToLibrary(result) },
                                onClick = { onSelectResult(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverResultCard(
    result: TrackSearch,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            AsyncImage(
                model = result.cover_url,
                contentDescription = result.title,
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.publishing_type.isNotBlank()) {
                    Text(
                        text = result.publishing_type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (result.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(MaterialTheme.padding.small))
            IconButton(
                onClick = onAdd,
                enabled = !isAdded,
                colors = if (isAdded) {
                    IconButtonDefaults.iconButtonColors(
                        disabledContentColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
            ) {
                Icon(
                    imageVector = if (isAdded) {
                        Icons.Outlined.Check
                    } else {
                        Icons.Outlined.Add
                    },
                    contentDescription = stringResource(
                        if (isAdded) {
                            MR.strings.discover_added
                        } else {
                            MR.strings.discover_add
                        },
                    ),
                )
            }
        }
    }
}

/**
 * Full-detail sheet shown when tapping a Discover search result.
 * Displays all authoritative metadata from the tracker: cover, title, description,
 * author/artist, status, chapters, publishing type, start date, and alternative titles.
 */
@Composable
private fun DiscoverDetailSheet(
    result: TrackSearch,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.medium,
                ),
        ) {
            // Cover + title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                AsyncImage(
                    model = result.cover_url,
                    contentDescription = result.title,
                    modifier = Modifier
                        .size(120.dp, 170.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val authors = result.authors.joinToString(", ")
                    if (authors.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(MR.strings.discover_detail_author),
                            value = authors,
                        )
                    }
                    val artists = result.artists.joinToString(", ")
                    if (artists.isNotBlank() && artists != authors) {
                        DetailLabel(
                            label = stringResource(MR.strings.discover_detail_artist),
                            value = artists,
                        )
                    }
                    if (result.publishing_status.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(MR.strings.discover_detail_status),
                            value = result.publishing_status,
                        )
                    }
                    if (result.publishing_type.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(MR.strings.discover_detail_type),
                            value = result.publishing_type,
                        )
                    }
                    if (result.start_date.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(MR.strings.discover_detail_start_date),
                            value = result.start_date,
                        )
                    }
                    if (result.total_chapters > 0) {
                        DetailLabel(
                            label = stringResource(MR.strings.discover_detail_chapters),
                            value = result.total_chapters.toString(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(MaterialTheme.padding.medium))

            // Add to library button
            Button(
                onClick = onAdd,
                enabled = !isAdded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (isAdded) MR.strings.discover_added else MR.strings.discover_add,
                    ),
                )
            }

            // Description
            if (result.summary.isNotBlank()) {
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                HorizontalDivider()
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                SelectionContainer {
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Alternative titles
            if (result.alternative_titles.isNotEmpty()) {
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                HorizontalDivider()
                Spacer(Modifier.height(MaterialTheme.padding.small))
                Text(
                    text = stringResource(MR.strings.discover_detail_alt_titles),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                result.alternative_titles.forEach { altTitle ->
                    Text(
                        text = "• $altTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(MaterialTheme.padding.medium))
        }
    }
}

/** Small label + value row for the detail view metadata section. */
@Composable
private fun DetailLabel(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Dialog shown when the user adds a manga from Discover and existing unpaired library
 * entries (without canonical IDs) match the title. The user can select one to merge
 * the canonical ID into, or skip to create a separate authority entry.
 */
@Composable
private fun MergeWithExistingDialog(
    resultTitle: String,
    candidates: List<MangaWithChapterCount>,
    onMerge: (MangaWithChapterCount) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.discover_merge_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(MR.strings.discover_merge_message, resultTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(candidates, key = { it.manga.id }) { candidate ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMerge(candidate) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.padding.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                            ) {
                                AsyncImage(
                                    model = candidate.manga.thumbnailUrl,
                                    contentDescription = candidate.manga.title,
                                    modifier = Modifier
                                        .size(40.dp, 56.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.manga.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(
                                            MR.strings.discover_merge_chapters,
                                            candidate.chapterCount,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(MR.strings.discover_merge_skip))
            }
        },
    )
}

/**
 * Dialog prompting the user to find a content source after adding an authority manga.
 * Shows auto-search results when available, with option for manual search fallback.
 * This bridges the authority-first model with source pairing: manga exist by their
 * canonical identity first, and a content source is an optional addition on top.
 */
@Composable
private fun FindSourceDialog(
    mangaTitle: String,
    sourceMatches: List<FindContentSource.SourceMatch>,
    isSearching: Boolean,
    onSelectSource: (FindContentSource.SourceMatch) -> Unit,
    onManualSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.discover_find_source_title)) },
        text = {
            Column {
                if (isSearching) {
                    Text(
                        text = stringResource(MR.strings.discover_find_source_searching),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(MaterialTheme.padding.medium))
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (sourceMatches.isNotEmpty()) {
                    Text(
                        text = stringResource(MR.strings.discover_find_source_found, mangaTitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(MaterialTheme.padding.medium))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(
                            sourceMatches,
                            key = { it.sourceId },
                        ) { match ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSource(match) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(MaterialTheme.padding.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        MaterialTheme.padding.medium,
                                    ),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = match.sourceName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = match.manga.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val confidencePercent = (match.confidence * 100).toInt()
                                    Text(
                                        text = "$confidencePercent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (match.confidence >= 0.9) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(MR.strings.discover_find_source_message, mangaTitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManualSearch) {
                Text(stringResource(MR.strings.discover_find_source_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.discover_find_source_skip))
            }
        },
    )
}

/** Display states for the animated content area — avoids Triple allocations. */
private enum class DiscoverDisplayState { LOADING, LANDING, NO_RESULTS, RESULTS }
