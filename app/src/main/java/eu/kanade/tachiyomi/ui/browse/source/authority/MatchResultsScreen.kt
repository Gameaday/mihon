package eu.kanade.tachiyomi.ui.browse.source.authority

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Screen showing the results of authority matching.
 * Displays recently linked manga and still-unlinked items with retry actions.
 */
class MatchResultsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MatchResultsScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.match_results_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state.isLoading) {
                LoadingScreen()
                return@Scaffold
            }

            MatchResultsContent(
                state = state,
                onRetrySingle = screenModel::retrySingle,
                onRetryAll = screenModel::retryAll,
                onOpenManga = { manga ->
                    navigator.push(MangaScreen(manga.id))
                },
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun MatchResultsContent(
    state: MatchResultsState,
    onRetrySingle: (Manga) -> Unit,
    onRetryAll: () -> Unit,
    onOpenManga: (Manga) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + MaterialTheme.padding.small,
            bottom = contentPadding.calculateBottomPadding() + MaterialTheme.padding.small,
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        // Summary card
        item(key = "summary") {
            SummaryCard(
                totalLinked = state.totalLinked,
                totalFavorites = state.totalFavorites,
                unlinkedCount = state.unlinkedManga.size,
            )
        }

        // Retry all button + progress
        if (state.unlinkedManga.isNotEmpty()) {
            item(key = "retry_all") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.isRetryingAll) {
                        val progress = state.retryAllProgress
                        if (progress != null && progress.second > 0) {
                            LinearProgressIndicator(
                                progress = { progress.first.toFloat() / progress.second },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MaterialTheme.padding.small),
                            )
                            Text(
                                text = stringResource(
                                    MR.strings.tracker_match_all_running_progress,
                                    progress.first,
                                    progress.second,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MaterialTheme.padding.small),
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onRetryAll,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                            Text(text = stringResource(MR.strings.match_results_retry_all))
                        }
                    }
                }
            }

            // Unlinked header
            item(key = "unlinked_header") {
                SectionHeader(
                    text = stringResource(MR.strings.match_results_unlinked_header),
                    count = state.unlinkedManga.size,
                )
            }

            // Unlinked manga items
            items(state.unlinkedManga, key = { "unlinked_${it.id}" }) { manga ->
                UnlinkedMangaItem(
                    manga = manga,
                    isMatching = manga.id in state.matchingIds,
                    hasFailed = manga.id in state.failedIds,
                    isRetryEnabled = !state.isRetryingAll,
                    onRetry = { onRetrySingle(manga) },
                    onClick = { onOpenManga(manga) },
                )
            }
        }

        // Recently linked section
        if (state.recentlyLinked.isNotEmpty()) {
            item(key = "linked_header") {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                SectionHeader(
                    text = stringResource(MR.strings.match_results_linked_header),
                    count = state.recentlyLinked.size,
                )
            }

            items(state.recentlyLinked, key = { "linked_${it.id}" }) { manga ->
                LinkedMangaItem(
                    manga = manga,
                    onClick = { onOpenManga(manga) },
                    onOpenAuthority = { url -> context.openInBrowser(url) },
                )
            }
        }

        // All linked state
        if (state.unlinkedManga.isEmpty() && state.recentlyLinked.isEmpty()) {
            item(key = "all_linked") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
                        Text(
                            text = stringResource(MR.strings.match_results_all_linked),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalLinked: Int,
    totalFavorites: Int,
    unlinkedCount: Int,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
        ) {
            Text(
                text = stringResource(MR.strings.match_results_summary, totalLinked, totalFavorites),
                style = MaterialTheme.typography.titleMedium,
            )
            if (totalFavorites > 0) {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                LinearProgressIndicator(
                    progress = {
                        if (totalFavorites > 0) totalLinked.toFloat() / totalFavorites else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (unlinkedCount > 0) {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))
                Text(
                    text = stringResource(MR.strings.match_results_unlinked_count, unlinkedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, count: Int) {
    Text(
        text = "$text ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
    )
}

@Composable
private fun UnlinkedMangaItem(
    manga: Manga,
    isMatching: Boolean,
    hasFailed: Boolean,
    isRetryEnabled: Boolean,
    onRetry: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                modifier = Modifier
                    .size(40.dp, 56.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(MR.strings.match_results_no_match),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (isMatching) {
                    Text(
                        text = stringResource(MR.strings.match_results_matching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isMatching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = onRetry,
                    enabled = isRetryEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(MR.strings.match_results_retry_single),
                        tint = if (isRetryEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedMangaItem(
    manga: Manga,
    onClick: () -> Unit,
    onOpenAuthority: (String) -> Unit,
) {
    val authorityInfo = remember(manga.canonicalId) {
        AuthorityInfo.from(manga.canonicalId)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                modifier = Modifier
                    .size(40.dp, 56.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (authorityInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = authorityInfo.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (authorityInfo?.url != null) {
                IconButton(onClick = { onOpenAuthority(authorityInfo.url) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
