package ephyra.app.di

import android.app.Application
import android.content.Context
import ephyra.app.data.scheduler.WorkSchedulerImpl
import ephyra.app.data.updater.AppUpdateDownloaderImpl
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.extension.util.ExtensionLoader
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.core.download.DownloadCache
import ephyra.core.download.DownloadPendingDeleter
import ephyra.core.download.DownloadProvider
import ephyra.core.download.Downloader
import ephyra.data.cache.CoverCache
import ephyra.data.track.TrackerManagerImpl
import ephyra.domain.backup.service.BackupScheduler
import ephyra.domain.backup.service.RestoreScheduler
import ephyra.domain.base.BasePreferences
import ephyra.domain.base.InstallerCapabilityProvider
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryUpdateScheduler
import ephyra.domain.library.service.MetadataUpdateScheduler
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.DeleteTrack
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.SyncChapterProgressWithTrack
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.ui.UiPreferences
import ephyra.feature.browse.source.globalsearch.GlobalSearchScreen
import ephyra.feature.migration.config.MigrationConfigScreen
import ephyra.feature.settings.screen.browse.ExtensionReposScreen
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.ExtensionReposScreenFactory
import ephyra.presentation.core.ui.GlobalSearchScreenFactory
import ephyra.presentation.core.ui.MigrationConfigScreenFactory
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.AndroidSourceManager
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import ephyra.app.BuildConfig as AppBuildConfig
import ephyra.core.download.DownloadManager as CoreDownloadManager
import ephyra.domain.download.service.DownloadManager as IDownloadManager
import ephyra.domain.extension.service.ExtensionManager as IExtensionManager
import ephyra.domain.manga.service.CoverCache as ICoverCache

/**
 * Annotation-based Koin module that gives the Koin compiler plugin (compileSafety=true)
 * a definition hint for every type that is consumed via a direct [koinInject] or [inject]
 * callsite in a feature module.
 *
 * DSL `module { }` registrations are opaque to the compiler's cross-module graph analyser;
 * types registered only in DSL blocks therefore appear as "missing definitions" during
 * [app:compileDebugKotlin].  Moving those registrations here (as [Single]/[Factory]
 * provider functions on a [Module]-annotated class) makes them visible to the compiler
 * while keeping the dependency wiring intact at runtime.
 *
 * Every type that is *not* consumed by a direct injection callsite in a feature module can
 * remain in a plain DSL [module] block without triggering a safety error.
 */
@Module
class AppAnnotatedModule {

    // ── Preferences ───────────────────────────────────────────────────────────

    @Single
    fun basePreferences(
        capabilityProvider: InstallerCapabilityProvider,
        preferenceStore: PreferenceStore,
    ): BasePreferences = BasePreferences(capabilityProvider, preferenceStore)

    @Single
    fun storagePreferences(
        folderProvider: AndroidStorageFolderProvider,
        preferenceStore: PreferenceStore,
    ): StoragePreferences = StoragePreferences(folderProvider, preferenceStore)

    @Single
    fun uiPreferences(preferenceStore: PreferenceStore): UiPreferences = UiPreferences(preferenceStore)

    // ── Network ───────────────────────────────────────────────────────────────

    @Single
    fun networkHelper(app: Application, preferences: NetworkPreferences): NetworkHelper =
        NetworkHelper(app, preferences)

    // ── Work scheduler ────────────────────────────────────────────────────────

    @Single
    fun workSchedulerImpl(app: Application): WorkSchedulerImpl = WorkSchedulerImpl(app)

    @Single
    fun backupScheduler(impl: WorkSchedulerImpl): BackupScheduler = impl

    @Single
    fun restoreScheduler(impl: WorkSchedulerImpl): RestoreScheduler = impl

    @Single
    fun libraryUpdateScheduler(impl: WorkSchedulerImpl): LibraryUpdateScheduler = impl

    @Single
    fun metadataUpdateScheduler(impl: WorkSchedulerImpl): MetadataUpdateScheduler = impl

    // ── Cover cache ───────────────────────────────────────────────────────────

    @Single
    fun coverCache(app: Application): CoverCache = CoverCache(app)

    @Single
    fun coverCacheInterface(impl: CoverCache): ICoverCache = impl

    // ── App updater ───────────────────────────────────────────────────────────

    @Single
    fun appUpdateDownloader(app: Application): AppUpdateDownloader = AppUpdateDownloaderImpl(app)

    // ── Tracker ───────────────────────────────────────────────────────────────

    @Single
    fun trackerManager(
        app: Application,
        trackPreferences: TrackPreferences,
        libraryPreferences: LibraryPreferences,
        sourceManager: SourceManager,
        networkHelper: NetworkHelper,
        addTracks: AddTracks,
        insertTrack: InsertTrack,
        json: Json,
    ): TrackerManager = TrackerManagerImpl(
        app, trackPreferences, libraryPreferences, sourceManager,
        networkHelper, addTracks, insertTrack, json,
    )

    // ── Download manager ─────────────────────────────────────────────────────

    @Single
    fun downloadManager(
        app: Application,
        provider: DownloadProvider,
        cache: DownloadCache,
        getCategories: GetCategories,
        getManga: GetManga,
        getChapter: GetChapter,
        sourceManager: SourceManager,
        downloadPreferences: DownloadPreferences,
        libraryPreferences: LibraryPreferences,
        downloader: Downloader,
        pendingDeleter: DownloadPendingDeleter,
    ): IDownloadManager = CoreDownloadManager(
        app, provider, cache, getCategories, getManga, getChapter,
        sourceManager, downloadPreferences, libraryPreferences, downloader, pendingDeleter,
    )

    // ── Extension infrastructure (internal types — not consumed cross-module) ──

    @Single
    internal fun extensionLoader(
        preferences: SourcePreferences,
        trustExtension: TrustExtension,
    ): ExtensionLoader = ExtensionLoader(preferences, trustExtension)

    @Single
    internal fun extensionInstaller(
        context: Context,
        basePreferences: BasePreferences,
        networkHelper: NetworkHelper,
        extensionLoader: ExtensionLoader,
    ): ExtensionInstaller = ExtensionInstaller(context, basePreferences, networkHelper, extensionLoader)

    @Single
    internal fun extensionApi(
        networkHelper: NetworkHelper,
        preferenceStore: PreferenceStore,
        getExtensionRepo: GetExtensionRepo,
        updateExtensionRepo: UpdateExtensionRepo,
        securityPreferences: SecurityPreferences,
        extensionLoader: ExtensionLoader,
        json: Json,
    ): ExtensionApi = ExtensionApi(
        networkHelper, preferenceStore, getExtensionRepo, updateExtensionRepo,
        securityPreferences, extensionLoader, json,
    )

    // ── Extension manager ─────────────────────────────────────────────────────

    @Single
    internal fun extensionManagerImpl(
        app: Application,
        preferences: SourcePreferences,
        trustExtension: TrustExtension,
        securityPreferences: SecurityPreferences,
        extensionLoader: ExtensionLoader,
        api: ExtensionApi,
        installer: ExtensionInstaller,
    ): ExtensionManager = ExtensionManager(
        app, preferences, trustExtension, securityPreferences, extensionLoader, api, installer,
    )

    @Single
    internal fun extensionManagerInterface(impl: ExtensionManager): IExtensionManager = impl

    // ── Source manager ────────────────────────────────────────────────────────

    @Single
    internal fun sourceManager(
        app: Application,
        extensionManager: ExtensionManager,
        sourceRepository: StubSourceRepository,
        fileSystem: LocalSourceFileSystem,
        coverManager: LocalCoverManager,
        downloadManager: IDownloadManager,
    ): SourceManager = AndroidSourceManager(
        app, extensionManager, sourceRepository, fileSystem, coverManager, downloadManager,
    )

    // ── AppInfo ───────────────────────────────────────────────────────────────

    @Single
    fun appInfo(): AppInfo = object : AppInfo {
        override val isDebug: Boolean get() = AppBuildConfig.DEBUG
        override val buildType: String get() = AppBuildConfig.BUILD_TYPE
        override val commitSha: String get() = AppBuildConfig.COMMIT_SHA
        override val commitCount: String get() = AppBuildConfig.COMMIT_COUNT.toString()
        override val versionName: String get() = AppBuildConfig.VERSION_NAME
        override val buildTime: String get() = AppBuildConfig.BUILD_TIME
        override val telemetryIncluded: Boolean get() = AppBuildConfig.TELEMETRY_INCLUDED
        override val updaterEnabled: Boolean get() = AppBuildConfig.UPDATER_ENABLED
        override val githubRepo: String
            get() = if (isPreview) "Gameaday/Ephyra-preview" else "Gameaday/Ephyra"
    }

    // ── Screen factories ──────────────────────────────────────────────────────

    @Single
    fun globalSearchScreenFactory(): GlobalSearchScreenFactory =
        GlobalSearchScreenFactory { query -> GlobalSearchScreen(query) }

    @Single
    fun migrationConfigScreenFactory(): MigrationConfigScreenFactory =
        MigrationConfigScreenFactory { mangaIds -> MigrationConfigScreen(mangaIds) }

    @Single
    fun extensionReposScreenFactory(): ExtensionReposScreenFactory =
        ExtensionReposScreenFactory { url -> ExtensionReposScreen(url) }

    // ── Domain interactors (explicitly injected via koinInject in feature modules) ──

    @Factory
    fun getManga(repository: MangaRepository): GetManga = GetManga(repository)

    @Factory
    fun getTracks(repository: TrackRepository): GetTracks = GetTracks(repository)

    @Factory
    fun deleteTrack(repository: TrackRepository): DeleteTrack = DeleteTrack(repository)

    @Factory
    fun refreshTracks(
        getTracks: GetTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertTrack,
        syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    ): RefreshTracks = RefreshTracks(getTracks, trackerManager, insertTrack, syncChapterProgressWithTrack)
}
