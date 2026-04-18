package ephyra.domain.backup.service

interface BackupNotifier {
    fun showBackupProgress()
    /**
     * Called when a backup has been created successfully.
     *
     * @param uriString  The URI of the completed backup file, as a String.  The Android `Uri`
     *   is represented as a String at the domain boundary so that this interface remains free
     *   of `android.*` imports and is JVM-testable.
     */
    fun showBackupComplete(uriString: String)
    fun showBackupError(error: String?)
    fun showRestoreProgress(progress: Int, total: Int, title: String)
    fun showRestoreComplete(time: Long, errorCount: Int, path: String?)
    fun showRestoreError(error: String?)
}
