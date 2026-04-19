package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.chapter.service.ChapterCache
import ephyra.domain.export.LibraryExporter
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.storage.service.StoragePreferences

class SettingsDataScreenModel(
    val backupPreferences: BackupPreferences,
    val storagePreferences: StoragePreferences,
    val libraryPreferences: LibraryPreferences,
    val chapterCache: ChapterCache,
    val getFavorites: GetFavorites,
    val libraryExporter: LibraryExporter,
) : ScreenModel
