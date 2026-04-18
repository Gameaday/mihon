package ephyra.feature.upcoming

import java.time.YearMonth

sealed interface UpcomingScreenEvent {
    data class SetSelectedYearMonth(val yearMonth: YearMonth) : UpcomingScreenEvent
}
