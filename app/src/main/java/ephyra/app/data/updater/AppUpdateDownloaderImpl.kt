package ephyra.app.data.updater

import android.content.Context
import ephyra.domain.release.service.AppUpdateDownloader

class AppUpdateDownloaderImpl(
    private val context: Context,
) : AppUpdateDownloader {
    override fun start(url: String, title: String?) {
        AppUpdateDownloadJob.start(context, url, title)
    }

    override fun stop() {
        AppUpdateDownloadJob.stop(context)
    }
}
