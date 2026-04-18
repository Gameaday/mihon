package ephyra.domain

import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.ExcludedScanlatorRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.release.ReleaseServiceImpl
import ephyra.data.repository.ExtensionRepoRepositoryImpl
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
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.chapter.interactor.GetChapterByUrlAndMangaId
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.ShouldUpdateDbChapter
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.download.interactor.DeleteDownload
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.extensionrepo.service.ExtensionRepoService
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.GetTotalReadDuration
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.interactor.RemoveResettedHistory
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.interactor.DeleteNonLibraryManga
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.GetDeadFavorites
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.service.ReleaseService
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.GetRemoteManga
import ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
import ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.domain.source.interactor.ToggleIncognito
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.DeleteTrack
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.GetTracksPerManga
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.interactor.LinkTrackedMangaToAuthority
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.SyncChapterProgressWithTrack
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.upcoming.interactor.GetUpcomingManga
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.repository.UpdatesRepository
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val koinDomainModule = module {
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    factory { GetCategories(get()) }
    factory { ResetCategoryFlags(get(), get()) }
    factory { SetDisplayMode(get()) }
    factory { SetSortModeForCategory(get(), get()) }
    factory { CreateCategoryWithName(get(), get()) }
    factory { RenameCategory(get()) }
    factory { ReorderCategory(get()) }
    factory { UpdateCategory(get()) }
    factory { DeleteCategory(get(), get(), get()) }

    single<MangaRepository> { MangaRepositoryImpl(get()) }
    single<ExcludedScanlatorRepository> { ExcludedScanlatorRepositoryImpl(get()) }
    factory { GetDuplicateLibraryManga(get()) }
    factory { GetFavorites(get()) }
    factory { GetFavoritesByCanonicalId(get()) }
    factory { GetDeadFavorites(get()) }
    factory { GetLibraryManga(get()) }
    factory { GetMangaWithChapters(get(), get()) }
    factory { GetMangaByUrlAndSourceId(get()) }
    factory { GetManga(get()) }
    factory { GetNextChapters(get(), get(), get()) }
    factory { GetUpcomingManga(get()) }
    factory { ephyra.domain.jellyfin.interactor.SyncJellyfin() }
    factory { ResetViewerFlags(get()) }
    factory { SetMangaChapterFlags(get()) }
    factory { FetchInterval(get()) }
    factory { SetMangaDefaultChapterFlags(get(), get(), get()) }
    factory { SetMangaViewerFlags(get()) }
    factory { NetworkToLocalManga(get()) }
    factory { UpdateManga(get(), get(), get(), get(), get(), get()) }
    factory { FindContentSource(get(), get()) }
    factory { UpdateMangaNotes(get()) }
    factory { SetMangaCategories(get()) }
    factory { GetExcludedScanlators(get()) }
    factory { SetExcludedScanlators(get()) }
    factory { DeleteNonLibraryManga(get()) }
    factory {
        MigrateMangaUseCase(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }

    single<ReleaseService> { ReleaseServiceImpl(get(), get()) }
    factory { GetApplicationRelease(get(), get()) }

    single<TrackRepository> { TrackRepositoryImpl(get()) }
    factory { TrackChapter(get(), get(), get(), get(), get()) }
    factory { AddTracks(get(), get(), get(), get(), get(), get()) }
    factory { RefreshTracks(get(), get(), get(), get()) }
    factory { DeleteTrack(get()) }
    factory { GetTracksPerManga(get()) }
    factory { GetTracks(get()) }
    factory { InsertTrack(get()) }
    factory { SyncChapterProgressWithTrack(get(), get(), get()) }
    factory { TrackerListImporter(get(), get(), get(), get()) }
    factory { LinkTrackedMangaToAuthority(get(), get()) }
    factory { MatchUnlinkedManga(get(), get(), get(), get()) }
    factory { RefreshCanonicalMetadata(get(), get(), get(), get()) }

    single<ChapterRepository> { ChapterRepositoryImpl(get()) }
    factory { GetChapter(get()) }
    factory { GetChaptersByMangaId(get()) }
    factory { GetBookmarkedChaptersByMangaId(get()) }
    factory { GetChapterByUrlAndMangaId(get()) }
    factory { UpdateChapter(get()) }
    factory { SetReadStatus(get(), get(), get(), get()) }
    factory { ShouldUpdateDbChapter() }
    factory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetAvailableScanlators(get()) }
    factory { FilterChaptersForDownload(get(), get(), get()) }
    factory { GenerateAuthorityChapters(get()) }

    single<HistoryRepository> { HistoryRepositoryImpl(get()) }
    factory { GetHistory(get()) }
    factory { UpsertHistory(get()) }
    factory { RemoveHistory(get()) }
    factory { RemoveResettedHistory(get()) }
    factory { GetTotalReadDuration(get()) }

    factory { DeleteDownload(get(), get()) }

    factory { GetExtensionsByType(get(), get()) }
    factory { GetExtensionSources(get()) }
    factory { GetExtensionLanguages(get(), get()) }

    single<UpdatesRepository> { UpdatesRepositoryImpl(get()) }
    factory { GetUpdates(get()) }

    single<SourceRepository> { SourceRepositoryImpl(get(), get(), get()) }
    single<StubSourceRepository> { StubSourceRepositoryImpl(get()) }
    factory { GetEnabledSources(get(), get()) }
    factory { GetLanguagesWithSources(get(), get()) }
    factory { GetRemoteManga(get()) }
    factory { GetSourcesWithFavoriteCount(get(), get()) }
    factory { GetSourcesWithNonLibraryManga(get()) }
    factory { SetMigrateSorting(get()) }
    factory { ToggleLanguage(get()) }
    factory { ToggleSource(get()) }
    factory { ToggleSourcePin(get()) }
    factory { TrustExtension(get(), get()) }

    single<ExtensionRepoRepository> { ExtensionRepoRepositoryImpl(get()) }
    factory { ExtensionRepoService(get(), get()) }
    factory { GetExtensionRepo(get()) }
    factory { GetExtensionRepoCount(get()) }
    factory { CreateExtensionRepo(get(), get()) }
    factory { DeleteExtensionRepo(get()) }
    factory { ReplaceExtensionRepo(get()) }
    factory { UpdateExtensionRepo(get(), get()) }
    factory { ToggleIncognito(get()) }
    factory { GetIncognitoState(get(), get(), get()) }
}
