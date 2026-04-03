package ephyra.domain.release.service

interface AppUpdateDownloader {
    fun start(url: String, title: String? = null)
    fun stop()
}
