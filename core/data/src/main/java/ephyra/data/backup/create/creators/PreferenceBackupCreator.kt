package ephyra.data.backup.create.creators

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.data.backup.models.BackupPreference
import ephyra.data.backup.models.BackupSourcePreferences
import ephyra.data.backup.models.BooleanPreferenceValue
import ephyra.data.backup.models.FloatPreferenceValue
import ephyra.data.backup.models.IntPreferenceValue
import ephyra.data.backup.models.LongPreferenceValue
import ephyra.data.backup.models.StringPreferenceValue
import ephyra.data.backup.models.StringSetPreferenceValue
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences

class PreferenceBackupCreator(
    private val sourceManager: SourceManager,
    private val preferenceStore: PreferenceStore,
) {

    fun createApp(includePrivatePreferences: Boolean): List<BackupPreference> {
        return preferenceStore.getAll().toBackupPreferences()
            .withPrivatePreferences(includePrivatePreferences)
    }

    fun createSource(includePrivatePreferences: Boolean): List<BackupSourcePreferences> {
        return sourceManager.getCatalogueSources()
            .filterIsInstance<ConfigurableSource>()
            .map {
                BackupSourcePreferences(
                    it.preferenceKey(),
                    it.sourcePreferences().all.toBackupPreferences()
                        .withPrivatePreferences(includePrivatePreferences),
                )
            }
            .filter { it.prefs.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return this
            .filterKeys { !Preference.isAppState(it) }
            .mapNotNull { (key, value) ->
                when (value) {
                    is Int -> BackupPreference(key, IntPreferenceValue(value))
                    is Long -> BackupPreference(key, LongPreferenceValue(value))
                    is Float -> BackupPreference(key, FloatPreferenceValue(value))
                    is String -> BackupPreference(key, StringPreferenceValue(value))
                    is Boolean -> BackupPreference(key, BooleanPreferenceValue(value))
                    is Set<*> -> (value as? Set<String>)?.let {
                        BackupPreference(key, StringSetPreferenceValue(it))
                    }

                    else -> null
                }
            }
    }

    private fun List<BackupPreference>.withPrivatePreferences(include: Boolean) =
        if (include) {
            this
        } else {
            this.filter { !Preference.isPrivate(it.key) }
        }
}
