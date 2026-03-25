package ephyra.app.ui.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.domain.base.BasePreferences
import ephyra.app.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class LibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setDisplayMode: SetDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
