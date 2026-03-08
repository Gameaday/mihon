package eu.kanade.tachiyomi.ui.browse.source.authority

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.interactor.MatchUnlinkedManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.CanonicalId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Screen model for the authority match results screen.
 * Shows the outcome of a bulk matching run — linked manga and still-unlinked items
 * that can be retried individually.
 */
class MatchResultsScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val matchUnlinkedManga: MatchUnlinkedManga = Injekt.get(),
) : StateScreenModel<MatchResultsState>(MatchResultsState()) {

    init {
        loadManga()
    }

    private fun loadManga() {
        screenModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true)
            try {
                val favorites = withIOContext { mangaRepository.getFavorites() }
                val unlinked = favorites
                    .filter { it.canonicalId == null }
                    .sortedBy { it.title }
                    .toImmutableList()
                val linked = favorites
                    .filter { it.canonicalId != null }
                    .sortedByDescending { it.lastModifiedAt }
                    .take(MAX_RECENTLY_LINKED)
                    .toImmutableList()
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    unlinkedManga = unlinked,
                    recentlyLinked = linked,
                    totalFavorites = favorites.size,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load manga for match results" }
                mutableState.value = mutableState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Retry matching a single unlinked manga.
     * Disabled while bulk retry is in progress to avoid conflicting operations.
     */
    fun retrySingle(manga: Manga) {
        if (manga.id in mutableState.value.matchingIds) return
        if (mutableState.value.isRetryingAll) return
        screenModelScope.launch {
            mutableState.value = mutableState.value.copy(
                matchingIds = mutableState.value.matchingIds + manga.id,
                failedIds = mutableState.value.failedIds - manga.id,
            )
            try {
                val canonicalId = withIOContext { matchUnlinkedManga.awaitSingle(manga) }
                if (canonicalId != null) {
                    // Reload to move from unlinked to linked
                    loadManga()
                } else {
                    mutableState.value = mutableState.value.copy(
                        matchingIds = mutableState.value.matchingIds - manga.id,
                        failedIds = mutableState.value.failedIds + manga.id,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to match '${manga.title}'" }
                mutableState.value = mutableState.value.copy(
                    matchingIds = mutableState.value.matchingIds - manga.id,
                    failedIds = mutableState.value.failedIds + manga.id,
                )
            }
        }
    }

    /**
     * Retry all unlinked manga via the bulk matcher.
     */
    fun retryAll() {
        if (mutableState.value.isRetryingAll) return
        screenModelScope.launch {
            mutableState.value = mutableState.value.copy(isRetryingAll = true)
            try {
                withIOContext {
                    matchUnlinkedManga.await { current, total ->
                        mutableState.value = mutableState.value.copy(
                            retryAllProgress = current to total,
                        )
                    }
                }
                loadManga()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Retry all failed" }
            } finally {
                mutableState.value = mutableState.value.copy(
                    isRetryingAll = false,
                    retryAllProgress = null,
                    matchingIds = emptySet(),
                    failedIds = emptySet(),
                )
            }
        }
    }

    companion object {
        private const val MAX_RECENTLY_LINKED = 50
    }
}

data class MatchResultsState(
    val isLoading: Boolean = true,
    val unlinkedManga: ImmutableList<Manga> = persistentListOf(),
    val recentlyLinked: ImmutableList<Manga> = persistentListOf(),
    val totalFavorites: Int = 0,
    /** IDs of manga currently being matched individually. */
    val matchingIds: Set<Long> = emptySet(),
    /** IDs of manga that failed to match. */
    val failedIds: Set<Long> = emptySet(),
    /** True when bulk retry is in progress. */
    val isRetryingAll: Boolean = false,
    /** (current, total) for bulk retry progress. */
    val retryAllProgress: Pair<Int, Int>? = null,
) {
    val totalLinked: Int get() = totalFavorites - unlinkedManga.size
}

/**
 * Simple display info for a linked manga's authority.
 */
data class AuthorityInfo(
    val label: String,
    val url: String?,
) {
    companion object {
        fun from(canonicalId: String?): AuthorityInfo? {
            if (canonicalId == null) return null
            val label = CanonicalId.toLabel(canonicalId) ?: return null
            val url = CanonicalId.toUrl(canonicalId)
            return AuthorityInfo(label, url)
        }
    }
}
