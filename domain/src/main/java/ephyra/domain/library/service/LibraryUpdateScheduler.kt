package ephyra.domain.library.service

import ephyra.domain.category.model.Category

interface LibraryUpdateScheduler {
    fun setupLibraryUpdateTask()

    /**
     * Immediately enqueues a one-off library update.
     *
     * The Android `Context` required to enqueue the WorkManager task is captured by the
     * implementation at construction time; it must not be part of the domain interface
     * (which must remain free of `android.*` imports and be JVM-testable).
     *
     * @param category When non-null, restrict the update to this category only.
     * @return `true` if the work was successfully enqueued, `false` if a run is already in
     *         progress.
     */
    fun startNow(category: Category? = null): Boolean
}
