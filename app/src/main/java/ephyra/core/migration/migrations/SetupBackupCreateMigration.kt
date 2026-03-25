package ephyra.core.migration.migrations

import android.app.Application
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationContext
import ephyra.domain.backup.service.BackupPreferences

class SetupBackupCreateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val backupPreferences = migrationContext.get<BackupPreferences>() ?: return false
        BackupCreateJob.setupTask(context, backupPreferences)
        return true
    }
}
