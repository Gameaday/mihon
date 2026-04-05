package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager

class SettingsDownloadScreenModel(
    val getCategories: GetCategories,
    val downloadPreferences: DownloadPreferences,
    val trackerManager: TrackerManager,
    val trackPreferences: TrackPreferences,
    val libraryPreferences: LibraryPreferences,
) : ScreenModel
