package ephyra.domain.backup.service

interface RestoreScheduler {
    /**
     * Enqueues a one-off restore from [uriString].
     *
     * @param uriString  Source backup file URI, as a String.  The Android `Uri` is represented
     *   as a String at the domain boundary so that this interface remains free of `android.*`
     *   imports and is JVM-testable.
     * @param optionsArray  Serialised [ephyra.data.backup.restore.RestoreOptions.asBooleanArray]
     *   controlling which data is restored.  Null restores everything.
     */
    fun startRestoreNow(uriString: String, optionsArray: BooleanArray?)

    /** Returns `true` if a restore job is currently running. */
    fun isRestoreRunning(): Boolean
}
