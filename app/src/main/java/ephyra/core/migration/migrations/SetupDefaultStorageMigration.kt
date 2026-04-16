package ephyra.core.migration.migrations

import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationContext
import ephyra.domain.storage.service.StoragePreferences

/**
 * Ensures the storage-directory preference is always explicitly set on first
 * install (and on any subsequent run where the key is missing).
 *
 * Without this, the [StoragePreferences.baseStorageDirectory] preference is
 * never written to DataStore on a fresh install, so [Preference.isSet] returns
 * false and the onboarding StorageStep's "Next" button stays disabled,
 * preventing the user from completing setup.
 *
 * Writing the platform default folder path makes [isSet] return true, which
 * lets the user proceed through onboarding immediately.  They can still
 * change the location later via Settings → Data and storage.
 */
class SetupDefaultStorageMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val storagePreferences = migrationContext.get<StoragePreferences>() ?: return false
        val folderProvider = migrationContext.get<AndroidStorageFolderProvider>() ?: return false
        val pref = storagePreferences.baseStorageDirectory()
        if (!pref.isSet()) {
            pref.set(folderProvider.path())
        }
        return true
    }
}
