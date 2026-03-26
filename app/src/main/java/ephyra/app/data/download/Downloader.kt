package ephyra.app.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import ephyra.domain.chapter.model.toSChapter
import ephyra.domain.manga.model.getComicInfo
import ephyra.app.data.cache.ChapterCache
import ephyra.app.data.download.model.Download
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.notification.NotificationHandler
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import ephyra.feature.reader.setting.ReaderPreferences
import ephyra.app.util.storage.DiskUtil
import ephyra.app.util.storage.DiskUtil.NOMEDIA_FILE
import ephyra.app.util.storage.saveTo
import ephyra.app.util.system.encoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import ephyra.core.archive.ZipWriter
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import okio.Buffer
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.storage.extension
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.launchNow
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.metadata.comicinfo.COMIC_INFO_FILE
import ephyra.core.metadata.comicinfo.ComicInfo
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.GetTracks
import ephyra.i18n.MR
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download.
 */
class Downloader(
    private val context: Context,
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManager: SourceManager,
    private val chapterCache: ChapterCache,
    private val downloadPreferences: DownloadPreferences,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val xml: XML,
    private val getCategories: GetCategories,
    private val getTracks: GetTracks,
    private val notifier: DownloadNotifier,
) {

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = DownloadStore(context)

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<Download>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        scope.launch {
            val chapters = store.restore()
            addAllToQueue(chapters)
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != Download.State.DOWNLOADED }
        pending.forEach { if (it.status != Download.State.QUEUE) it.status = Download.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        DownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.QUEUE }
        isPaused = true
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = combine(
                queueState,
                downloadPreferences.parallelSourceLimit().changes(),
            ) { a, b -> a to b }.transformLatest { (queue, parallelCount) ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        // Ignore completed downloads, leave them in the queue
                        .filter { it.status.value <= Download.State.DOWNLOADING.value }
                        .groupBy { it.source }
                        .toList()
                        .take(parallelCount)
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    // Suspend until a download enters the ERROR state
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(Download::statusFlow)) { states ->
                            states.contains(Download.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }
            }
                .distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<Download, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: Download) = launchIO {
        try {
            downloadChapter(download)

            // Remove successful download from queue
            if (download.status == Download.State.DOWNLOADED) {
                removeFromQueue(download)
            }
            if (areAllDownloadsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        val enqueuedChapterIds = queueState.value.mapTo(HashSet()) { it.chapter.id }
        val chaptersToQueue = chapters.asSequence()
            // Filter out those already downloaded.
            .filter { provider.findChapterDir(it.name, it.scanlator, it.url, manga.title, source) == null }
            // Add chapters to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { chapter -> chapter.id !in enqueuedChapterIds }
            // Create a download for each one.
            .map { Download(source, manga, it) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queueState.value.count { it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(
                            MR.strings.download_queue_size_warning,
                            context.stringResource(MR.strings.app_name),
                        ),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(context, LibraryUpdateNotifier.HELP_WARNING_URL),
                    )
                }
                DownloadJob.start(context)
            }
        }
    }

    /**
     * Downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private suspend fun downloadChapter(download: Download) {
        val mangaDir = provider.getMangaDir(download.manga.title, download.source).getOrElse { e ->
            download.status = Download.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
            return
        }

        val availSpace = DiskUtil.getAvailableStorageSpace(mangaDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = Download.State.ERROR
            notifier.onError(
                context.stringResource(MR.strings.download_insufficient_space),
                download.chapter.name,
                download.manga.title,
                download.manga.id,
            )
            return
        }

        val chapterDirname = if (
            libraryPreferences.jellyfinCompatibleNaming().get() &&
            downloadPreferences.saveChaptersAsCBZ().get()
        ) {
            provider.getJellyfinChapterDirName(
                download.manga.title,
                download.chapter.chapterNumber,
                download.chapter.name,
            )
        } else {
            provider.getChapterDirName(
                download.chapter.name,
                download.chapter.scanlator,
                download.chapter.url,
            )
        }
        val tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)
            ?: error("Failed to create temporary download directory for chapter ${download.chapter.name}")

        try {
            // If the page list already exists, start from the file
            val pageList = download.pages ?: run {
                // Otherwise, pull page list from network and add them to download object
                val pages = download.source.getPageList(download.chapter.toSChapter())

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }
                // Don't trust index from source
                val reIndexedPages = pages.mapIndexed { index, page -> Page(index, page.url, page.imageUrl, page.uri) }
                download.pages = reIndexedPages
                reIndexedPages
            }

            // Delete all temporary (unfinished) files
            tmpDir.listFiles()
                ?.filter { it.extension == "tmp" }
                ?.forEach { it.delete() }

            download.status = Download.State.DOWNLOADING

            // Start downloading images, consider we can have downloaded images already
            pageList.asFlow().flatMapMerge(concurrency = downloadPreferences.parallelPageLimit().get()) { page ->
                flow {
                    // Fetch image URL if necessary
                    if (page.imageUrl.isNullOrEmpty()) {
                        page.status = Page.State.LoadPage
                        try {
                            page.imageUrl = download.source.getImageUrl(page)
                        } catch (e: Throwable) {
                            page.status = Page.State.Error(e)
                        }
                    }

                    withIOContext { getOrDownloadImage(page, download, tmpDir) }
                    emit(page)
                }
                    .flowOn(Dispatchers.IO)
            }
                .collect {
                    // Do when page is downloaded.
                    notifier.onProgressChange(download)
                }

            // Do after download completes

            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = Download.State.ERROR
                return
            }

            // Unified post-processing: blocklist filtering + stub-page merging in a single
            // ordered pass. Lists files once, applies position-aware credit page detection
            // with aspect-ratio pre-filter, then merges consecutive stub pages.
            postProcessPages(tmpDir)

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                download.source,
            )

            // Only rename the directory if it's downloaded
            if (downloadPreferences.saveChaptersAsCBZ().get()) {
                archiveChapter(mangaDir, chapterDirname, tmpDir)
            } else {
                tmpDir.renameTo(chapterDirname)
            }

            // Copy CBZ to Jellyfin library folder if sync is enabled and folder is configured
            if (downloadPreferences.autoSyncToJellyfin().get() &&
                downloadPreferences.saveChaptersAsCBZ().get() &&
                libraryPreferences.jellyfinCompatibleNaming().get() &&
                downloadPreferences.jellyfinLibraryFolder().get().isNotBlank()
            ) {
                copyToJellyfinLibrary(mangaDir, chapterDirname, download.manga.title)
            }

            cache.addChapter(chapterDirname, mangaDir, download.manga)

            DiskUtil.createNoMediaFile(tmpDir, context)

            download.status = Download.State.DOWNLOADED
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the page list threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = Download.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Gets the image from the filesystem if it exists or downloads it otherwise.
     *
     * @param page the page to download.
     * @param download the download of the page.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadImage(page: Page, download: Download, tmpDir: UniFile) {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return
        }

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists
        tmpFile?.delete()

        // Try to find the image file
        val imageFile = tmpDir.listFiles()?.firstOrNull {
            it.name!!.startsWith("$filename.") || it.name!!.startsWith("${filename}__001")
        }

        try {
            // If the image is already downloaded, do nothing. Otherwise download from network
            val file = when {
                imageFile != null -> imageFile
                chapterCache.isImageInCache(
                    page.imageUrl!!,
                ) -> copyImageFromCache(chapterCache.getImageFile(page.imageUrl!!), tmpDir, filename)
                else -> downloadImage(page, download.source, tmpDir, filename)
            }

            // When the page is ready, set page path, progress (just in case) and status
            splitTallImageIfNeeded(page, tmpDir)

            page.uri = file.uri
            page.progress = 100
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // Mark this page as error and allow to download the remaining
            page.progress = 0
            page.status = Page.State.Error(e)
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Downloads the image from network to a file in tmpDir.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private suspend fun downloadImage(page: Page, source: HttpSource, tmpDir: UniFile, filename: String): UniFile {
        page.status = Page.State.DownloadImage
        page.progress = 0
        return flow {
            val response = source.getImage(page)
            val file = tmpDir.createFile("$filename.tmp")
                ?: error("Failed to create temporary file for page $filename")
            try {
                response.body.source().saveTo(file.openOutputStream())
                val extension = getImageExtension(response, file)
                file.renameTo("$filename.$extension")
            } catch (e: Exception) {
                response.close()
                file.delete()
                throw e
            }
            emit(file)
        }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            .retryWhen { _, attempt ->
                if (attempt < 3) {
                    delay((2L shl attempt.toInt()) * 1000)
                    true
                } else {
                    false
                }
            }
            .first()
    }

    /**
     * Copies the image from cache to file in tmpDir.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")
            ?: throw IOException("Could not create temporary file in download directory")
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = cacheFile.inputStream().use { ImageUtil.findImageType(it) } ?: return tmpFile
        tmpFile.renameTo("$filename.${extension.extension}")
        // Do NOT delete the cache file here: deleting the raw file from the DiskLruCache directory
        // bypasses journal tracking (leaving a stale entry) and can break active reader streams if
        // the same chapter is open in the reader at the same time.  The DiskLruCache manages its
        // own LRU eviction automatically when new content is added.
        return tmpFile
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        val mime = response.body.contentType()?.run { if (type == "image") "image/$subtype" else null }
        return ImageUtil.getExtensionFromMimeType(mime) { file.openInputStream() }
    }

    /**
     * Returns the encoder function and file extension for the user's preferred image format.
     * Used by every code path that creates a derived (split / merged / rotated) image so
     * that all such images within a chapter use the same format.
     */
    private fun derivedImageEncoder(): Pair<(android.graphics.Bitmap, java.io.OutputStream) -> Unit, String> {
        val fmt = libraryPreferences.imageFormat().get()
        return fmt.encoder() to fmt.extension
    }

    private fun splitTallImageIfNeeded(page: Page, tmpDir: UniFile) {
        if (!downloadPreferences.splitTallImages().get()) return

        try {
            val filenamePrefix = "%03d".format(Locale.ENGLISH, page.number)
            val imageFile = tmpDir.listFiles()?.firstOrNull { it.name.orEmpty().startsWith(filenamePrefix) }
                ?: error(context.stringResource(MR.strings.download_notifier_split_page_not_found, page.number))

            // If the original page was previously split, then skip
            if (imageFile.name.orEmpty().startsWith("${filenamePrefix}__")) return

            val (encoder, ext) = derivedImageEncoder()
            ImageUtil.splitTallImage(tmpDir, imageFile, filenamePrefix, encoder, ext)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to split downloaded image" }
        }
    }

    /**
     * Unified post-download processing pass that runs **after** page-count verification
     * and **before** ComicInfo creation / CBZ archiving.  Combines two concerns into one
     * ordered pipeline:
     *
     * 1. **Credit-page filtering** — removes known scanlation intro/outro/credits pages by
     *    comparing their perceptual hash (dHash) against
     *    [DownloadPreferences.blockedPageHashes].  Uses two optimizations to avoid
     *    computing dHash for every page:
     *    • *Position-aware*: credit pages are almost always at chapter boundaries, so only
     *      the first and last [BOUNDARY_PAGES] pages are checked by default.
     *    • *Aspect-ratio pre-filter*: the dominant (median) aspect ratio is computed from
     *      header-only reads; pages whose aspect ratio is within 5% of the dominant are
     *      assumed to be real content and skipped (credit pages often have a
     *      visually different aspect ratio, e.g. a landscape banner in a portrait manga).
     *
     * 2. **Stub-page merging** — merges narrow watermark strips into the preceding page
     *    when [ReaderPreferences.smartCombinePaged] is enabled. Uses header-only decoding
     *    for the stub check, so the non-stub case is inexpensive.
     *
     * @param tmpDir the temporary chapter directory.
     */
    private fun postProcessPages(tmpDir: UniFile) {
        // ── Phase 1: Credit-page filtering ──────────────────────────────
        val blockedHexes = downloadPreferences.blockedPageHashes().get()
        if (blockedHexes.isNotEmpty()) {
            val blockedDHashes = blockedHexes.mapNotNull { hex ->
                runCatching { ImageUtil.hexToDHash(hex) }.getOrNull()
            }
            if (blockedDHashes.isNotEmpty()) {
                filterBlockedPagesImpl(tmpDir, blockedDHashes)
            }
        }

        // ── Phase 2: Stub-page merging ──────────────────────────────────
        if (readerPreferences.smartCombinePaged().get()) {
            mergeStubPagesImpl(tmpDir)
        }
    }

    /**
     * Removes credit/intro/outro pages using position-aware, aspect-ratio-gated dHash matching.
     *
     * Only pages near chapter boundaries (first/last [BOUNDARY_PAGES]) are candidates.
     * Among those, pages whose aspect ratio closely matches the chapter's dominant aspect ratio
     * are assumed to be real content and skipped — the expensive dHash decode is reserved for
     * pages that *look* structurally different from the majority.
     */
    private fun filterBlockedPagesImpl(tmpDir: UniFile, blockedDHashes: List<Long>) {
        val threshold = DownloadPreferences.BLOCKED_PAGE_DHASH_THRESHOLD

        val allFiles = tmpDir.listFiles()
            ?.filter { file ->
                val name = file.name.orEmpty()
                !name.endsWith(".tmp") &&
                    name !in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) &&
                    ImageUtil.isImage(name)
            }
            ?.sortedBy { it.name }
            ?: return

        if (allFiles.isEmpty()) return

        // Determine the dominant (median) aspect ratio from header-only dimension reads.
        // Cache per-file aspect ratios so we don't re-read headers in the candidate loop.
        val fileAspectRatios = FloatArray(allFiles.size) { -1f }
        val validRatios = mutableListOf<Float>()
        for ((idx, file) in allFiles.withIndex()) {
            try {
                val dims = file.openInputStream().use { ImageUtil.getImageDimensions(it) }
                if (dims != null && dims.second > 0) {
                    val ar = dims.first.toFloat() / dims.second
                    fileAspectRatios[idx] = ar
                    validRatios.add(ar)
                }
            } catch (_: Exception) { /* skip */ }
        }
        val dominantAR = if (validRatios.isNotEmpty()) {
            validRatios.sort()
            validRatios[validRatios.size / 2]
        } else {
            null
        }

        // Select candidate pages: first/last BOUNDARY_PAGES of the chapter
        val candidateIndices = buildSet {
            for (i in 0 until min(BOUNDARY_PAGES, allFiles.size)) add(i)
            for (i in (allFiles.size - BOUNDARY_PAGES).coerceAtLeast(0) until allFiles.size) add(i)
        }

        for (idx in candidateIndices) {
            val file = allFiles[idx]
            try {
                // Aspect-ratio pre-filter: skip pages matching the dominant ratio within 5 %
                // Uses cached ratio from the dimension scan above — no additional I/O.
                if (dominantAR != null && fileAspectRatios[idx] > 0f) {
                    if (abs(fileAspectRatios[idx] - dominantAR) / dominantAR <= ASPECT_RATIO_TOLERANCE) continue
                }

                // Expensive: compute dHash (uses inSampleSize for reduced-resolution decode)
                val hash = file.openInputStream().use { ImageUtil.computeDHash(it) } ?: continue
                val matched = blockedDHashes.any { blocked ->
                    ImageUtil.dHashDistance(hash, blocked) <= threshold
                }
                if (matched) {
                    logcat(LogPriority.DEBUG) {
                        "Blocked page removed: ${file.name} " +
                            "(dHash=${ImageUtil.dHashToHex(hash)})"
                    }
                    file.delete()
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "Failed to process ${file.name} for blocklist check, skipping"
                }
            }
        }
    }

    /**
     * Merges consecutive stub pages (narrow watermark strips) into the preceding page
     * using the same smart-combine logic as the reader.
     */
    private fun mergeStubPagesImpl(tmpDir: UniFile) {
        val (encoder, ext) = derivedImageEncoder()

        // Build a sorted mutable list of primary page image files, excluding:
        //  • temporary files (.tmp)
        //  • metadata files (ComicInfo.xml, .nomedia)
        //  • secondary split pages (e.g. "001__002.webp") — first split ("001__001.webp") is kept
        val pageFiles = tmpDir.listFiles()
            ?.filter { file ->
                val name = file.name.orEmpty()
                !name.endsWith(".tmp") &&
                    name !in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) &&
                    ImageUtil.isImage(name) &&
                    !(name.contains("__") && !name.contains("__001."))
            }
            ?.sortedBy { it.name }
            ?.toMutableList()
            ?: return

        var i = 0
        while (i < pageFiles.size - 1) {
            val current = pageFiles[i]
            val next = pageFiles[i + 1]
            try {
                // Buffer the current page once; isAnimatedAndSupported and isSmallPage both use
                // peek() internally so the buffer is not consumed by the dimension checks.
                val currentSource = current.openInputStream().use { Buffer().readFrom(it) }

                // Animated pages cannot be merged
                if (ImageUtil.isAnimatedAndSupported(currentSource)) {
                    i++
                    continue
                }

                // Header-only stub check for the next page (cheap: reads only image dimensions)
                val isStub = next.openInputStream().use { ImageUtil.isSmallPage(it, currentSource) }
                if (!isStub) {
                    i++
                    continue
                }

                // Stub confirmed: open the next page again for full bitmap decode and merge.
                // currentSource still holds all its data (peek() was used above).
                val nextSource = next.openInputStream().use { Buffer().readFrom(it) }
                val mergedBitmap = ImageUtil.mergePages(currentSource, nextSource)

                // Write the merged image to a temp file, swap it in for the current file,
                // and delete the stub.  Using a temp file prevents data loss if the write fails.
                val baseName = current.name!!.substringBeforeLast(".")
                val mergedTmp = tmpDir.createFile("$baseName.$ext.tmp")
                    ?: throw IOException("Could not create temp file for merged stub page")
                mergedTmp.openOutputStream().use { encoder(mergedBitmap, it) }
                mergedBitmap.recycle()
                current.delete()
                next.delete()
                pageFiles.removeAt(i + 1)
                mergedTmp.renameTo("$baseName.$ext")
                // Update our list to point to the freshly renamed file so the next
                // iteration can check the merged page against the new next page.
                val mergedFile = tmpDir.findFile("$baseName.$ext")
                if (mergedFile != null) {
                    pageFiles[i] = mergedFile
                    // Do NOT increment i — check the merged page against the new next page
                } else {
                    // Rename succeeded but we cannot locate the file — unusual; skip forward
                    logcat(LogPriority.WARN) { "Could not locate merged file $baseName.$ext after rename; skipping" }
                    i++
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) {
                    "Failed to merge stub page ${next.name} into ${current.name} during download"
                }
                i++
            }
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: Download,
        tmpDir: UniFile,
    ): Boolean {
        // Page list hasn't been initialized
        val downloadPageCount = download.pages?.size ?: return false

        // Ensure that all pages have been downloaded
        if (download.downloadedImages != downloadPageCount) {
            return false
        }

        // Ensure that the chapter folder has all the pages
        val downloadedImagesCount = tmpDir.listFiles().orEmpty().count {
            val fileName = it.name.orEmpty()
            when {
                fileName in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) -> false
                fileName.endsWith(".tmp") -> false
                // Only count the first split page and not the others
                fileName.contains("__") && !fileName.contains("__001.") -> false
                else -> true
            }
        }
        return downloadedImagesCount == downloadPageCount
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
    ) {
        val zip = mangaDir.createFile("$dirname.cbz$TMP_DIR_SUFFIX")
            ?: throw IOException("Could not create CBZ archive file")
        ZipWriter(context, zip).use { writer ->
            tmpDir.listFiles()?.forEach { file ->
                writer.write(file)
            }
        }
        zip.renameTo("$dirname.cbz")
        tmpDir.delete()
    }

    /**
     * Copies a completed CBZ file to the configured Jellyfin library folder.
     * The folder structure is: {jellyfinFolder}/{mangaTitle}/{chapter}.cbz
     * This allows Jellyfin to discover the files via a library scan, even when
     * the app's download directory is not directly accessible to the server
     * (e.g., the Jellyfin folder is an SMB/NFS network share on a NAS).
     */
    private fun copyToJellyfinLibrary(
        mangaDir: UniFile,
        dirname: String,
        mangaTitle: String,
    ) {
        val folderUri = downloadPreferences.jellyfinLibraryFolder().get()
        if (folderUri.isBlank()) return

        try {
            val jellyfinRoot = UniFile.fromUri(context, android.net.Uri.parse(folderUri))
                ?: run {
                    logcat(LogPriority.WARN) { "Jellyfin library folder is not accessible: $folderUri" }
                    return
                }

            // Create series subdirectory: {jellyfinFolder}/{mangaTitle}/
            val seriesDir = jellyfinRoot.createDirectory(
                DiskUtil.buildValidFilename(mangaTitle),
            ) ?: run {
                logcat(LogPriority.WARN) { "Failed to create series directory in Jellyfin library folder" }
                return
            }

            val cbzFile = mangaDir.findFile("$dirname.cbz") ?: run {
                logcat(LogPriority.WARN) { "CBZ file not found for copy: $dirname.cbz" }
                return
            }

            // Skip if the file already exists in the Jellyfin folder
            val destFile = seriesDir.findFile("$dirname.cbz")
            if (destFile != null && destFile.length() > 0) {
                logcat(LogPriority.DEBUG) { "CBZ already exists in Jellyfin folder: $dirname.cbz" }
                return
            }

            val newFile = seriesDir.createFile("$dirname.cbz") ?: run {
                logcat(LogPriority.WARN) { "Failed to create CBZ in Jellyfin library folder" }
                return
            }

            cbzFile.openInputStream().use { input ->
                newFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }

            logcat(LogPriority.INFO) { "Copied $dirname.cbz to Jellyfin library folder" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy CBZ to Jellyfin library folder" }
        }
    }

    /**
     * Creates a ComicInfo.xml file inside the given directory.
     */
    private suspend fun createComicInfoFile(
        dir: UniFile,
        manga: Manga,
        chapter: Chapter,
        source: HttpSource,
    ) {
        val categories = getCategories.await(manga.id).map { it.name.trim() }.takeUnless { it.isEmpty() }
        val urls = getTracks.await(manga.id)
            .mapNotNull { track ->
                track.remoteUrl.takeUnless { url -> url.isBlank() }?.trim()
            }
            .plus(source.getChapterUrl(chapter.toSChapter()).trim())
            .distinct()

        val comicInfo = getComicInfo(
            manga,
            chapter,
            urls,
            categories,
            source.name,
            source.lang,
        )

        // Remove the old file
        dir.findFile(COMIC_INFO_FILE)?.delete()
        val comicInfoFile = dir.createFile(COMIC_INFO_FILE)
            ?: throw IOException("Could not create $COMIC_INFO_FILE")
        comicInfoFile.openOutputStream().use {
            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
            it.write(comicInfoString.toByteArray())
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= Download.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<Download>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = Download.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: Download) {
        _queueState.update {
            store.remove(download)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (Download) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            queue - downloads
        }
    }

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        removeFromQueueIf { it.chapter.id in chapterIds }
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<Download>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30

        // Arbitrary minimum required space to start a download: 200 MB
        const val MIN_DISK_SPACE = 200L * 1024 * 1024

        /** Number of pages at each end of the chapter to check for credit pages. */
        private const val BOUNDARY_PAGES = 3

        /** Aspect-ratio tolerance for credit page pre-filter (5 %). */
        private const val ASPECT_RATIO_TOLERANCE = 0.05f
    }
}
