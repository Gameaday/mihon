package ephyra.core.common.preference

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import ephyra.core.common.util.system.logcat

sealed class AndroidPreference<T>(
    private val preferences: SharedPreferences,
    private val keyFlow: Flow<String?>,
    private val key: String,
    private val defaultValue: T,
) : Preference<T> {

    abstract fun read(preferences: SharedPreferences, key: String, defaultValue: T): T

    abstract fun write(key: String, value: T): Editor.() -> Unit

    override fun key(): String {
        return key
    }

    override fun getSync(): T {
        return try {
            read(preferences, key, defaultValue)
        } catch (e: ClassCastException) {
            logcat { "Invalid value for $key; deleting" }
            delete()
            defaultValue
        }
    }

    override suspend fun get(): T = getSync()

    override fun set(value: T) {
        // commit = false uses Editor.apply() — fire-and-forget async write, never blocks the
        // calling thread. commit = true would use Editor.commit() which is a synchronous disk
        // write and must never run on the main thread.
        preferences.edit(commit = false, action = write(key, value))
    }

    override fun isSet(): Boolean {
        return preferences.contains(key)
    }

    override fun delete() {
        preferences.edit {
            remove(key)
        }
    }

    override fun defaultValue(): T {
        return defaultValue
    }

    override fun changes(): Flow<T> {
        return keyFlow
            .filter { it == key || it == null }
            .onStart { emit("ignition") }
            .map { getSync() }
            .conflate()
            // H1 — Ensure SharedPreferences disk reads in map { getSync() } always execute on IO.
            // Without this, the first emission from onStart fires on whatever thread the
            // collector is running on, which is typically the main thread during Compose
            // recomposition. flowOn does NOT affect the downstream collector's thread.
            .flowOn(Dispatchers.IO)
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.Eagerly, getSync())
    }

    class StringPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: String,
    ) : AndroidPreference<String>(preferences, keyFlow, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: String,
        ): String {
            return preferences.getString(key, defaultValue) ?: defaultValue
        }

        override fun write(key: String, value: String): Editor.() -> Unit = {
            putString(key, value)
        }
    }

    class LongPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Long,
    ) : AndroidPreference<Long>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: Long): Long {
            return preferences.getLong(key, defaultValue)
        }

        override fun write(key: String, value: Long): Editor.() -> Unit = {
            putLong(key, value)
        }
    }

    class IntPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Int,
    ) : AndroidPreference<Int>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: Int): Int {
            return preferences.getInt(key, defaultValue)
        }

        override fun write(key: String, value: Int): Editor.() -> Unit = {
            putInt(key, value)
        }
    }

    class FloatPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Float,
    ) : AndroidPreference<Float>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: Float): Float {
            return preferences.getFloat(key, defaultValue)
        }

        override fun write(key: String, value: Float): Editor.() -> Unit = {
            putFloat(key, value)
        }
    }

    class BooleanPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Boolean,
    ) : AndroidPreference<Boolean>(preferences, keyFlow, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: Boolean,
        ): Boolean {
            return preferences.getBoolean(key, defaultValue)
        }

        override fun write(key: String, value: Boolean): Editor.() -> Unit = {
            putBoolean(key, value)
        }
    }

    class StringSetPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Set<String>,
    ) : AndroidPreference<Set<String>>(preferences, keyFlow, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: Set<String>,
        ): Set<String> {
            return preferences.getStringSet(key, defaultValue) ?: defaultValue
        }

        override fun write(key: String, value: Set<String>): Editor.() -> Unit = {
            putStringSet(key, value)
        }
    }

    class ObjectAsString<T>(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
    ) : AndroidPreference<T>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: T): T {
            return try {
                preferences.getString(key, null)?.let(deserializer) ?: defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: T): Editor.() -> Unit = {
            putString(key, serializer(value))
        }
    }

    class ObjectAsInt<T>(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> Int,
        private val deserializer: (Int) -> T,
    ) : AndroidPreference<T>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: T): T {
            return try {
                if (preferences.contains(key)) preferences.getInt(key, 0).let(deserializer) else defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: T): Editor.() -> Unit = {
            putInt(key, serializer(value))
        }
    }
}
