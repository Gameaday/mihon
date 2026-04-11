package ephyra.feature.browse.source.authority

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.model.TrackSearch
import ephyra.feature.browse.source.globalsearch.GlobalSearchScreen
import ephyra.i18n.MR
import ephyra.presentation.core.components.AdaptiveSheet
import ephyra.presentation.core.components.ScrollbarLazyColumn
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.theme.MotionTokens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

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
fun VoyagerScreen.discoverTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<AuthoritySearchScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = MR.strings.label_search,
        actions = persistentListOf(),
        content = { contentPadding, _ ->
            DiscoverContent(
                state = state,
                trackersForFilter = screenModel::trackersForFilter,
                onSelectTracker = screenModel::selectTracker,
                onSearch = screenModel::search,
                onRetrySearch = screenModel::retrySearch,
                onAddToLibrary = screenModel::addToLibrary,
                onSelectResult = screenModel::selectResult,
                onSetContentTypeFilter = screenModel::setContentTypeFilter,
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
                            ephyra.feature.browse.source.browse.BrowseSourceScreen(
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
    trackersForFilter: (ContentType) -> ImmutableList<ephyra.domain.track.service.Tracker>,
    onSelectTracker: (ephyra.domain.track.service.Tracker) -> Unit,
    onSearch: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onAddToLibrary: (TrackSearch) -> Unit,
    onSelectResult: (TrackSearch) -> Unit,
    onSetContentTypeFilter: (ContentType) -> Unit,
    contentPadding: PaddingValues,
) {
    // All trackers = unfiltered list — use to check if any are available at all
    val allTrackers = trackersForFilter(ContentType.UNKNOWN)
    if (allTrackers.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.discover_no_trackers,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    // Trackers filtered by the selected content type
    val filteredTrackers = trackersForFilter(state.contentTypeFilter)

    val focusManager = LocalFocusManager.current
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
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch(query)
                    focusManager.clearFocus()
                },
            ),
        )

        // Content type filter chips — always visible, controls which trackers are shown.
        // This organizes authorities by what they are an authority of, so only
        // relevant trackers are queried — saving API calls as more authorities are added.
        val typeFilters = remember {
            listOf(ContentType.UNKNOWN, ContentType.MANGA, ContentType.NOVEL)
        }
        val typeFilterLabels = mapOf(
            ContentType.UNKNOWN to stringResource(MR.strings.discover_filter_all),
            ContentType.MANGA to stringResource(MR.strings.discover_filter_manga),
            ContentType.NOVEL to stringResource(MR.strings.discover_filter_novel),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.extraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(typeFilters.size) { index ->
                val type = typeFilters[index]
                val isSelected = state.contentTypeFilter == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetContentTypeFilter(type) },
                    label = { Text(typeFilterLabels[type] ?: "") },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        // Tracker filter chips — filtered by the selected content type.
        // Only shown when multiple trackers match the current type.
        // Uses LazyRow for horizontal scrolling if many trackers are available.
        if (filteredTrackers.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.extraSmall,
                ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(filteredTrackers.size) { index ->
                    val tracker = filteredTrackers[index]
                    val isSelected = tracker == state.selectedTracker
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTracker(tracker) },
                        label = { Text(tracker.name) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // Result count indicator — shows how many results are displayed
        val displayResults = state.filteredResults
        if (displayResults.isNotEmpty()) {
            val countText = if (
                state.contentTypeFilter != ContentType.UNKNOWN &&
                displayResults.size != state.results.size
            ) {
                stringResource(
                    MR.strings.discover_result_count_filtered,
                    displayResults.size,
                    state.results.size,
                )
            } else {
                stringResource(MR.strings.discover_result_count, displayResults.size)
            }
            Text(
                text = countText,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.extraSmall,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Animated content area — search results or landing state
        // Derive a stable display state to avoid unnecessary transitions
        val displayState = when {
            state.isSearching -> DiscoverDisplayState.LOADING
            state.searchError != null -> DiscoverDisplayState.ERROR
            state.results.isEmpty() && state.query.isBlank() -> DiscoverDisplayState.LANDING
            displayResults.isEmpty() -> DiscoverDisplayState.NO_RESULTS
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

                DiscoverDisplayState.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(MaterialTheme.padding.medium))
                        Text(
                            text = stringResource(MR.strings.discover_search_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(MaterialTheme.padding.medium))
                        Button(onClick = onRetrySearch) {
                            Text(stringResource(MR.strings.discover_retry))
                        }
                    }
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
                            displayResults,
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
                            val isAdding = canonicalId != null &&
                                canonicalId in state.addingCanonicalIds
                            DiscoverResultCard(
                                result = result,
                                isAdded = isAdded,
                                isAdding = isAdding,
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
    isAdding: Boolean,
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
                    .clip(MaterialTheme.shapes.extraSmall),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Type + status metadata row for disambiguation
                val metaItems = buildList {
                    if (result.publishing_type.isNotBlank()) add(result.publishing_type)
                    if (result.publishing_status.isNotBlank()) add(result.publishing_status)
                    if (result.start_date.isNotBlank()) add(result.start_date)
                }
                if (metaItems.isNotEmpty()) {
                    Text(
                        text = metaItems.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                enabled = !isAdded && !isAdding,
                colors = if (isAdded) {
                    IconButtonDefaults.iconButtonColors(
                        disabledContentColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
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
                        .clip(MaterialTheme.shapes.small),
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
                                        .clip(MaterialTheme.shapes.extraSmall),
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
                                        val chapterText = when {
                                            match.chapterCount > 0 -> stringResource(
                                                MR.strings.discover_find_source_chapters,
                                                match.chapterCount,
                                            )

                                            match.chapterCount == 0 -> stringResource(
                                                MR.strings.discover_find_source_no_chapters,
                                            )

                                            else -> null
                                        }
                                        if (chapterText != null) {
                                            Text(
                                                text = chapterText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (match.chapterCount > 0) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                            )
                                        }
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
private enum class DiscoverDisplayState { LOADING, LANDING, NO_RESULTS, RESULTS, ERROR }
