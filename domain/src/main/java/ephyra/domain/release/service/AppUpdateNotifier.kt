package ephyra.domain.release.service

import ephyra.domain.release.model.Release

interface AppUpdateNotifier {
    fun cancel()
    fun promptUpdate(release: Release)
    fun onDownloadStarted(title: String? = null)
    fun onProgressChange(progress: Int)

    /**
     * Called when an APK download has finished and is ready to install.
     *
     * @param uriString  String form of the APK file URI.  Represented as a String at the domain
     *   boundary so that this interface remains free of `android.*` imports.
     */
    fun promptInstall(uriString: String)
    fun onDownloadError(url: String)
}
