package ephyra.feature.browse.source.authority

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.mergedAlternativeTitles
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.annotation.Factory

@Factory
class AuthoritySearchScreenModel(
    private val trackerManager: TrackerManager,
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
    private val insertTrack: InsertTrack,
    private val generateAuthorityChapters: GenerateAuthorityChapters,
    private val findContentSource: FindContentSource,
) : StateScreenModel<AuthoritySearchState>(AuthoritySearchState()) {

    private var searchJob: Job? = null

    init {
        screenModelScope.launch {
            val trackers = withIOContext {
                trackerManager.getAll(AddTracks.TRACKER_CANONICAL_PREFIXES.keys)
                    .filter {
                        AddTracks.TRACKER_CANONICAL_PREFIXES.containsKey(it.id) &&
                            (it.isLoggedIn() || it.id in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH)
                    }
                    .toImmutableList()
            }
            mutableState.update { state ->
                state.copy(
                    availableTrackers = trackers,
                    selectedTracker = state.selectedTracker ?: trackers.firstOrNull(),
                )
            }
        }
    }

    /**
     * Trackers filtered by the current content type selection.
     * When "All" is selected, returns all available trackers.
     * When a specific type is selected, returns only trackers that are authorities
     * for that type — saving API calls by not querying irrelevant services.
     */
    fun trackersForFilter(contentType: ContentType): ImmutableList<Tracker> {
        val available = mutableState.value.availableTrackers
        if (contentType == ContentType.UNKNOWN) return available
        val validIds = AddTracks.trackersForContentType(contentType)
        return available.filter { it.id in validIds }.toImmutableList()
    }

    // ── UDF entry-point ──────────────────────────────────────────────────────
    fun onEvent(event: AuthoritySearchScreenEvent) {
        when (event) {
            is AuthoritySearchScreenEvent.SelectTracker -> selectTracker(event.tracker)
            is AuthoritySearchScreenEvent.SetContentTypeFilter -> setContentTypeFilter(event.contentType)
            is AuthoritySearchScreenEvent.Search -> search(event.query)
            is AuthoritySearchScreenEvent.AddToLibrary -> addToLibrary(event.result)
            is AuthoritySearchScreenEvent.MergeWithExisting -> mergeWithExisting(event.candidate)
            is AuthoritySearchScreenEvent.SkipMerge -> skipMerge()
            is AuthoritySearchScreenEvent.DismissMergePrompt -> dismissMergePrompt()
            is AuthoritySearchScreenEvent.DismissSourcePrompt -> dismissSourcePrompt()
            is AuthoritySearchScreenEvent.SelectResult -> selectResult(event.result)
            is AuthoritySearchScreenEvent.DismissDetail -> dismissDetail()
            is AuthoritySearchScreenEvent.RetrySearch -> retrySearch()
        }
    }

    private fun selectTracker(tracker: Tracker) {
        mutableState.update {
            it.copy(
                selectedTracker = tracker,
                results = persistentListOf(),
                query = "",
            )
        }
    }

    /**
     * Sets the content type filter, which controls:
     * 1. Which trackers are shown as available (only authorities for that type)
     * 2. Which search results are displayed (client-side filtering)
     *
     * When the filter changes, if the currently selected tracker doesn't support
     * the new type, auto-selects the first tracker that does.
     * [ContentType.UNKNOWN] means "All types" (no filtering).
     */
    private fun setContentTypeFilter(contentType: ContentType) {
        val filteredTrackers = trackersForFilter(contentType)
        val currentTracker = mutableState.value.selectedTracker

        // If current tracker doesn't support the new type, switch to first valid one
        val newTracker = if (currentTracker != null && currentTracker in filteredTrackers) {
            currentTracker
        } else {
            filteredTrackers.firstOrNull()
        }

        mutableState.update {
            it.copy(
                contentTypeFilter = contentType,
                selectedTracker = newTracker,
                // Clear results when switching type — old results may not be relevant
                results = persistentListOf(),
            )
        }
    }

    private fun search(query: String) {
        val tracker = mutableState.value.selectedTracker ?: return
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                query = query,
                isSearching = true,
                results = persistentListOf(),
                searchError = null,
            )
        }
        searchJob = screenModelScope.launch {
            try {
                val results = withIOContext { tracker.search(query) }
                mutableState.update {
                    it.copy(
                        results = results.toImmutableList(),
                        isSearching = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Authority search failed: query=$query" }
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        results = persistentListOf(),
                        searchError = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    /**
     * Adds a search result to the library as an authority-only manga entry.
     * Before creating a new entry, checks for existing library manga without a canonical ID
     * that match by title. If found, prompts the user to merge instead.
     * If the tracker is logged in, also creates a tracker binding.
     */
    private fun addToLibrary(result: TrackSearch) {
        val tracker = mutableState.value.selectedTracker ?: return
        val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[tracker.id] ?: return
        val canonicalId = "$prefix:${result.remote_id}"

        // Track loading state for this specific result
        mutableState.update { it.copy(addingCanonicalIds = it.addingCanonicalIds + canonicalId) }

        screenModelScope.launch {
            try {
                withIOContext {
                    // Reuse the same dedup logic as TrackerListImporter
                    val existingByCanonical = getFavoritesByCanonicalId.await(canonicalId, -1L)
                    if (existingByCanonical.isNotEmpty()) {
                        // Already in library — just mark as added in UI
                        mutableState.update { state ->
                            state.copy(
                                addedCanonicalIds = state.addedCanonicalIds + canonicalId,
                                addingCanonicalIds = state.addingCanonicalIds - canonicalId,
                            )
                        }
                        return@withIOContext
                    }

                    // Check for unpaired library manga (no canonical ID) that match by title.
                    // If found, prompt the user to merge with existing content first.
                    val unpairedMatches = getDuplicateLibraryManga.invoke(result.title)
                        .filter { it.manga.canonicalId == null }
                    if (unpairedMatches.isNotEmpty()) {
                        mutableState.update { state ->
                            state.copy(
                                mergePrompt = MergePromptInfo(
                                    result = result,
                                    canonicalId = canonicalId,
                                    tracker = tracker,
                                    candidates = unpairedMatches.toImmutableList(),
                                ),
                                addingCanonicalIds = state.addingCanonicalIds - canonicalId,
                            )
                        }
                        return@withIOContext
                    }

                    // No existing matches — create new authority entry
                    createAuthorityEntry(result, tracker, canonicalId)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to add authority manga: canonical_id=$canonicalId" }
            } finally {
                // Ensure loading state is cleared even on error
                mutableState.update { it.copy(addingCanonicalIds = it.addingCanonicalIds - canonicalId) }
            }
        }
    }

    /**
     * Merges a discover result with an existing unpaired library manga by assigning
     * the canonical ID to the existing entry. Also enriches the manga with authoritative
     * metadata (description, author, cover, alt titles) from the tracker result,
     * only filling fields that are currently missing.
     */
    private fun mergeWithExisting(candidate: MangaWithChapterCount) {
        val prompt = mutableState.value.mergePrompt ?: return
        screenModelScope.launch {
            try {
                withIOContext {
                    // Assign canonical ID and enrich missing metadata from the authoritative result
                    val result = prompt.result
                    val manga = candidate.manga
                    updateManga.await(
                        MangaUpdate(
                            id = manga.id,
                            canonicalId = prompt.canonicalId,
                            description = result.summary.takeIf {
                                it.isNotBlank() && manga.description.isNullOrBlank()
                            },
                            author = result.authors.joinToString(", ").takeIf {
                                it.isNotBlank() && manga.author.isNullOrBlank()
                            },
                            artist = result.artists.joinToString(", ").takeIf {
                                it.isNotBlank() && manga.artist.isNullOrBlank()
                            },
                            thumbnailUrl = result.cover_url.takeIf {
                                it.isNotBlank() && manga.thumbnailUrl.isNullOrBlank()
                            },
                            contentType = ContentType.fromPublishingType(result.publishing_type).takeIf {
                                it != ContentType.UNKNOWN && manga.contentType == ContentType.UNKNOWN
                            },
                        ),
                    )
                    logcat(LogPriority.INFO) {
                        "Merged '${manga.title}' with canonical_id=${prompt.canonicalId}"
                    }

                    // Merge alternative titles from the authoritative result
                    mergeAlternativeTitles(manga, result)

                    // Bind the tracker if logged in
                    if (prompt.tracker.isLoggedIn()) {
                        val track = Track(
                            id = 0L,
                            mangaId = manga.id,
                            trackerId = prompt.tracker.id,
                            remoteId = result.remote_id,
                            libraryId = null,
                            title = result.title,
                            lastChapterRead = result.last_chapter_read,
                            totalChapters = result.total_chapters,
                            status = result.status,
                            score = result.score,
                            remoteUrl = result.tracking_url,
                            startDate = result.started_reading_date,
                            finishDate = result.finished_reading_date,
                            isPrivate = result.isPrivate,
                        )
                        insertTrack.await(track)
                    }

                    mutableState.update { state ->
                        state.copy(
                            addedCanonicalIds = state.addedCanonicalIds + prompt.canonicalId,
                            mergePrompt = null,
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to merge with existing manga" }
                mutableState.update { it.copy(mergePrompt = null) }
            }
        }
    }

    /**
     * User declined to merge — create a new authority entry instead and proceed
     * to the find-source prompt.
     */
    private fun skipMerge() {
        val prompt = mutableState.value.mergePrompt ?: return
        mutableState.update { it.copy(mergePrompt = null) }
        screenModelScope.launch {
            try {
                withIOContext {
                    createAuthorityEntry(prompt.result, prompt.tracker, prompt.canonicalId)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create authority entry after skip merge" }
            }
        }
    }

    /** Dismiss the merge prompt without doing anything. */
    private fun dismissMergePrompt() {
        mutableState.update { it.copy(mergePrompt = null) }
    }

    /**
     * Creates the authority-only manga entry, optionally binds tracker, generates chapters,
     * then triggers the source-pairing prompt.
     */
    private suspend fun createAuthorityEntry(
        result: TrackSearch,
        tracker: Tracker,
        canonicalId: String,
    ) {
        val existingByUrl = getMangaByUrlAndSourceId.await(
            canonicalId,
            TrackerListImporter.AUTHORITY_SOURCE_ID,
        )

        val manga = if (existingByUrl != null) {
            existingByUrl
        } else {
            val newManga = Manga.create().copy(
                url = canonicalId,
                title = result.title,
                source = TrackerListImporter.AUTHORITY_SOURCE_ID,
                thumbnailUrl = result.cover_url.ifBlank { null },
                artist = result.artists.joinToString(", ").ifBlank { null },
                author = result.authors.joinToString(", ").ifBlank { null },
                description = result.summary.ifBlank { null },
                initialized = true,
            )
            val inserted = networkToLocalManga(listOf(newManga))
            inserted.firstOrNull() ?: return
        }

        // Mark as favourite and set content type from publishing_type
        val inferredType = ContentType.fromPublishingType(result.publishing_type)
        updateManga.await(
            MangaUpdate(
                id = manga.id,
                favorite = true,
                dateAdded = System.currentTimeMillis(),
                canonicalId = canonicalId,
                contentType = inferredType.takeIf { it != ContentType.UNKNOWN },
            ),
        )

        // Bind the tracker only if user is logged in
        if (tracker.isLoggedIn()) {
            val track = Track(
                id = 0L,
                mangaId = manga.id,
                trackerId = tracker.id,
                remoteId = result.remote_id,
                libraryId = null,
                title = result.title,
                lastChapterRead = 0.0,
                totalChapters = result.total_chapters,
                status = 0L,
                score = 0.0,
                remoteUrl = result.tracking_url,
                startDate = 0L,
                finishDate = 0L,
                isPrivate = result.isPrivate,
            )
            insertTrack.await(track)
        }

        // Generate authority chapters so the user can mark progress
        if (result.total_chapters > 0) {
            generateAuthorityChapters.await(
                mangaId = manga.id,
                totalChapters = result.total_chapters.toInt(),
                lastChapterRead = 0,
            )
        }

        mutableState.update { state ->
            state.copy(
                addedCanonicalIds = state.addedCanonicalIds + canonicalId,
                sourcePromptManga = SourcePromptInfo(
                    title = result.title,
                    mangaId = manga.id,
                    isSearching = true,
                ),
            )
        }

        // Automatically search for content sources in the background
        autoSearchForSources(manga)
    }

    /**
     * Automatically searches enabled content sources for a matching manga.
     * Updates the source prompt with results as they come in.
     */
    private fun autoSearchForSources(manga: Manga) {
        screenModelScope.launch {
            try {
                val matches = withIOContext {
                    findContentSource.findSources(manga, maxResults = 5, deepSearch = false)
                }
                val currentPrompt = mutableState.value.sourcePromptManga ?: return@launch
                if (currentPrompt.mangaId == manga.id) {
                    mutableState.update {
                        it.copy(
                            sourcePromptManga = currentPrompt.copy(
                                sourceMatches = matches.toImmutableList(),
                                isSearching = false,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Auto-search for sources failed" }
                val currentPrompt = mutableState.value.sourcePromptManga ?: return@launch
                if (currentPrompt.mangaId == manga.id) {
                    mutableState.update {
                        it.copy(sourcePromptManga = currentPrompt.copy(isSearching = false))
                    }
                }
            }
        }
    }

    /** Dismiss the source prompt dialog. */
    private fun dismissSourcePrompt() {
        mutableState.update { it.copy(sourcePromptManga = null) }
    }

    /** User tapped a result card to view full details. */
    private fun selectResult(result: TrackSearch) {
        mutableState.update { it.copy(selectedResult = result) }
    }

    /** Dismiss the detail view. */
    private fun dismissDetail() {
        mutableState.update { it.copy(selectedResult = null) }
    }

    /** Retry the last failed search. */
    private fun retrySearch() {
        val query = mutableState.value.query
        if (query.isNotBlank()) search(query)
    }

    /**
     * Merges alternative titles from a tracker result into the manga's existing list.
     * Also adds the tracker's title as an alternative if it differs from the primary title.
     */
    private suspend fun mergeAlternativeTitles(manga: Manga, result: TrackSearch) {
        val newTitles = buildList {
            if (result.title.isNotBlank()) add(result.title)
            addAll(result.alternative_titles)
        }
        val merged = manga.mergedAlternativeTitles(newTitles) ?: return
        updateManga.await(
            MangaUpdate(id = manga.id, alternativeTitles = merged),
        )
    }
}

data class AuthoritySearchState(
    val selectedTracker: Tracker? = null,
    /** All trackers available for authority search, populated asynchronously on IO. */
    val availableTrackers: ImmutableList<Tracker> = persistentListOf(),
    val query: String = "",
    val isSearching: Boolean = false,
    val results: ImmutableList<TrackSearch> = persistentListOf(),
    /** Content type filter — [ContentType.UNKNOWN] means "All types" (no filtering). */
    val contentTypeFilter: ContentType = ContentType.UNKNOWN,
    /** Canonical IDs of manga already added to the library in this session. */
    val addedCanonicalIds: Set<String> = emptySet(),
    /** Canonical IDs currently being added (for loading indicator on add button). */
    val addingCanonicalIds: Set<String> = emptySet(),
    /** Non-null when the last search resulted in an error (for retry UI). */
    val searchError: String? = null,
    /** Non-null when the user just added a manga and should be prompted to find a source. */
    val sourcePromptManga: SourcePromptInfo? = null,
    /** Non-null when unpaired library manga match the title — user should merge or skip. */
    val mergePrompt: MergePromptInfo? = null,
    /** Non-null when user tapped a result to view full details. */
    val selectedResult: TrackSearch? = null,
) {
    /**
     * Search results filtered by the active content type filter.
     * When filter is [ContentType.UNKNOWN] (All), returns all results unfiltered.
     * Otherwise, only returns results whose publishing_type maps to the selected content type.
     */
    val filteredResults: ImmutableList<TrackSearch>
        get() = if (contentTypeFilter == ContentType.UNKNOWN) {
            results
        } else {
            results.filter {
                val resultType = ContentType.fromPublishingType(it.publishing_type)
                resultType == contentTypeFilter || resultType == ContentType.UNKNOWN
            }.toImmutableList()
        }
}

/** Info for the "find content source?" prompt shown after adding authority manga. */
data class SourcePromptInfo(
    val title: String,
    val mangaId: Long,
    /** Auto-search results from FindContentSource, populated asynchronously. */
    val sourceMatches: ImmutableList<FindContentSource.SourceMatch> = persistentListOf(),
    /** True while auto-search is in progress. */
    val isSearching: Boolean = true,
)

/** Info for the "merge with existing?" prompt shown when library has unpaired matches. */
data class MergePromptInfo(
    val result: TrackSearch,
    val canonicalId: String,
    val tracker: Tracker,
    val candidates: ImmutableList<MangaWithChapterCount>,
)
