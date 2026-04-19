package ephyra.feature.browse.source.authority

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.CanonicalId
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.track.interactor.MatchUnlinkedManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.annotation.Factory

/**
 * Screen model for the authority match results screen.
 * Shows the outcome of a bulk matching run — linked manga and still-unlinked items
 * that can be retried individually.
 */
@Factory
class MatchResultsScreenModel(
    private val getFavorites: GetFavorites,
    private val matchUnlinkedManga: MatchUnlinkedManga,
) : StateScreenModel<MatchResultsState>(MatchResultsState()) {

    init {
        loadManga()
    }

    private fun loadManga() {
        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            try {
                val favorites = withIOContext { getFavorites.await() }
                val unlinked = favorites
                    .filter { it.canonicalId == null }
                    .sortedBy { it.title }
                    .toImmutableList()
                val linked = favorites
                    .filter { it.canonicalId != null }
                    .sortedByDescending { it.lastModifiedAt }
                    .take(MAX_RECENTLY_LINKED)
                    .toImmutableList()
                // Compute content type counts for the summary
                val mangaCount = favorites.count { it.contentType == ContentType.MANGA }
                val novelCount = favorites.count { it.contentType == ContentType.NOVEL }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        unlinkedManga = unlinked,
                        recentlyLinked = linked,
                        totalFavorites = favorites.size,
                        mangaCount = mangaCount,
                        novelCount = novelCount,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load manga for match results" }
                mutableState.update { it.copy(isLoading = false) }
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
            mutableState.update { state ->
                state.copy(
                    matchingIds = state.matchingIds + manga.id,
                    failedIds = state.failedIds - manga.id,
                )
            }
            try {
                val canonicalId = withIOContext { matchUnlinkedManga.awaitSingle(manga) }
                if (canonicalId != null) {
                    // Reload to move from unlinked to linked
                    loadManga()
                } else {
                    mutableState.update { state ->
                        state.copy(
                            matchingIds = state.matchingIds - manga.id,
                            failedIds = state.failedIds + manga.id,
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to match '${manga.title}'" }
                mutableState.update { state ->
                    state.copy(
                        matchingIds = state.matchingIds - manga.id,
                        failedIds = state.failedIds + manga.id,
                    )
                }
            }
        }
    }

    /**
     * Retry all unlinked manga via the bulk matcher.
     */
    fun retryAll() {
        if (mutableState.value.isRetryingAll) return
        screenModelScope.launch {
            mutableState.update { it.copy(isRetryingAll = true) }
            try {
                withIOContext {
                    matchUnlinkedManga.await { current, total ->
                        mutableState.update { it.copy(retryAllProgress = current to total) }
                    }
                }
                loadManga()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Retry all failed" }
            } finally {
                mutableState.update {
                    it.copy(
                        isRetryingAll = false,
                        retryAllProgress = null,
                        matchingIds = emptySet(),
                        failedIds = emptySet(),
                    )
                }
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
    val mangaCount: Int = 0,
    val novelCount: Int = 0,
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
