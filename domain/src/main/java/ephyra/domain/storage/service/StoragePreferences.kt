package ephyra.domain.storage.service

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.storage.FolderProvider

class StoragePreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun baseStorageDirectory() = preferenceStore.getString(Preference.appStateKey("storage_dir"), folderProvider.path())
}
