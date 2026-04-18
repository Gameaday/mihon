package ephyra.feature.updates

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.domain.updates.service.UpdatesPreferences

sealed interface UpdatesSettingsScreenEvent {
    data class ToggleFilter(val preference: (UpdatesPreferences) -> Preference<TriState>) : UpdatesSettingsScreenEvent
}
