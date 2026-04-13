package ephyra.data.backup.restore.restorers

import android.content.Context
import android.util.Log
import ephyra.core.common.preference.PreferenceStore
import ephyra.data.backup.models.BackupCategory
import ephyra.data.backup.models.BackupPreference
import ephyra.data.backup.models.BackupSourcePreferences
import ephyra.data.backup.models.BooleanPreferenceValue
import ephyra.data.backup.models.FloatPreferenceValue
import ephyra.data.backup.models.IntPreferenceValue
import ephyra.data.backup.models.LongPreferenceValue
import ephyra.data.backup.models.StringPreferenceValue
import ephyra.data.backup.models.StringSetPreferenceValue
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.backup.service.BackupScheduler
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.model.Category
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryUpdateScheduler

class PreferenceRestorer(
    private val context: Context,
    private val getCategories: GetCategories,
    private val preferenceStore: PreferenceStore,
    private val libraryPreferences: LibraryPreferences,
    private val backupPreferences: BackupPreferences,
    private val backupScheduler: BackupScheduler,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCategories: List<BackupCategory>?,
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            backupCategories,
        )

        libraryUpdateScheduler.setupLibraryUpdateTask()
        backupScheduler.setupBackupTask(backupPreferences.backupInterval().get())
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            // val sourcePrefs = DataStorePreferenceStore(SharedPreferencesDataStore(sourcePreferences(it.sourceKey)))
            // restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupCategories: List<BackupCategory>? = null,
    ) {
        val allCategories = if (backupCategories != null) getCategories.await() else emptyList()
        val categoriesByName = allCategories.associateBy { it.name }
        val backupCategoriesById = backupCategories?.associateBy { it.id.toString() }.orEmpty()
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val newValue = if (key == LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY) {
                                backupCategoriesById[value.value.toString()]
                                    ?.let { categoriesByName[it.name]?.id?.toInt() }
                            } else {
                                value.value
                            }

                            newValue?.let { preferenceStore.getInt(key).set(it) }
                        }
                    }

                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }

                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }

                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }

                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }

                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            val restored = restoreCategoriesPreference(
                                key,
                                value.value,
                                preferenceStore,
                                backupCategoriesById,
                                categoriesByName,
                            )
                            if (!restored) preferenceStore.getStringSet(key).set(value.value)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)
            }
        }
    }

    private fun restoreCategoriesPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        backupCategoriesById: Map<String, BackupCategory>,
        categoriesByName: Map<String, Category>,
    ): Boolean {
        val categoryPreferences = LibraryPreferences.categoryPreferenceKeys + DownloadPreferences.categoryPreferenceKeys
        if (key !in categoryPreferences) return false

        val ids = value.mapNotNull {
            backupCategoriesById[it]?.name?.let { name ->
                categoriesByName[name]?.id?.toString()
            }
        }

        if (ids.isNotEmpty()) {
            preferenceStore.getStringSet(key).set(ids.toSet())
        }
        return true
    }
}
