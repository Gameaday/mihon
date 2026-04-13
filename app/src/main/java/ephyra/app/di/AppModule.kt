package ephyra.app.di

import android.app.DownloadManager
import androidx.room.*
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.app.data.download.DownloadNotifier
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.library.MetadataUpdateJob
import ephyra.app.data.scheduler.WorkSchedulerImpl
import ephyra.app.data.updater.AppUpdateDownloadJob
import ephyra.app.data.updater.AppUpdateDownloaderImpl
import ephyra.app.data.updater.AppUpdateNotifier
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.extension.util.ExtensionLoader
import ephyra.app.track.MatchUnlinkedJob
import ephyra.app.track.MatchUnlinkedNotifier
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.core.download.DownloadCache
import ephyra.core.download.DownloadJob
import ephyra.core.download.DownloadPendingDeleter
import ephyra.core.download.DownloadProvider
import ephyra.core.download.DownloadStore
import ephyra.core.download.Downloader
import ephyra.data.backup.BackupDecoder
import ephyra.data.backup.BackupFileValidator
import ephyra.data.backup.create.BackupCreator
import ephyra.data.backup.create.creators.CategoriesBackupCreator
import ephyra.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.data.backup.create.creators.MangaBackupCreator
import ephyra.data.backup.create.creators.PreferenceBackupCreator
import ephyra.data.backup.create.creators.SourcesBackupCreator
import ephyra.data.backup.restore.BackupRestorer
import ephyra.data.backup.restore.restorers.CategoriesRestorer
import ephyra.data.backup.restore.restorers.ExtensionRepoRestorer
import ephyra.data.backup.restore.restorers.MangaRestorer
import ephyra.data.backup.restore.restorers.PreferenceRestorer
import ephyra.data.cache.ChapterCache
import ephyra.data.cache.CoverCache
import ephyra.data.coil.MangaCoverKeyer
import ephyra.data.coil.MangaKeyer
import ephyra.data.room.EphyraDatabase
import ephyra.data.saver.ImageSaver
import ephyra.data.track.TrackerManagerImpl
import ephyra.data.updater.AppUpdateChecker
import ephyra.domain.source.service.SourceManager
import ephyra.domain.storage.service.StorageManager
import ephyra.domain.track.service.DelayedTrackingUpdateJob
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.feature.browse.source.globalsearch.GlobalSearchScreen
import ephyra.feature.migration.config.MigrationConfigScreen
import ephyra.feature.more.NewUpdateScreen
import ephyra.feature.more.OnboardingScreen
import ephyra.feature.settings.screen.browse.ExtensionReposScreen
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.ExtensionReposScreenFactory
import ephyra.presentation.core.ui.GlobalSearchScreenFactory
import ephyra.presentation.core.ui.MatchUnlinkedJobRunner
import ephyra.presentation.core.ui.MigrationConfigScreenFactory
import ephyra.presentation.core.ui.NewUpdateScreenFactory
import ephyra.presentation.core.ui.OnboardingScreenFactory
import ephyra.presentation.core.util.CrashLogUtil
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module
import ephyra.app.BuildConfig as AppBuildConfig
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate as CoreSecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate as CoreThemingDelegate

val koinAppModule = module {
    single {
        Room.databaseBuilder(
            context = androidApplication(),
            klass = EphyraDatabase::class.java,
            name = "tachiyomi.db",
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.query("PRAGMA foreign_keys = ON").close()
                    db.query("PRAGMA journal_mode = WAL").close()
                    db.query("PRAGMA synchronous = NORMAL").close()
                }
            })
            .build()
    }

    single { get<EphyraDatabase>().mangaDao() }
    single { get<EphyraDatabase>().chapterDao() }
    single { get<EphyraDatabase>().categoryDao() }
    single { get<EphyraDatabase>().historyDao() }
    single { get<EphyraDatabase>().trackDao() }
    single { get<EphyraDatabase>().updateDao() }
    single { get<EphyraDatabase>().extensionRepoDao() }
    single { get<EphyraDatabase>().sourceDao() }

    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        XML {
            defaultPolicy { ignoreUnknownChildren() }
            autoPolymorphic = true
            xmlDeclMode = XmlDeclMode.Charset
            indent = 2
            xmlVersion = XmlVersion.XML10
        }
    }

    single<ProtoBuf> { ProtoBuf }
    single { ExtensionLoader(get(), get()) }
    single { ExtensionInstaller(androidContext(), get(), get(), get()) }

    single { CrashLogUtil(androidContext(), get()) }
    single<AppInfo> {
        object : AppInfo {
            override val isDebug: Boolean get() = AppBuildConfig.DEBUG
            override val buildType: String get() = AppBuildConfig.BUILD_TYPE
            override val commitSha: String get() = AppBuildConfig.COMMIT_SHA
            override val commitCount: String get() = AppBuildConfig.COMMIT_COUNT.toString()
            override val versionName: String get() = AppBuildConfig.VERSION_NAME
            override val buildTime: String get() = AppBuildConfig.BUILD_TIME
            override val telemetryIncluded: Boolean get() = AppBuildConfig.TELEMETRY_INCLUDED
            override val updaterEnabled: Boolean get() = AppBuildConfig.UPDATER_ENABLED
        }
    }
    single { ChapterCache(androidApplication(), get()) }
    single { CoverCache(androidApplication()) }
    single { MangaKeyer(get()) }
    single<ephyra.presentation.core.util.AppNavigator> { ephyra.app.util.NavigatorImpl() }
    single<ephyra.core.common.notification.NotificationManager> {
        ephyra.app.data.notification.NotificationManagerImpl(get())
    }
    single<CoreThemingDelegate> { ThemingDelegateImpl(get()) }
    single<CoreSecureActivityDelegate> { SecureActivityDelegateImpl(get(), get()) }
    single { MangaCoverKeyer(get()) }

    single { NetworkHelper(androidApplication(), get()) }
    single { JavaScriptEngine(androidApplication()) }
    single { ExtensionApi(get(), get(), get(), get(), get(), get(), get()) }

    single<SourceManager> { AndroidSourceManager(androidApplication(), get(), get(), get(), get(), get()) }
    single { ExtensionManager(androidApplication(), get(), get(), get(), get(), get(), get()) }
    single<ephyra.domain.extension.service.ExtensionManager> { get<ExtensionManager>() }

    single { DownloadStore(androidApplication(), get(), get(), get(), get()) }
    single { DownloadProvider(androidApplication(), get(), get()) }
    single { DownloadCache(androidApplication(), get(), get(), get()) }
    single<ephyra.domain.download.service.DownloadManager> {
        ephyra.core.download.DownloadManager(
            androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
    single {
        Downloader(
            androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
    single { DownloadPendingDeleter(androidApplication(), get()) }
    single { DownloadNotifier(androidApplication(), get()) }

    single<TrackerManager> { TrackerManagerImpl(androidApplication(), get(), get(), get(), get(), get(), get(), get()) }
    single { DelayedTrackingStore(androidApplication()) }

    single { BackupDecoder(androidApplication(), get()) }
    single { BackupFileValidator(androidApplication(), get(), get()) }

    single { CategoriesBackupCreator(get()) }
    single { MangaBackupCreator(get(), get(), get()) }
    single { PreferenceBackupCreator(get(), get()) }
    single { ExtensionRepoBackupCreator(get()) }
    single { SourcesBackupCreator(get()) }
    single { BackupCreator(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    single { CategoriesRestorer(get(), get(), get()) }
    single { ExtensionRepoRestorer(get(), get()) }
    single { BackupRestorer(androidApplication(), get(), get(), get(), get(), get()) }
    single { AppUpdateChecker(get(), get()) }

    single { WorkSchedulerImpl(androidApplication()) }
    single<ephyra.domain.backup.service.BackupScheduler> { get<WorkSchedulerImpl>() }
    single<ephyra.domain.backup.service.RestoreScheduler> { get<WorkSchedulerImpl>() }
    single<ephyra.domain.library.service.LibraryUpdateScheduler> { get<WorkSchedulerImpl>() }
    single<ephyra.domain.library.service.MetadataUpdateScheduler> { get<WorkSchedulerImpl>() }

    single { PreferenceRestorer(androidApplication(), get(), get(), get(), get(), get(), get()) }
    single { MangaRestorer(get(), get(), get(), get(), get(), get(), get(), get()) }

    single { LibraryUpdateNotifier(androidApplication(), get(), get()) }
    single<ephyra.domain.library.service.LibraryUpdateNotifier> { get<LibraryUpdateNotifier>() }

    single { BackupNotifier(androidApplication()) }
    single<ephyra.domain.backup.service.BackupNotifier> { get<BackupNotifier>() }

    single { AppUpdateNotifier(androidApplication()) }
    single<ephyra.domain.release.service.AppUpdateNotifier> { get<AppUpdateNotifier>() }
    single<ephyra.domain.release.service.AppUpdateDownloader> { AppUpdateDownloaderImpl(androidApplication()) }

    single { ImageSaver(androidApplication()) }
    single<GlobalSearchScreenFactory> { GlobalSearchScreenFactory { query -> GlobalSearchScreen(query) } }
    single<MigrationConfigScreenFactory> {
        MigrationConfigScreenFactory { mangaIds -> MigrationConfigScreen(mangaIds) }
    }
    single<ExtensionReposScreenFactory> { ExtensionReposScreenFactory { url -> ExtensionReposScreen(url) } }
    single<NewUpdateScreenFactory> { NewUpdateScreenFactory { v, c, r, d -> NewUpdateScreen(v, c, r, d) } }
    single<OnboardingScreenFactory> { OnboardingScreenFactory { OnboardingScreen() } }
    single<MatchUnlinkedJobRunner> {
        object : MatchUnlinkedJobRunner {
            override fun isRunning(context: android.content.Context) = MatchUnlinkedJob.isRunning(context)
            override fun start(context: android.content.Context) = MatchUnlinkedJob.start(context)
        }
    }

    single { AndroidStorageFolderProvider(androidApplication()) }
    single { LocalSourceFileSystem(get()) }
    single { LocalCoverManager(androidApplication(), get()) }
    single { StorageManager(androidApplication(), get()) }
    single { MatchUnlinkedNotifier(get()) }
    worker { AppUpdateDownloadJob(get(), get(), get()) }
    worker { BackupCreateJob(get(), get(), get()) }
    worker { BackupRestoreJob(get(), get(), get()) }
    worker { DownloadJob(get(), get(), get(), get()) }
    worker { DelayedTrackingUpdateJob(get(), get(), get(), get(), get()) }
    worker { MatchUnlinkedJob(get(), get(), get()) }
    worker { MetadataUpdateJob(get(), get(), get(), get(), get(), get(), get()) }
    worker {
        LibraryUpdateJob(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
}
