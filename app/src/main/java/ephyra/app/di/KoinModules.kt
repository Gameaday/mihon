package ephyra.app.di

import ephyra.app.ui.deeplink.DeepLinkScreenModel
import ephyra.feature.category.CategoryScreenModel
import ephyra.feature.download.DownloadQueueScreenModel
import ephyra.feature.manga.CoverSearchScreenModel
import ephyra.feature.manga.MangaCoverScreenModel
import ephyra.feature.migration.config.MigrationConfigScreen
import ephyra.feature.migration.dialog.MigrateDialogScreenModel
import ephyra.feature.migration.list.MigrationListScreenModel
import ephyra.feature.reader.ReaderViewModel
import ephyra.feature.settings.screen.SettingsAdvancedScreenModel
import ephyra.feature.settings.screen.SettingsAppearanceScreenModel
import ephyra.feature.settings.screen.SettingsBrowseScreenModel
import ephyra.feature.settings.screen.SettingsDataScreenModel
import ephyra.feature.settings.screen.SettingsDownloadScreenModel
import ephyra.feature.settings.screen.SettingsLibraryScreenModel
import ephyra.feature.settings.screen.SettingsReaderScreenModel
import ephyra.feature.settings.screen.SettingsSecurityScreenModel
import ephyra.feature.settings.screen.SettingsTrackingScreenModel
import ephyra.feature.settings.screen.about.AboutScreenModel
import ephyra.feature.settings.screen.advanced.ClearDatabaseScreenModel
import ephyra.feature.settings.screen.browse.ExtensionReposScreenModel
import ephyra.feature.settings.screen.debug.WorkerInfoScreen
import ephyra.feature.upcoming.UpcomingScreenModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinAppModule_UI = module {
    factory { (mangaIds: Collection<Long>, extraSearchQuery: String?) ->
        MigrationListScreenModel(
            mangaIds = mangaIds,
            extraSearchQuery = extraSearchQuery,
            preferences = get(),
            sourceManager = get(),
            getManga = get(),
            networkToLocalManga = get(),
            updateManga = get(),
            syncChaptersWithSource = get(),
            getChaptersByMangaId = get(),
            migrateManga = get(),
            getFavoritesByCanonicalId = get(),
        )
    }

    factory {
        CategoryScreenModel(
            getCategories = get(),
            createCategoryWithName = get(),
            deleteCategory = get(),
            reorderCategory = get(),
            renameCategory = get(),
        )
    }

    viewModel {
        ReaderViewModel(
            savedState = get(),
            sourceManager = get(),
            downloadManager = get(),
            downloadProvider = get(),
            imageSaver = get(),
            readerPreferences = get(),
            basePreferences = get(),
            downloadPreferences = get(),
            trackPreferences = get(),
            trackChapter = get(),
            getManga = get(),
            getChaptersByMangaId = get(),
            getNextChapters = get(),
            upsertHistory = get(),
            updateChapter = get(),
            setMangaViewerFlags = get(),
            getIncognitoState = get(),
            libraryPreferences = get(),
            app = androidApplication(),
            coverCache = get(),
            localCoverManager = get(),
            updateManga = get(),
            chapterCache = get(),
        )
    }
    factory {
        ExtensionReposScreenModel(
            getExtensionRepo = get(),
            createExtensionRepo = get(),
            deleteExtensionRepo = get(),
            replaceExtensionRepo = get(),
            updateExtensionRepo = get(),
            extensionManager = get(),
        )
    }

    factory {
        ClearDatabaseScreenModel(
            getSourcesWithNonLibraryManga = get(),
            deleteNonLibraryManga = get(),
            removeResettedHistory = get(),
        )
    }
    factory { (query: String) ->
        DeepLinkScreenModel(
            query = query,
            sourceManager = get(),
            networkToLocalManga = get(),
            getChapterByUrlAndMangaId = get(),
            syncChaptersWithSource = get(),
        )
    }

    factory { DownloadQueueScreenModel(get()) }
    factory { (mangaId: Long) ->
        MangaCoverScreenModel(mangaId, get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    factory { (title: String, sourceId: Long) -> CoverSearchScreenModel(title, sourceId, get()) }
    factory { MigrationConfigScreen.ScreenModel(get(), get()) }
    factory { UpcomingScreenModel(get()) }
    factory { WorkerInfoScreen.Model(androidContext(), get()) }

    factory {
        MigrateDialogScreenModel(
            sourcePreference = get(),
            coverCache = get(),
            downloadManager = get(),
            migrateManga = get(),
        )
    }

    factory { AboutScreenModel(get(), get(), get()) }
    factory { SettingsDownloadScreenModel(get(), get(), get(), get(), get()) }
    factory { SettingsDataScreenModel(get(), get(), get(), get(), get()) }
    factory { SettingsBrowseScreenModel(get(), get()) }
    factory { SettingsLibraryScreenModel(get(), get(), get()) }
    factory { SettingsTrackingScreenModel(get(), get(), get(), get(), get(), get()) }
    factory { SettingsAppearanceScreenModel(get()) }
    factory { SettingsReaderScreenModel(get()) }
    factory { SettingsSecurityScreenModel(get(), get()) }
    factory { SettingsAdvancedScreenModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}
