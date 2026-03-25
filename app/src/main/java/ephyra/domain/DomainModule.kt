package ephyra.domain

import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.download.interactor.DeleteDownload
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.domain.source.interactor.ToggleIncognito
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.LinkTrackedMangaToAuthority
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.SyncChapterProgressWithTrack
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.data.repository.ExtensionRepoRepositoryImpl
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.extensionrepo.service.ExtensionRepoService
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.upcoming.interactor.GetUpcomingManga
import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.release.ReleaseServiceImpl
import ephyra.data.source.SourceRepositoryImpl
import ephyra.data.source.StubSourceRepositoryImpl
import ephyra.data.track.TrackRepositoryImpl
import ephyra.data.updates.UpdatesRepositoryImpl
import ephyra.domain.category.interactor.CreateCategoryWithName
import ephyra.domain.category.interactor.DeleteCategory
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.RenameCategory
import ephyra.domain.category.interactor.ReorderCategory
import ephyra.domain.category.interactor.ResetCategoryFlags
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.interactor.UpdateCategory
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.chapter.interactor.GetChapterByUrlAndMangaId
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.chapter.interactor.ShouldUpdateDbChapter
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.GetTotalReadDuration
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetDeadFavorites
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.service.ReleaseService
import ephyra.domain.source.interactor.GetRemoteManga
import ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.track.interactor.DeleteTrack
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.GetTracksPerManga
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.repository.UpdatesRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<CategoryRepository> { CategoryRepositoryImpl(get()) }
        addFactory { GetCategories(get()) }
        addFactory { ResetCategoryFlags(get(), get()) }
        addFactory { SetDisplayMode(get()) }
        addFactory { SetSortModeForCategory(get(), get()) }
        addFactory { CreateCategoryWithName(get(), get()) }
        addFactory { RenameCategory(get()) }
        addFactory { ReorderCategory(get()) }
        addFactory { UpdateCategory(get()) }
        addFactory { DeleteCategory(get(), get(), get()) }

        addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryManga(get()) }
        addFactory { GetFavorites(get()) }
        addFactory { GetFavoritesByCanonicalId(get()) }
        addFactory { GetDeadFavorites(get()) }
        addFactory { GetLibraryManga(get()) }
        addFactory { GetMangaWithChapters(get(), get()) }
        addFactory { GetMangaByUrlAndSourceId(get()) }
        addFactory { GetManga(get()) }
        addFactory { GetNextChapters(get(), get(), get()) }
        addFactory { GetUpcomingManga(get()) }
        addFactory { ResetViewerFlags(get()) }
        addFactory { SetMangaChapterFlags(get()) }
        addFactory { FetchInterval(get()) }
        addFactory { SetMangaDefaultChapterFlags(get(), get(), get()) }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { NetworkToLocalManga(get()) }
        addFactory { UpdateManga(get(), get()) }
        addFactory { FindContentSource(get(), get()) }
        addFactory { UpdateMangaNotes(get()) }
        addFactory { SetMangaCategories(get()) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SetExcludedScanlators(get()) }
        addFactory {
            MigrateMangaUseCase(
                get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            )
        }

        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get(), get()) }

        addSingletonFactory<TrackRepository> { TrackRepositoryImpl(get()) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddTracks(get(), get(), get(), get()) }
        addFactory { RefreshTracks(get(), get(), get(), get()) }
        addFactory { DeleteTrack(get()) }
        addFactory { GetTracksPerManga(get()) }
        addFactory { GetTracks(get()) }
        addFactory { InsertTrack(get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }
        addFactory { TrackerListImporter(get(), get(), get(), get()) }
        addFactory { LinkTrackedMangaToAuthority(get(), get()) }
        addFactory { MatchUnlinkedManga(get(), get(), get(), get()) }
        addFactory { RefreshCanonicalMetadata(get(), get(), get(), get()) }

        addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
        addFactory { GetChapter(get()) }
        addFactory { GetChaptersByMangaId(get()) }
        addFactory { GetBookmarkedChaptersByMangaId(get()) }
        addFactory { GetChapterByUrlAndMangaId(get()) }
        addFactory { UpdateChapter(get()) }
        addFactory { SetReadStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbChapter() }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { GetAvailableScanlators(get()) }
        addFactory { FilterChaptersForDownload(get(), get(), get()) }
        addFactory { GenerateAuthorityChapters(get()) }

        addSingletonFactory<HistoryRepository> { HistoryRepositoryImpl(get()) }
        addFactory { GetHistory(get()) }
        addFactory { UpsertHistory(get()) }
        addFactory { RemoveHistory(get()) }
        addFactory { GetTotalReadDuration(get()) }

        addFactory { DeleteDownload(get(), get()) }

        addFactory { GetExtensionsByType(get(), get()) }
        addFactory { GetExtensionSources(get()) }
        addFactory { GetExtensionLanguages(get(), get()) }

        addSingletonFactory<UpdatesRepository> { UpdatesRepositoryImpl(get()) }
        addFactory { GetUpdates(get()) }

        addSingletonFactory<SourceRepository> { SourceRepositoryImpl(get(), get()) }
        addSingletonFactory<StubSourceRepository> { StubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledSources(get(), get()) }
        addFactory { GetLanguagesWithSources(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetSourcesWithNonLibraryManga(get()) }
        addFactory { SetMigrateSorting(get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleSource(get()) }
        addFactory { ToggleSourcePin(get()) }
        addFactory { TrustExtension(get(), get()) }

        addSingletonFactory<ExtensionRepoRepository> { ExtensionRepoRepositoryImpl(get()) }
        addFactory { ExtensionRepoService(get(), get()) }
        addFactory { GetExtensionRepo(get()) }
        addFactory { GetExtensionRepoCount(get()) }
        addFactory { CreateExtensionRepo(get(), get()) }
        addFactory { DeleteExtensionRepo(get()) }
        addFactory { ReplaceExtensionRepo(get()) }
        addFactory { UpdateExtensionRepo(get(), get()) }
        addFactory { ToggleIncognito(get()) }
        addFactory { GetIncognitoState(get(), get(), get()) }
    }
}
