package ephyra.feature.updates

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.updates.service.UpdatesPreferences
import org.koin.core.annotation.Factory

@Factory
class UpdatesSettingsScreenModel(
    val updatesPreferences: UpdatesPreferences,
) : ScreenModel {

    fun onEvent(event: UpdatesSettingsScreenEvent) {
        when (event) {
            is UpdatesSettingsScreenEvent.ToggleFilter -> toggleFilter(event.preference)
        }
    }

    private fun toggleFilter(preference: (UpdatesPreferences) -> Preference<TriState>) {
        screenModelScope.launchIO {
            preference(updatesPreferences).getAndSet {
                it.next()
            }
        }
    }
}
