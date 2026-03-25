package ephyra.app.di

import ephyra.feature.migration.list.MigrationListScreenModel
import ephyra.app.ui.category.CategoryScreenModel
import ephyra.app.ui.reader.ReaderViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val koinAppModule = module {
    // Injekt to Koin Bridge - App Preferences & Managers
    single<ephyra.domain.source.service.SourcePreferences> { Injekt.get() }
    single<ephyra.domain.source.service.SourceManager> { Injekt.get() }
    single<ephyra.app.data.download.DownloadManager> { Injekt.get() }
    single<ephyra.app.data.download.DownloadProvider> { Injekt.get() }
    single<ephyra.app.data.saver.ImageSaver> { Injekt.get() }
    single<ephyra.app.ui.reader.setting.ReaderPreferences> { Injekt.get() }
    single<ephyra.domain.base.BasePreferences> { Injekt.get() }
    single<ephyra.domain.download.service.DownloadPreferences> { Injekt.get() }
    single<ephyra.domain.track.service.TrackPreferences> { Injekt.get() }
    single<ephyra.domain.library.service.LibraryPreferences> { Injekt.get() }
    
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
            libraryPreferences = get()
        )
    }
}

val koinDomainModule = module {
    // Injekt to Koin Bridge - Domain Interactors
    single<ephyra.domain.manga.interactor.GetManga> { Injekt.get() }
    single<ephyra.domain.manga.interactor.NetworkToLocalManga> { Injekt.get() }
    single<ephyra.domain.manga.interactor.UpdateManga> { Injekt.get() }
    single<ephyra.domain.chapter.interactor.SyncChaptersWithSource> { Injekt.get() }
    single<ephyra.domain.chapter.interactor.GetChaptersByMangaId> { Injekt.get() }
    single<ephyra.domain.migration.usecases.MigrateMangaUseCase> { Injekt.get() }
    single<ephyra.domain.manga.interactor.GetFavoritesByCanonicalId> { Injekt.get() }
    
    single<ephyra.domain.category.interactor.GetCategories> { Injekt.get() }
    single<ephyra.domain.category.interactor.CreateCategoryWithName> { Injekt.get() }
    single<ephyra.domain.category.interactor.DeleteCategory> { Injekt.get() }
    single<ephyra.domain.category.interactor.ReorderCategory> { Injekt.get() }
    single<ephyra.domain.category.interactor.RenameCategory> { Injekt.get() }

    single<ephyra.domain.track.interactor.TrackChapter> { Injekt.get() }
    single<ephyra.domain.history.interactor.GetNextChapters> { Injekt.get() }
    single<ephyra.domain.history.interactor.UpsertHistory> { Injekt.get() }
    single<ephyra.domain.chapter.interactor.UpdateChapter> { Injekt.get() }
    single<ephyra.domain.manga.interactor.SetMangaViewerFlags> { Injekt.get() }
    single<ephyra.domain.source.interactor.GetIncognitoState> { Injekt.get() }
}
