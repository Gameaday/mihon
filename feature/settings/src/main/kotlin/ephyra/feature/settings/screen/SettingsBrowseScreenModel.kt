package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount
import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow

class SettingsBrowseScreenModel(
    val sourcePreferences: SourcePreferences,
    private val getExtensionRepoCount: GetExtensionRepoCount,
) : ScreenModel {

    fun getExtensionRepoCount(): Flow<Long> {
        return getExtensionRepoCount.subscribe()
    }
}
