package ephyra.data.track.anilist.dto

import ephyra.core.common.util.system.logcat
import kotlinx.serialization.Serializable
import logcat.LogPriority
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ALFuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?,
) {
    fun toEpochMilli(): Long {
        if (year == null || month == null || day == null) return 0L
        return try {
            LocalDate.of(year, month, day)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Failed to parse AniList fuzzy date ($year-$month-$day); returning 0" }
            0L
        }
    }
}
