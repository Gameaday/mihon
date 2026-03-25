package ephyra.feature.upcoming

import ephyra.domain.manga.model.Manga
import java.time.LocalDate

sealed interface UpcomingUIModel {
    data class Header(val date: LocalDate, val mangaCount: Int) : UpcomingUIModel
    data class Item(val manga: Manga) : UpcomingUIModel
}
