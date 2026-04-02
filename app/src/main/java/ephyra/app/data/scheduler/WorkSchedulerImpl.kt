package ephyra.app.data.scheduler

import android.content.Context
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.domain.backup.service.BackupScheduler
import ephyra.domain.category.model.Category
import ephyra.domain.library.service.LibraryUpdateScheduler

class WorkSchedulerImpl(private val context: Context) : BackupScheduler, LibraryUpdateScheduler {
    override fun setupBackupTask(interval: Int) {
        BackupCreateJob.setupTask(context, interval)
    }

    override fun setupLibraryUpdateTask() {
        // LibraryUpdateJob.setupTask(context)
    }

    override fun startNow(context: Context, category: Category?): Boolean {
        return LibraryUpdateJob.startNow(context, category)
    }
}
