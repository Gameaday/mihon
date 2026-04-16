package ephyra.domain.storage.service

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.storage.FolderProvider

class StoragePreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    /**
     * The preferred base storage directory URI.
     *
     * The default value is an empty string (unset sentinel).  On first install
     * a migration ([ephyra.core.migration.migrations.SetupDefaultStorageMigration])
     * writes the platform's default folder path so that the value is never empty
     * after initialization, and so that [Preference.isSet] reliably returns true.
     */
    fun baseStorageDirectory() = preferenceStore.getString(Preference.appStateKey("storage_dir"), "")

    /** The platform-default storage directory path used when no explicit choice has been made. */
    fun defaultStoragePath(): String = folderProvider.path()
}
