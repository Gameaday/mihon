package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.data.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.ResetViewerFlags
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences

class SettingsAdvancedScreenModel(
    val basePreferences: BasePreferences,
    val networkPreferences: NetworkPreferences,
    val libraryPreferences: LibraryPreferences,
    val downloadCache: DownloadCache,
    val networkHelper: NetworkHelper,
    val resetViewerFlags: ResetViewerFlags,
    val trustExtension: TrustExtension,
) : ScreenModel
