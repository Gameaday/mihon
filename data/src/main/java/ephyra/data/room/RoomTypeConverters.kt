package ephyra.data.room

import androidx.room.TypeConverter
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.util.Date

class RoomTypeConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromGenreString(value: String?): List<String>? {
        return value?.takeIf { it.isNotEmpty() }?.split(", ") ?: emptyList()
    }

    @TypeConverter
    fun genreListToString(list: List<String>?): String? {
        return list?.joinToString(separator = ", ")
    }

    @TypeConverter
    fun fromUpdateStrategy(value: Int): UpdateStrategy {
        return UpdateStrategy.entries.getOrElse(value) { UpdateStrategy.ALWAYS_UPDATE }
    }

    @TypeConverter
    fun updateStrategyToLong(value: UpdateStrategy): Int {
        return value.ordinal
    }
}
