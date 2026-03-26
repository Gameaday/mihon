package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.app.data.track.TrackerManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.service.TrackPreferences

class SettingsTrackingScreenModel(
    val trackPreferences: TrackPreferences,
    val trackerManager: TrackerManager,
    val sourceManager: SourceManager,
    val libraryPreferences: LibraryPreferences,
    val trackerListImporter: TrackerListImporter,
) : ScreenModel
