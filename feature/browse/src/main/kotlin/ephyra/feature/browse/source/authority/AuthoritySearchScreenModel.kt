package ephyra.feature.browse.source.authority

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.mergedAlternativeTitles
import ephyra.domain.manga.repository.MangaRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.annotation.Factory

@Factory
class AuthoritySearchScreenModel(
    private val trackerManager: TrackerManager,
    private val mangaRepository: MangaRepository,
    private val insertTrack: InsertTrack,
    private val generateAuthorityChapters: GenerateAuthorityChapters,
    private val findContentSource: FindContentSource,
) : StateScreenModel<AuthoritySearchState>(AuthoritySearchState()) {

    private var searchJob: Job? = null

    /**
     * All trackers available for authority search (regardless of content type).
     * Includes logged-in authoritative trackers AND trackers with public search APIs.
     * This allows discovery to work without login for trackers like MangaUpdates.
     * Initialized asynchronously on IO to avoid blocking the main thread on DataStore reads.
     */
    private val allTrackers = MutableStateFlow<ImmutableList<Tracker>>(persistentListOf())

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
            allTrackers.value = trackers
            if (trackers.isNotEmpty()) {
                mutableState.update { state ->
                    state.copy(selectedTracker = state.selectedTracker ?: trackers.first())
                }
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
        if (contentType == ContentType.UNKNOWN) return allTrackers.value
        val validIds = AddTracks.trackersForContentType(contentType)
        return allTrackers.value.filter { it.id in validIds }.toImmutableList()
    }

    fun selectTracker(tracker: Tracker) {
        mutableState.value = mutableState.value.copy(
            selectedTracker = tracker,
            results = persistentListOf(),
            query = "",
        )
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
    fun setContentTypeFilter(contentType: ContentType) {
        val filteredTrackers = trackersForFilter(contentType)
        val currentTracker = mutableState.value.selectedTracker

        // If current tracker doesn't support the new type, switch to first valid one
        val newTracker = if (currentTracker != null && currentTracker in filteredTrackers) {
            currentTracker
        } else {
            filteredTrackers.firstOrNull()
        }

        mutableState.value = mutableState.value.copy(
            contentTypeFilter = contentType,
            selectedTracker = newTracker,
            // Clear results when switching type — old results may not be relevant
            results = persistentListOf(),
        )
    }

    fun search(query: String) {
        val tracker = mutableState.value.selectedTracker ?: return
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            query = query,
            isSearching = true,
            results = persistentListOf(),
            searchError = null,
        )
        searchJob = screenModelScope.launch {
            try {
                val results = withIOContext { tracker.search(query) }
                mutableState.value = mutableState.value.copy(
                    results = results.toImmutableList(),
                    isSearching = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Authority search failed: query=$query" }
                mutableState.value = mutableState.value.copy(
                    isSearching = false,
                    results = persistentListOf(),
                    searchError = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    /**
     * Adds a search result to the library as an authority-only manga entry.
     * Before creating a new entry, checks for existing library manga without a canonical ID
     * that match by title. If found, prompts the user to merge instead.
     * If the tracker is logged in, also creates a tracker binding.
     */
    fun addToLibrary(result: TrackSearch) {
        val tracker = mutableState.value.selectedTracker ?: return
        val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[tracker.id] ?: return
        val canonicalId = "$prefix:${result.remote_id}"

        // Track loading state for this specific result
        mutableState.value = mutableState.value.copy(
            addingCanonicalIds = mutableState.value.addingCanonicalIds + canonicalId,
        )

        screenModelScope.launch {
            try {
                withIOContext {
                    // Reuse the same dedup logic as TrackerListImporter
                    val existingByCanonical = mangaRepository.getFavoritesByCanonicalId(canonicalId, -1L)
                    if (existingByCanonical.isNotEmpty()) {
                        // Already in library — just mark as added in UI
                        mutableState.value = mutableState.value.copy(
                            addedCanonicalIds = mutableState.value.addedCanonicalIds + canonicalId,
                            addingCanonicalIds = mutableState.value.addingCanonicalIds - canonicalId,
                        )
                        return@withIOContext
                    }

                    // Check for unpaired library manga (no canonical ID) that match by title.
                    // If found, prompt the user to merge with existing content first.
                    val unpairedMatches = mangaRepository.getDuplicateLibraryManga(-1L, result.title.lowercase())
                        .filter { it.manga.canonicalId == null }
                    if (unpairedMatches.isNotEmpty()) {
                        mutableState.value = mutableState.value.copy(
                            mergePrompt = MergePromptInfo(
                                result = result,
                                canonicalId = canonicalId,
                                tracker = tracker,
                                candidates = unpairedMatches.toImmutableList(),
                            ),
                            addingCanonicalIds = mutableState.value.addingCanonicalIds - canonicalId,
                        )
                        return@withIOContext
                    }

                    // No existing matches — create new authority entry
                    createAuthorityEntry(result, tracker, canonicalId)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to add authority manga: canonical_id=$canonicalId" }
            } finally {
                // Ensure loading state is cleared even on error
                mutableState.value = mutableState.value.copy(
                    addingCanonicalIds = mutableState.value.addingCanonicalIds - canonicalId,
                )
            }
        }
    }

    /**
     * Merges a discover result with an existing unpaired library manga by assigning
     * the canonical ID to the existing entry. Also enriches the manga with authoritative
     * metadata (description, author, cover, alt titles) from the tracker result,
     * only filling fields that are currently missing.
     */
    fun mergeWithExisting(candidate: MangaWithChapterCount) {
        val prompt = mutableState.value.mergePrompt ?: return
        screenModelScope.launch {
            try {
                withIOContext {
                    // Assign canonical ID and enrich missing metadata from the authoritative result
                    val result = prompt.result
                    val manga = candidate.manga
                    mangaRepository.update(
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

                    mutableState.value = mutableState.value.copy(
                        addedCanonicalIds = mutableState.value.addedCanonicalIds + prompt.canonicalId,
                        mergePrompt = null,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to merge with existing manga" }
                mutableState.value = mutableState.value.copy(mergePrompt = null)
            }
        }
    }

    /**
     * User declined to merge — create a new authority entry instead and proceed
     * to the find-source prompt.
     */
    fun skipMerge() {
        val prompt = mutableState.value.mergePrompt ?: return
        mutableState.value = mutableState.value.copy(mergePrompt = null)
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
    fun dismissMergePrompt() {
        mutableState.value = mutableState.value.copy(mergePrompt = null)
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
        val existingByUrl = mangaRepository.getMangaByUrlAndSourceId(
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
            val inserted = mangaRepository.insertNetworkManga(listOf(newManga))
            inserted.firstOrNull() ?: return
        }

        // Mark as favourite and set content type from publishing_type
        val inferredType = ContentType.fromPublishingType(result.publishing_type)
        mangaRepository.update(
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

        mutableState.value = mutableState.value.copy(
            addedCanonicalIds = mutableState.value.addedCanonicalIds + canonicalId,
            sourcePromptManga = SourcePromptInfo(
                title = result.title,
                mangaId = manga.id,
                isSearching = true,
            ),
        )

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
                    mutableState.value = mutableState.value.copy(
                        sourcePromptManga = currentPrompt.copy(
                            sourceMatches = matches.toImmutableList(),
                            isSearching = false,
                        ),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Auto-search for sources failed" }
                val currentPrompt = mutableState.value.sourcePromptManga ?: return@launch
                if (currentPrompt.mangaId == manga.id) {
                    mutableState.value = mutableState.value.copy(
                        sourcePromptManga = currentPrompt.copy(isSearching = false),
                    )
                }
            }
        }
    }

    /** Dismiss the source prompt dialog. */
    fun dismissSourcePrompt() {
        mutableState.value = mutableState.value.copy(sourcePromptManga = null)
    }

    /** User tapped a result card to view full details. */
    fun selectResult(result: TrackSearch) {
        mutableState.value = mutableState.value.copy(selectedResult = result)
    }

    /** Dismiss the detail view. */
    fun dismissDetail() {
        mutableState.value = mutableState.value.copy(selectedResult = null)
    }

    /** Retry the last failed search. */
    fun retrySearch() {
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
        mangaRepository.update(
            MangaUpdate(id = manga.id, alternativeTitles = merged),
        )
    }
}

data class AuthoritySearchState(
    val selectedTracker: Tracker? = null,
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
