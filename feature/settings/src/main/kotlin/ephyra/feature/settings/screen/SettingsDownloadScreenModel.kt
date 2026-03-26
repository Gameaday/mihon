package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.app.data.track.TrackerManager
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackPreferences

class SettingsDownloadScreenModel(
    val getCategories: GetCategories,
    val downloadPreferences: DownloadPreferences,
    val trackerManager: TrackerManager,
    val trackPreferences: TrackPreferences,
    val libraryPreferences: LibraryPreferences,
) : ScreenModel
