package ephyra.core.migration.migrations

import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationContext
import ephyra.domain.storage.service.StoragePreferences
import kotlinx.coroutines.flow.first

/**
 * Ensures the storage-directory preference is always explicitly set on first
 * install (and on any subsequent run where the key is missing).
 *
 * Without this, the [StoragePreferences.baseStorageDirectory] preference is
 * never written to DataStore on a fresh install, leaving it as the empty-string
 * sentinel, so the onboarding StorageStep's "Next" button stays disabled and
 * the user cannot complete setup.
 *
 * Writing the platform default folder path makes the preference non-empty,
 * which lets the user proceed through onboarding immediately.  They can still
 * change the location later via Settings → Data and storage.
 */
class SetupDefaultStorageMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val storagePreferences = migrationContext.get<StoragePreferences>() ?: return false
        val pref = storagePreferences.baseStorageDirectory()
        // Use the suspending get() to read the committed DataStore value rather than
        // the in-memory snapshot (which may be stale on a cold start).
        val currentValue = pref.get()
        if (currentValue.isEmpty()) {
            pref.set(storagePreferences.defaultStorageDirectoryUri())
            // Wait for the DataStore write to be committed and observable before
            // returning, so that concurrent observers (e.g. StorageStep) see the
            // new value as soon as the migration completes.
            pref.changes().first { it.isNotEmpty() }
        }
        return true
    }
}
