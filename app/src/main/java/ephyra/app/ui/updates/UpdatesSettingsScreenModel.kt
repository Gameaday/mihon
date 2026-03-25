package ephyra.app.ui.updates

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.domain.updates.service.UpdatesPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UpdatesSettingsScreenModel(
    val updatesPreferences: UpdatesPreferences = Injekt.get(),
) : ScreenModel {

    fun toggleFilter(preference: (UpdatesPreferences) -> Preference<TriState>) {
        preference(updatesPreferences).getAndSet {
            it.next()
        }
    }
}
