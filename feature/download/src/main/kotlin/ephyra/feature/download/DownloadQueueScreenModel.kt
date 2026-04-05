package ephyra.feature.download

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
class DownloadQueueScreenModel(
    private val downloadManager: DownloadManager,
) : ScreenModel {

    val state = downloadManager.queueState
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<Download>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancel(downloads: List<Download>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(selector: (Download) -> R, reverse: Boolean = false) {
        val reordered = state.value
            .groupBy { it.source.id }
            .values
            .flatMap { group ->
                group.sortedBy(selector).let { if (reverse) it.reversed() else it }
            }
        reorder(reordered)
    }
}
