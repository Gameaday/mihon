package ephyra.domain.backup.service

interface BackupScheduler {
    fun setupBackupTask(interval: Int)

    /**
     * Enqueues a one-off manual backup to [uriString].
     *
     * @param uriString  Destination file URI string chosen by the user (null → automatic backup
     *   directory).  The Android Uri is represented as a String at the domain boundary so that
     *   this interface remains free of `android.*` imports and is JVM-testable.
     * @param optionsArray  Serialised [ephyra.data.backup.create.BackupOptions.asBooleanArray]
     *   controlling which data is included.  Null uses all-default options.
     */
    fun startBackupNow(uriString: String?, optionsArray: BooleanArray?)

    /** Returns `true` if a manual backup job is currently running. */
    fun isBackupRunning(): Boolean
}
