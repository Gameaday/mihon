package eu.kanade.tachiyomi.ui.browse.source.authority

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.FindContentSource
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.TrackerListImporter
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GenerateAuthorityChapters
import tachiyomi.domain.manga.model.ContentType
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.mergedAlternativeTitles
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AuthoritySearchScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val generateAuthorityChapters: GenerateAuthorityChapters = Injekt.get(),
    private val findContentSource: FindContentSource = Injekt.get(),
) : StateScreenModel<AuthoritySearchState>(AuthoritySearchState()) {

    /**
     * Trackers available for authority search.
     * Includes logged-in authoritative trackers AND trackers with public search APIs.
     * This allows discovery to work without login for trackers like MangaUpdates.
     */
    val availableTrackers: ImmutableList<Tracker> = trackerManager.trackers
        .filter {
            AddTracks.TRACKER_CANONICAL_PREFIXES.containsKey(it.id) &&
                (it.isLoggedIn || it.id in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH)
        }
        .toImmutableList()

    init {
        if (availableTrackers.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(selectedTracker = availableTrackers.first())
        }
    }

    fun selectTracker(tracker: Tracker) {
        mutableState.value = mutableState.value.copy(
            selectedTracker = tracker,
            results = persistentListOf(),
            query = "",
        )
    }

    fun search(query: String) {
        val tracker = mutableState.value.selectedTracker ?: return
        mutableState.value = mutableState.value.copy(
            query = query,
            isSearching = true,
            results = persistentListOf(),
        )
        screenModelScope.launch {
            try {
                val results = withIOContext { tracker.search(query) }
                mutableState.value = mutableState.value.copy(
                    results = results.toImmutableList(),
                    isSearching = false,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Authority search failed: query=$query" }
                mutableState.value = mutableState.value.copy(
                    isSearching = false,
                    results = persistentListOf(),
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

        screenModelScope.launch {
            try {
                withIOContext {
                    // Reuse the same dedup logic as TrackerListImporter
                    val existingByCanonical = mangaRepository.getFavoritesByCanonicalId(canonicalId, -1L)
                    if (existingByCanonical.isNotEmpty()) {
                        // Already in library — just mark as added in UI
                        mutableState.value = mutableState.value.copy(
                            addedCanonicalIds = mutableState.value.addedCanonicalIds + canonicalId,
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
                        )
                        return@withIOContext
                    }

                    // No existing matches — create new authority entry
                    createAuthorityEntry(result, tracker, canonicalId)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to add authority manga: canonical_id=$canonicalId" }
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
                    if (prompt.tracker.isLoggedIn) {
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
                            private = result.private,
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
        if (tracker.isLoggedIn) {
            val track = Track(
                id = 0L,
                mangaId = manga.id,
                trackerId = tracker.id,
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
                private = result.private,
            )
            insertTrack.await(track)
        }

        // Generate authority chapters so the user can mark progress
        if (result.total_chapters > 0) {
            generateAuthorityChapters.await(
                mangaId = manga.id,
                totalChapters = result.total_chapters.toInt(),
                lastChapterRead = result.last_chapter_read.toInt(),
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
    /** Canonical IDs of manga already added to the library in this session. */
    val addedCanonicalIds: Set<String> = emptySet(),
    /** Non-null when the user just added a manga and should be prompted to find a source. */
    val sourcePromptManga: SourcePromptInfo? = null,
    /** Non-null when unpaired library manga match the title — user should merge or skip. */
    val mergePrompt: MergePromptInfo? = null,
    /** Non-null when user tapped a result to view full details. */
    val selectedResult: TrackSearch? = null,
)

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
