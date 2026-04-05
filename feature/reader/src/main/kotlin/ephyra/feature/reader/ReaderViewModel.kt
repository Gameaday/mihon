package ephyra.feature.reader

import android.app.Application
import android.net.Uri
import androidx.annotation.IntRange
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ephyra.core.common.preference.toggle
import ephyra.core.common.util.lang.byteSize
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.lang.withUIContext
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.util.storage.cacheImageDir
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadManager
import ephyra.core.download.DownloadProvider
import ephyra.core.download.util.filterDownloaded
import ephyra.core.download.util.removeDuplicates
import ephyra.data.cache.ChapterCache
import ephyra.data.cache.CoverCache
import ephyra.data.database.models.toDomainChapter
import ephyra.data.saver.Image
import ephyra.data.saver.ImageSaver
import ephyra.data.saver.Location
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.model.toDbChapter
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.readerOrientation
import ephyra.domain.manga.model.readingMode
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.TrackPreferences
import ephyra.feature.reader.loader.ChapterLoader
import ephyra.feature.reader.loader.DownloadPageLoader
import ephyra.feature.reader.model.InsertPage
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.Viewer
import ephyra.presentation.core.util.manga.editCover
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import java.io.InputStream
import java.time.Instant
import java.util.Date

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val imageSaver: ImageSaver,
    val readerPreferences: ReaderPreferences,
    private val basePreferences: ephyra.domain.base.BasePreferences,
    private val downloadPreferences: DownloadPreferences,
    private val trackPreferences: TrackPreferences,
    private val trackChapter: TrackChapter,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getNextChapters: GetNextChapters,
    private val upsertHistory: UpsertHistory,
    private val updateChapter: UpdateChapter,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val getIncognitoState: GetIncognitoState,
    private val libraryPreferences: LibraryPreferences,
    private val app: Application,
    private val coverCache: CoverCache,
    private val localCoverManager: LocalCoverManager,
    private val updateManga: UpdateManga,
    private val chapterCache: ChapterCache,
) : ViewModel() {
    private companion object {
        const val FALLBACK_LAST_PAGE_INDEX = Int.MAX_VALUE
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    /**
     * Number of pages to proactively start loading when an adjacent chapter is preloaded,
     * scaled to the device's performance tier so high-end devices prefetch more aggressively.
     */
    private val preloadChapterAheadPages: Int by lazy {
        when (DeviceUtil.performanceTier(app)) {
            DeviceUtil.PerformanceTier.LOW -> 2
            DeviceUtil.PerformanceTier.MEDIUM -> 4
            DeviceUtil.PerformanceTier.HIGH -> 6
        }
    }

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }
    private var hasAppliedSavedPageIndex = false

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    private var chapterToDownload: Download? = null

    private var unfilteredChapterListCache: List<Chapter>? = null
    private suspend fun getUnfilteredChapterList(): List<Chapter> {
        if (unfilteredChapterListCache == null) {
            val manga = manga!!
            unfilteredChapterListCache = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
        }
        return unfilteredChapterListCache!!
    }

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private var chapterListCache: List<ReaderChapter>? = null
    private suspend fun getChapterList(): List<ReaderChapter> {
        chapterListCache?.let { return it }

        // Ensure the DownloadCache has finished reading its on-disk snapshot before we
        // run any filter that calls isChapterDownloaded() (skipFiltered branch and the
        // downloadedOnly filter below). On the warm path this is a single deferred check
        // with no suspension. On a cold start it waits for the brief disk-cache read.
        downloadManager.awaitCacheReady()

        val manga = manga!!
        val chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)

        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader = when {
            (readerPreferences.skipRead().get() || readerPreferences.skipFiltered().get()) -> {
                val skipRead = readerPreferences.skipRead().get()
                val skipFiltered = readerPreferences.skipFiltered().get()
                val filteredChapters = chapters.filterNot {
                    when {
                        skipRead && it.read -> true
                        skipFiltered -> {
                            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                                (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                                (
                                    manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_DOWNLOADED &&
                                        !downloadManager.isChapterDownloaded(
                                            it.name,
                                            it.scanlator,
                                            it.url,
                                            manga.title,
                                            manga.source,
                                        )
                                    ) ||
                                (
                                    manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                                        downloadManager.isChapterDownloaded(
                                            it.name,
                                            it.scanlator,
                                            it.url,
                                            manga.title,
                                            manga.source,
                                        )
                                    ) ||
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                        }

                        else -> false
                    }
                }

                if (selectedChapter in filteredChapters) {
                    filteredChapters
                } else {
                    filteredChapters + listOf(selectedChapter)
                }
            }

            else -> chapters
        }

        val result = chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(selectedChapter)
                } else {
                    this
                }
            }
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloaded(manga, downloadManager)
                } else {
                    this
                }
            }
            .map { it.toDbChapter() }
            .map(::ReaderChapter)
        chapterListCache = result
        return result
    }

    private val incognitoMode: Boolean by lazy { getIncognitoState.await(manga?.source) }
    private suspend fun downloadAheadAmount(): Int = downloadPreferences.autoDownloadWhileReading().get()

    init {
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { currentChapter ->
                if (!hasAppliedSavedPageIndex && chapterPageIndex >= 0) {
                    // Restore from SavedState
                    currentChapter.requestedPage = chapterPageIndex
                    // Apply the saved page index only once on restore, then keep the persisted
                    // value in sync with real progress via onPageSelected().
                    hasAppliedSavedPageIndex = true
                } else if (!currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.last_page_read.toInt()
                }
                chapterId = currentChapter.chapter.id!!
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            chapterToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onActivityFinish() {
        deletePendingChapters()
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    suspend fun init(mangaId: Long, initialChapterId: Long): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        return withIOContext {
            try {
                val manga = getManga.await(mangaId)
                if (manga != null) {
                    sourceManager.isInitialized.first { it }
                    mutableState.update { it.copy(manga = manga) }
                    if (chapterId == -1L) chapterId = initialChapterId

                    val source = sourceManager.getOrStub(manga.source)
                    loader = ChapterLoader(
                        app,
                        downloadManager,
                        downloadProvider,
                        manga,
                        source,
                        downloadPreferences,
                        chapterCache,
                    )

                    loadChapter(loader!!, getChapterList().first { chapterId == it.chapter.id })
                    Result.success(true)
                } else {
                    // Unlikely but okay
                    Result.success(false)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                Result.failure(e)
            }
        }
    }

    /**
     * Loads the given [chapter] with this [loader] and updates the currently active chapters.
     * Callers must handle errors.
     */
    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
    ): ViewerChapters {
        loader.loadChapter(chapter)

        // When the pre-processor blocks a page during online loading (after the image
        // arrives), the adapter must rebuild its item list to exclude the newly hidden
        // page. Wire up the callback so the viewer refreshes automatically.
        chapter.pageLoader?.onPageFiltered = {
            eventChannel.trySend(Event.ReloadViewerChapters)
        }

        // Queue every page at the lowest background priority so the smart-combine pre-scan
        // can process the entire chapter without waiting for the user to scroll to each page.
        // Priority is kept below the nearby-page preload (0) and current-page load (1) so
        // this never competes for bandwidth with what the user is actively reading.
        chapter.pageLoader?.preloadAllPages()

        val chapterList = getChapterList()
        val chapterPos = chapterList.indexOf(chapter)
        val newChapters = ViewerChapters(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
        )

        withUIContext {
            mutableState.update {
                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                it.viewerChapters?.unref()

                chapterToDownload = cancelQueuedDownloads(newChapters.currChapter)
                it.copy(
                    viewerChapters = newChapters,
                    bookmarked = newChapters.currChapter.chapter.bookmark,
                )
            }
        }
        return newChapters
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        viewModelScope.launchIO {
            logcat { "Loading ${chapter.chapter.url}" }

            updateHistory()
            restartReadTimer()

            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     */
    private suspend fun loadAdjacent(
        chapter: ReaderChapter,
        startFromEnd: Boolean,
    ) {
        val loader = loader ?: return

        logcat { "Loading adjacent ${chapter.chapter.url}" }

        // Direction-aware start for toolbar transitions:
        // - Next chapter should start at the beginning.
        // - Previous chapter should start at the end so readers can quickly refresh context.
        // The activity then snaps to the first/last visible page, excluding hidden pages.
        chapter.requestedPage = if (startFromEnd) {
            // Best effort explicit end target when pages are already known (e.g., preloaded).
            // Otherwise use a large index which is clamped to lastIndex by the viewers.
            chapter.pages?.lastIndex ?: FALLBACK_LAST_PAGE_INDEX
        } else {
            0
        }

        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withIOContext {
                loadChapter(loader, chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    suspend fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) {
            return
        }

        if (chapter.pageLoader?.isLocal == false) {
            val manga = manga ?: return
            val dbChapter = chapter.chapter
            // Ensure the DownloadCache is ready before querying: on the fast path (deferred
            // already complete) this is a single non-suspending check; on a cold start it
            // waits for the disk-cache read so we don't incorrectly skip the reload.
            downloadManager.awaitCacheReady()
            val isDownloaded = downloadManager.isChapterDownloaded(
                dbChapter.name,
                dbChapter.scanlator,
                dbChapter.url,
                manga.title,
                manga.source,
            )
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        val loader = loader ?: return
        try {
            logcat { "Preloading ${chapter.chapter.url}" }
            // Load with isPreloadOnly=true so the underlying HttpPageLoader spawns only a single
            // background worker. This prevents the speculative preload from competing with the
            // active chapter's downloads for network bandwidth.
            loader.loadChapter(chapter, isPreloadOnly = true)

            // Only proactively start downloading the first few images of the preloaded chapter
            // if the current chapter's buffer is healthy. If the user is outpacing the current
            // buffer (i.e. there are still many unloaded pages ahead of the reading position),
            // skip image preloading so all available bandwidth stays with the active chapter.
            // This makes cross-chapter image prefetch purely opportunistic.
            val currentPages = getCurrentChapter()?.pages
            val pendingAhead = if (currentPages != null) {
                currentPages
                    .asSequence()
                    .drop(chapterPageIndex + 1)
                    .take(preloadChapterAheadPages)
                    .count {
                        it.status == Page.State.Queue || it.status == Page.State.LoadPage ||
                            it.status == Page.State.DownloadImage
                    }
            } else {
                0
            }
            if (pendingAhead < preloadChapterAheadPages) {
                logcat { "Current buffer healthy ($pendingAhead pending ahead); starting next-chapter image preload" }
                chapter.pageLoader?.preloadFirstPages(preloadChapterAheadPages)
            } else {
                logcat {
                    "Buffer thin ($pendingAhead pending ahead >= $preloadChapterAheadPages threshold); deferring next-chapter image preload"
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        eventChannel.trySend(Event.ReloadViewerChapters)
    }

    fun onViewerLoaded(viewer: Viewer?) {
        mutableState.update {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        val selectedChapter = page.chapter
        val pages = selectedChapter.pages ?: return

        // Save last page read and mark as read if needed
        viewModelScope.launchNonCancellable {
            updateChapterProgress(selectedChapter, page)
        }

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        if (inDownloadRange) {
            downloadNextChapters()
        }

        // If the next chapter is already loaded (preloaded) but its image prefetch was
        // previously deferred because the current buffer was too thin, retry it now that
        // another page has been consumed. This makes the preload truly opportunistic: it
        // starts the moment bandwidth is available rather than only at the instant preload()
        // is first called.
        tryPreloadNextChapterImages(page.index, pages)

        eventChannel.trySend(Event.PageChanged)
    }

    /**
     * Starts image preloading for the next chapter if the current chapter's forward buffer is
     * healthy enough that we can spare bandwidth. Idempotent — pages already downloading or
     * cached are skipped by [preloadFirstPages].
     *
     * @param currentPageIndex zero-based index of the page the user just navigated to.
     * @param currentPages     the full page list of the chapter being read.
     */
    private fun tryPreloadNextChapterImages(currentPageIndex: Int, currentPages: List<ReaderPage>) {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        if (nextChapter.state !is ReaderChapter.State.Loaded) return
        val pendingAhead = currentPages
            .asSequence()
            .drop(currentPageIndex + 1)
            .take(preloadChapterAheadPages)
            .count {
                it.status == Page.State.Queue || it.status == Page.State.LoadPage ||
                    it.status == Page.State.DownloadImage
            }
        if (pendingAhead < preloadChapterAheadPages) {
            nextChapter.pageLoader?.preloadFirstPages(preloadChapterAheadPages)
        }
    }

    private fun downloadNextChapters() {
        val manga = manga ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return

        viewModelScope.launchIO {
            val downloadAheadAmount = downloadAheadAmount()
            if (downloadAheadAmount == 0) return@launchIO
            // Ensure the DownloadCache has finished reading its on-disk snapshot before
            // querying it. On the warm path this is a non-suspending check; on a cold
            // start it waits for the brief disk-cache read to avoid a false negative that
            // would suppress the download-ahead even though the next chapter is on disk.
            downloadManager.awaitCacheReady()
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                manga.title,
                manga.source,
            )
            if (!isNextChapterDownloaded) return@launchIO

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id!!).run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(nextChapter.toDomainChapter()!!)
                } else {
                    this
                }
            }.take(downloadAheadAmount)

            downloadManager.downloadChapters(
                manga,
                chaptersToDownload,
            )
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun cancelQueuedDownloads(currentChapter: ReaderChapter): Download? {
        return downloadManager.getQueuedDownloadOrNull(currentChapter.chapter.id!!)?.also {
            downloadManager.cancelQueuedDownloads(listOf(it))
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private suspend fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterReadSlots == -1) return

        // Determine which chapter should be deleted and enqueue
        val chapterList = getChapterList()
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // If chapter is completely read, no need to download it
        chapterToDownload = null

        if (chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     */
    private suspend fun updateChapterProgress(readerChapter: ReaderChapter, page: Page) {
        val pageIndex = page.index
        val chapterPages = readerChapter.pages

        // Compute the 1-based display position by counting only visible pages up to
        // and including the current page. This keeps the bottom indicator and slider in sync
        // with what is actually visible — hidden pages (absorbed stubs and blocked pages)
        // are invisible to the user and should not inflate the page count or current position.
        val displayIndex = if (chapterPages != null) {
            chapterPages.take(pageIndex + 1).count { !it.isHidden }
        } else {
            pageIndex + 1
        }
        mutableState.update {
            it.copy(currentPage = displayIndex)
        }
        readerChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex

        if (!incognitoMode && page.status !is Page.State.Error) {
            val prevLastPageRead = readerChapter.chapter.last_page_read
            val prevRead = readerChapter.chapter.read

            readerChapter.chapter.last_page_read = pageIndex

            // A page is the effective last page when it literally is the last page in the
            // chapter's page list, OR when all subsequent pages are hidden (absorbed stubs
            // or blocked credit pages). This covers both the merged-bitmap stub case and the
            // plain-visible-page case where trailing blocked pages are skipped by the reader.
            val isEffectivelyLastPage = pageIndex == chapterPages?.lastIndex ||
                chapterPages?.drop(pageIndex + 1)?.all { it.isHidden } == true
            if (isEffectivelyLastPage) {
                updateChapterProgressOnComplete(readerChapter)
            }

            // Skip the DB write when neither lastPageRead nor read changed — a memory
            // equality check is cheaper than a SQL UPDATE and avoids unnecessary I/O.
            if (readerChapter.chapter.last_page_read != prevLastPageRead ||
                readerChapter.chapter.read != prevRead
            ) {
                updateChapter.await(
                    ChapterUpdate(
                        id = readerChapter.chapter.id!!,
                        read = readerChapter.chapter.read,
                        lastPageRead = readerChapter.chapter.last_page_read.toLong(),
                    ),
                )
            }
        }
    }

    private suspend fun updateChapterProgressOnComplete(readerChapter: ReaderChapter) {
        readerChapter.chapter.read = true
        updateTrackChapterRead(readerChapter)
        deleteChapterIfNeeded(readerChapter)

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = getUnfilteredChapterList()
            .mapNotNull { chapter ->
                if (
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapterNumber == readerChapter.chapter.chapter_number.toDouble()
                ) {
                    ChapterUpdate(id = chapter.id, read = true)
                } else {
                    null
                }
            }
        updateChapter.awaitAll(duplicateUnreadChapters)
    }

    fun restartReadTimer() {
        chapterReadStartTime = Instant.now().toEpochMilli()
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    suspend fun updateHistory() {
        getCurrentChapter()?.let { readerChapter ->
            if (incognitoMode) return@let

            val chapterId = readerChapter.chapter.id!!
            val endTime = Date()
            val sessionReadDuration = chapterReadStartTime?.let { endTime.time - it } ?: 0

            upsertHistory.await(HistoryUpdate(chapterId, endTime, sessionReadDuration))
            chapterReadStartTime = null
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    suspend fun loadNextChapter() {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter, startFromEnd = false)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    suspend fun loadPreviousChapter(): Int? {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return null
        loadAdjacent(prevChapter, startFromEnd = true)
        // loadAdjacent() completes after loadChapter() returns, so the target chapter's page
        // list has been initialized (or load failed). Return null when a visible index can't be
        // derived (null/empty/all-hidden pages; indexOfLast returns -1 for empty/all-hidden)
        // so the caller can choose an explicit fallback.
        return prevChapter.lastVisiblePageIndex()
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    private fun ReaderChapter.lastVisiblePageIndex(): Int? {
        val visibleCount = pages?.count { !it.isHidden } ?: 0
        return (visibleCount - 1).takeIf { it >= 0 }
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    fun getChapterUrl(): String? {
        val sChapter = getCurrentChapter()?.chapter ?: return null
        val source = getSource() ?: return null

        return try {
            source.getChapterUrl(sChapter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun toggleChapterBookmark() {
        val chapter = getCurrentChapter()?.chapter ?: return
        val bookmarked = !chapter.bookmark
        chapter.bookmark = bookmarked

        viewModelScope.launchNonCancellable {
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!,
                    bookmark = bookmarked,
                ),
            )
        }

        mutableState.update {
            it.copy(
                bookmarked = bookmarked,
            )
        }
    }

    private val defaultReadingMode = readerPreferences.defaultReadingMode().stateIn(viewModelScope)
    private val defaultOrientation = readerPreferences.defaultOrientationType().stateIn(viewModelScope)

    /**
     * Returns the viewer position used by this manga or the default one.
     * When reading mode is DEFAULT, auto-detects webtoon content from
     * genre keywords and publishing type to suggest continuous vertical
     * scrolling — matching Jellyfin's content-aware reader behaviour.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = defaultReadingMode.value
        val readingMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT -> {
                // Content-type-aware default: auto-detect webtoon from metadata
                val currentManga = manga
                if (currentManga != null && ContentType.isLikelyWebtoon(currentManga.genre)) {
                    ReadingMode.WEBTOON.flagValue
                } else {
                    default
                }
            }

            else -> manga?.readingMode?.toInt() ?: default
        }
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.last_page_read

                mutableState.update {
                    it.copy(
                        manga = getManga.await(manga.id),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = defaultOrientation.value
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.last_page_read

                mutableState.update {
                    it.copy(
                        manga = getManga.await(manga.id),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.SetOrientation(getMangaOrientation()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    fun toggleCropBorders(): Boolean {
        val isPagerType = ReadingMode.isPagerType(getMangaReadingMode())
        return if (isPagerType) {
            runBlocking { readerPreferences.cropBorders().toggle() }
        } else {
            runBlocking { readerPreferences.cropBordersWebtoon().toggle() }
        }
    }

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    fun showLoadingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Loading) }
    }

    fun openReadingModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingModeSelect) }
    }

    fun openOrientationModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.OrientationModeSelect) }
    }

    fun openPageDialog(page: ReaderPage) {
        mutableState.update { it.copy(dialog = Dialog.PageActions(page)) }
    }

    fun openSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setBrightnessOverlayValue(value: Int) {
        mutableState.update { it.copy(brightnessOverlayValue = value) }
    }

    /**
     * Saves the image of the selected page on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage() {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val context = app
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (runBlocking { readerPreferences.folderPerManga().get() }) {
            DiskUtil.buildValidFilename(
                manga.title,
            )
        } else {
            ""
        }

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                // If the page was smart-combined with a stub, save the merged image so the saved
                // file reflects exactly what the reader showed rather than just the first page.
                // The bitmap is encoded to PNG on-the-fly — this is a rare, user-triggered
                // action so the one-time encode cost is negligible.
                val inputStream: () -> InputStream = page.mergedBitmap?.let { bitmap ->
                    {
                        val buffer = okio.Buffer()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
                        buffer.inputStream()
                    }
                } ?: page.stream!!
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = inputStream,
                        name = filename,
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                withUIContext {
                    notifier.onComplete(uri)
                    eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
                }
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the image of the selected page and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(copyToClipboard: Boolean) {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val destDir = app.cacheImageDir

        val filename = generateFilename(manga, page)

        try {
            viewModelScope.launchNonCancellable {
                destDir.deleteRecursively()
                // If the page was smart-combined with a stub, share the merged image so the
                // recipient sees the same combined view that the reader displayed.
                val inputStream: () -> InputStream = page.mergedBitmap?.let { bitmap ->
                    {
                        val buffer = okio.Buffer()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
                        buffer.inputStream()
                    }
                } ?: page.stream!!
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = inputStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the image of the selected page as cover and notifies the UI of the result.
     */
    fun setAsCover() {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                manga.editCover(localCoverManager, stream(), updateManga, coverCache)
                if (manga.isLocal() || manga.favorite) {
                    SetAsCoverResult.Success
                } else {
                    SetAsCoverResult.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsCoverResult.Error
            }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    /**
     * Computes the perceptual hash (dHash) of the selected page and adds it to the
     * blocked-pages preference set.  Future downloads will silently skip any page
     * whose dHash is within the configured Hamming distance threshold.
     *
     * The result includes the hex hash so the caller can offer an "Undo" action.
     */
    fun blockPage() {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return

        viewModelScope.launchNonCancellable {
            val result = try {
                val stream = page.stream ?: error("No page stream")
                val hash = withIOContext { stream().use { ImageUtil.computeDHash(it) } }
                    ?: error("Could not compute dHash")
                val hex = ImageUtil.dHashToHex(hash)
                val pref = downloadPreferences.blockedPageHashes()
                val current = pref.get().toMutableSet()
                current.add(hex)
                pref.set(current)
                logcat(LogPriority.INFO) { "Blocked page dHash=$hex" }
                BlockPageResult.Success(hex)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to block page" }
                BlockPageResult.Error
            }
            eventChannel.send(Event.BlockPageResult(result))
        }
    }

    /**
     * Removes a specific dHash hex string from the blocked-pages preference set.
     * Used to undo an accidental block or to selectively unblock a page.
     */
    fun unblockPage(hex: String) {
        viewModelScope.launchNonCancellable {
            withIOContext {
                val pref = downloadPreferences.blockedPageHashes()
                val current = pref.get().toMutableSet()
                if (current.remove(hex)) {
                    pref.set(current)
                    logcat(LogPriority.INFO) { "Unblocked page dHash=$hex" }
                }
            }
        }
    }

    /**
     * Checks whether the currently selected page's dHash matches any entry in
     * the blocked-pages set (within the configured Hamming distance threshold).
     *
     * @return The matching hex hash if blocked, or `null` if not blocked / not computable.
     */
    suspend fun findMatchingBlockedHash(): String? {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return null

        return try {
            val stream = page.stream ?: return null
            val hash = withIOContext { stream().use { ImageUtil.computeDHash(it) } } ?: return null
            val threshold = DownloadPreferences.BLOCKED_PAGE_DHASH_THRESHOLD
            downloadPreferences.blockedPageHashes().get().firstOrNull { hexStr ->
                try {
                    val blocked = ImageUtil.hexToDHash(hexStr)
                    ImageUtil.dHashDistance(hash, blocked) <= threshold
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed interface BlockPageResult {
        data class Success(val hex: String) : BlockPageResult
        data object Error : BlockPageResult
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (incognitoMode) return
        if (!runBlocking { trackPreferences.autoUpdateTrack().get() }) return

        val manga = manga ?: return
        viewModelScope.launchNonCancellable {
            trackChapter.await(app, manga.id, readerChapter.chapter.chapter_number.toDouble())
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val manga = manga ?: return

        viewModelScope.launchNonCancellable {
            downloadManager.enqueueChaptersToDelete(listOf(chapter.chapter.toDomainChapter()!!), manga)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingChapters()
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
        @IntRange(from = -100, to = 100) val brightnessOverlayValue: Int = 0,
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            // Count only visible pages so the indicator reflects the number of pages
            // the user actually navigates through. Hidden pages (absorbed stubs and
            // blocked credit pages) are invisible and should not inflate the total.
            get() = currentChapter?.pages?.count { !it.isHidden } ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data object Settings : Dialog
        data object ReadingModeSelect : Dialog
        data object OrientationModeSelect : Dialog
        data class PageActions(val page: ReaderPage) : Dialog
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event
        data class SetCoverResult(val result: SetAsCoverResult) : Event
        data class BlockPageResult(val result: ReaderViewModel.BlockPageResult) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(val uri: Uri, val page: ReaderPage) : Event
        data class CopyImage(val uri: Uri) : Event
    }
}
