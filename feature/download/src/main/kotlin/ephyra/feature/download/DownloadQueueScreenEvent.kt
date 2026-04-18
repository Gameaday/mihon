package ephyra.feature.download

import ephyra.domain.download.model.Download

sealed interface DownloadQueueScreenEvent {
    data object StartDownloads : DownloadQueueScreenEvent
    data object PauseDownloads : DownloadQueueScreenEvent
    data object ClearQueue : DownloadQueueScreenEvent
    data class Reorder(val downloads: List<Download>) : DownloadQueueScreenEvent
    data class Cancel(val downloads: List<Download>) : DownloadQueueScreenEvent
}
