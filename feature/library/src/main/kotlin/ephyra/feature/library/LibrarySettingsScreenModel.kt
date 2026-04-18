package ephyra.feature.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackerManager
import ephyra.source.local.isLocal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Factory
import kotlin.time.Duration.Companion.seconds

@Factory
class LibrarySettingsScreenModel(
    val preferences: BasePreferences,
    val libraryPreferences: LibraryPreferences,
    private val setDisplayMode: SetDisplayMode,
    private val setSortModeForCategory: SetSortModeForCategory,
    trackerManager: TrackerManager,
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    fun onEvent(event: LibrarySettingsScreenEvent) {
        when (event) {
            is LibrarySettingsScreenEvent.ToggleFilter -> toggleFilter(event.preference)
            is LibrarySettingsScreenEvent.ToggleTracker -> toggleTracker(event.id)
            is LibrarySettingsScreenEvent.SetDisplayMode -> setDisplayMode(event.mode)
            is LibrarySettingsScreenEvent.SetSort -> setSort(event.category, event.mode, event.direction)
        }
    }

    private fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        screenModelScope.launchIO {
            preference(libraryPreferences).getAndSet {
                it.next()
            }
        }
    }

    private fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    private fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    private fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
