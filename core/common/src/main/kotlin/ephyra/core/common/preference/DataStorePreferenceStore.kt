package ephyra.core.common.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ephyra.core.common.util.system.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority

/**
 * Modern [PreferenceStore] implementation backed by Jetpack DataStore.
 *
 * - All writes are transactional and crash-safe via [DataStore.edit].
 * - [Preference.changes] returns DataStore's native [Flow].
 * - Synchronous [Preference.get] reads from an eagerly-loaded snapshot that is
 *   kept permanently in sync. This is a **transitional bridge** — callers should
 *   migrate to [Preference.changes] or collect the [StateFlow] from [Preference.stateIn].
 * - Automatic one-time migration from SharedPreferences via [SharedPreferencesMigration].
 *
 * @param context Application context.
 * @param name DataStore file name (without path/extension).
 * @param sharedPreferencesName Legacy SharedPreferences file to migrate from.
 */
class DataStorePreferenceStore(
    private val context: Context,
    name: String = "ephyra_preferences",
    sharedPreferencesName: String = "${context.packageName}_preferences",
) : PreferenceStore {

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = name,
        produceMigrations = { ctx ->
            listOf(SharedPreferencesMigration(ctx, sharedPreferencesName))
        },
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    /**
     * Transitional bridge snapshot for [getSync].
     * We initialize this asynchronously now to avoid runBlocking in init.
     */
    @Volatile
    private var snapshot: Preferences = emptyPreferences()

    init {
        storeScope.launch {
            dataStore.data.collect { prefs -> snapshot = prefs }
        }
    }

    // ── Factory methods ──────────────────────────────────────────────────

    override fun getString(key: String, defaultValue: String): Preference<String> =
        DirectPreference(stringPreferencesKey(key), key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        DirectPreference(longPreferencesKey(key), key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        DirectPreference(intPreferencesKey(key), key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        DirectPreference(floatPreferencesKey(key), key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        DirectPreference(booleanPreferencesKey(key), key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        DirectPreference(stringSetPreferencesKey(key), key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = MappedPreference(
        prefsKey = stringPreferencesKey(key),
        key = key,
        defaultValue = defaultValue,
        toRaw = serializer,
        fromRaw = deserializer,
    )

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = MappedPreference(
        prefsKey = intPreferencesKey(key),
        key = key,
        defaultValue = defaultValue,
        toRaw = serializer,
        fromRaw = deserializer,
    )

    override fun getAll(): Map<String, *> =
        snapshot.asMap().entries.associate { (k, v) -> k.name to v }

    // ── Preference implementations ───────────────────────────────────────

    /**
     * Preference where the DataStore type matches the domain type exactly.
     */
    private inner class DirectPreference<T>(
        private val prefsKey: Preferences.Key<T>,
        private val key: String,
        private val defaultValue: T,
    ) : Preference<T> {

        override fun key(): String = key

        override fun getSync(): T = snapshot[prefsKey] ?: defaultValue

        override suspend fun get(): T = changes().first()

        override fun set(value: T) {
            storeScope.launch { dataStore.edit { it[prefsKey] = value } }
        }

        override fun isSet(): Boolean = prefsKey in snapshot

        override fun delete() {
            storeScope.launch { dataStore.edit { it.remove(prefsKey) } }
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> =
            dataStore.data.map { it[prefsKey] ?: defaultValue }.distinctUntilChanged()

        override fun stateIn(scope: CoroutineScope): StateFlow<T> =
            changes().stateIn(scope, SharingStarted.Eagerly, getSync())
    }

    /**
     * Preference that maps between a domain type [T] and a raw DataStore type [R].
     */
    private inner class MappedPreference<T, R>(
        private val prefsKey: Preferences.Key<R>,
        private val key: String,
        private val defaultValue: T,
        private val toRaw: (T) -> R,
        private val fromRaw: (R) -> T,
    ) : Preference<T> {

        override fun key(): String = key

        override fun getSync(): T {
            val raw = snapshot[prefsKey] ?: return defaultValue
            return try {
                fromRaw(raw)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to deserialize preference '$key'; returning default" }
                defaultValue
            }
        }

        override suspend fun get(): T = changes().first()

        override fun set(value: T) {
            storeScope.launch { dataStore.edit { it[prefsKey] = toRaw(value) } }
        }

        override fun isSet(): Boolean = prefsKey in snapshot

        override fun delete() {
            storeScope.launch { dataStore.edit { it.remove(prefsKey) } }
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = dataStore.data.map { prefs ->
            val raw = prefs[prefsKey] ?: return@map defaultValue
            try {
                fromRaw(raw)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to deserialize preference '$key' from Flow; returning default" }
                defaultValue
            }
        }.distinctUntilChanged()

        override fun stateIn(scope: CoroutineScope): StateFlow<T> =
            changes().stateIn(scope, SharingStarted.Eagerly, getSync())
    }
}
