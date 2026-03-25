package ephyra.app.di

import ephyra.feature.migration.list.MigrationListScreenModel
import ephyra.app.ui.category.CategoryScreenModel
import ephyra.app.ui.reader.ReaderViewModel
import ephyra.app.ui.deeplink.DeepLinkScreenModel
import ephyra.presentation.more.settings.screen.browse.ExtensionReposScreenModel
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
            getFavoritesByCanonicalId = get()
        )
    }

    factory { 
        CategoryScreenModel(
            getCategories = get(),
            createCategoryWithName = get(),
            deleteCategory = get(),
            reorderCategory = get(),
            renameCategory = get()
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
            app = org.koin.android.ext.koin.androidApplication(),
            coverCache = get(),
            localCoverManager = get(),
            updateManga = get()
        )
    }
    factory {
        ExtensionReposScreenModel(
            getExtensionRepo = get(),
            createExtensionRepo = get(),
            deleteExtensionRepo = get(),
            replaceExtensionRepo = get(),
            updateExtensionRepo = get(),
            extensionManager = get()
        )
    factory { (query: String) ->
        DeepLinkScreenModel(
            query = query,
            sourceManager = get(),
            networkToLocalManga = get(),
            getChapterByUrlAndMangaId = get(),
            syncChaptersWithSource = get()
        )
    }
}
