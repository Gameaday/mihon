package ephyra.feature.updates

import cafe.adriel.voyager.core.model.ScreenModel
import org.koin.core.annotation.Factory
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.domain.updates.service.UpdatesPreferences
import ephyra.domain.updates.service.UpdatesPreferences

@Factory
class UpdatesSettingsScreenModel(
    val updatesPreferences: UpdatesPreferences,
) : ScreenModel {

    fun toggleFilter(preference: (UpdatesPreferences) -> Preference<TriState>) {
        preference(updatesPreferences).getAndSet {
            it.next()
        }
    }
}
