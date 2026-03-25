package ephyra.core.migration.migrations

import android.app.Application
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationContext
import ephyra.domain.library.service.LibraryPreferences

class SetupLibraryUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false
        LibraryUpdateJob.setupTask(context, libraryPreferences)
        return true
    }
}
