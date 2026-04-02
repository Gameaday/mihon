package ephyra.domain.library.service

import android.content.Context
import ephyra.domain.category.model.Category

interface LibraryUpdateScheduler {
    fun setupLibraryUpdateTask()

    /**
     * Immediately enqueues a one-off library update.
     *
     * @param context Android context used to enqueue the WorkManager task.
     * @param category When non-null, restrict the update to this category only.
     * @return `true` if the work was successfully enqueued, `false` if a run is already in
     *         progress.
     */
    fun startNow(context: Context, category: Category? = null): Boolean
}
