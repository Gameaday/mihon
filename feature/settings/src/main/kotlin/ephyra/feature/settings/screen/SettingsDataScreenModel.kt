package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.data.cache.ChapterCache
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.storage.service.StoragePreferences

class SettingsDataScreenModel(
    val backupPreferences: BackupPreferences,
    val storagePreferences: StoragePreferences,
    val libraryPreferences: LibraryPreferences,
    val chapterCache: ChapterCache,
    val getFavorites: GetFavorites,
) : ScreenModel
