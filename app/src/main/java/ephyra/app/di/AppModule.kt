package ephyra.app.di

import android.app.DownloadManager
import androidx.room.*
import ephyra.data.backup.BackupDecoder
import ephyra.data.backup.BackupFileValidator
import ephyra.app.data.backup.BackupNotifier
import ephyra.data.backup.create.BackupCreator
import ephyra.data.backup.create.creators.CategoriesBackupCreator
import ephyra.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.data.backup.create.creators.MangaBackupCreator
import ephyra.data.backup.create.creators.PreferenceBackupCreator
import ephyra.data.backup.create.creators.SourcesBackupCreator
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.data.backup.restore.BackupRestorer
import ephyra.data.backup.restore.restorers.CategoriesRestorer
import ephyra.data.cache.ChapterCache
import ephyra.data.cache.CoverCache
import ephyra.data.coil.MangaCoverKeyer
import ephyra.data.coil.MangaKeyer
import ephyra.core.download.DownloadCache
import ephyra.core.download.DownloadJob
import ephyra.core.download.DownloadNotifier
import ephyra.core.download.DownloadPendingDeleter
import ephyra.core.download.Downloader
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.library.MetadataUpdateJob
import ephyra.data.saver.ImageSaver
import ephyra.app.data.updater.AppUpdateDownloadJob
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.extension.util.ExtensionLoader
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.presentation.core.util.CrashLogUtil
import ephyra.presentation.core.ui.AppInfo
import ephyra.app.BuildConfig as AppBuildConfig
import ephyra.app.track.MatchUnlinkedJob
import ephyra.app.track.MatchUnlinkedNotifier
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.data.room.EphyraDatabase
import ephyra.data.track.TrackerManagerImpl
import ephyra.domain.source.service.SourceManager
import ephyra.domain.storage.service.StorageManager
import ephyra.domain.track.service.DelayedTrackingUpdateJob
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate as CoreSecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate as CoreThemingDelegate
import ephyra.app.data.scheduler.WorkSchedulerImpl
import ephyra.data.updater.AppUpdateChecker
import ephyra.data.backup.restore.restorers.MangaRestorer
import ephyra.data.backup.restore.restorers.PreferenceRestorer
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.core.download.DownloadProvider
import ephyra.core.download.DownloadStore
import ephyra.app.data.updater.AppUpdateNotifier
import nl.adaptivity.xmlutil.serialization.XML

val koinAppModule = module {
    single {
        Room.databaseBuilder(
            context = androidApplication(),
            klass = EphyraDatabase::class.java,
            name = "tachiyomi.db"
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
    single<ephyra.presentation.core.util.Navigator> { ephyra.app.util.NavigatorImpl() }
    single<ephyra.core.common.notification.NotificationManager> { ephyra.app.data.notification.NotificationManagerImpl(get()) }
    single<CoreThemingDelegate> { ThemingDelegateImpl(get()) }
    single<CoreSecureActivityDelegate> { SecureActivityDelegateImpl(get(), get()) }
    single { MangaCoverKeyer(get()) }

    single { NetworkHelper(androidApplication(), get()) }
    single { JavaScriptEngine(androidApplication()) }
    single { ExtensionApi(get(), get(), get(), get(), get(), get(), get()) }

    single<SourceManager> { AndroidSourceManager(androidApplication(), get(), get(), get(), get(), get()) }
    single { ExtensionManager(androidApplication(), get(), get(), get(), get(), get()) }

    single { DownloadStore(androidApplication(), get(), get(), get(), get()) }
    single { DownloadProvider(androidApplication(), get()) }
    single { DownloadCache(androidApplication(), get(), get()) }
    single<ephyra.domain.download.service.DownloadManager> { ephyra.core.download.DownloadManager(androidApplication(), get(), get(), get(), get(), get(), get()) }
    single { Downloader(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
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
    single { BackupRestorer(androidApplication(), get(), get(), get(), get(), get()) }
    single { AppUpdateChecker(get(), get()) }

    single { WorkSchedulerImpl(androidApplication()) }
    single<ephyra.domain.backup.service.BackupScheduler> { get<WorkSchedulerImpl>() }
    single<ephyra.domain.library.service.LibraryUpdateScheduler> { get<WorkSchedulerImpl>() }

    single { PreferenceRestorer(androidApplication(), get(), get(), get(), get(), get(), get()) }
    single { MangaRestorer(get(), get(), get(), get(), get(), get(), get(), get()) }

    single { LibraryUpdateNotifier(androidApplication(), get(), get()) }
    single<ephyra.domain.library.service.LibraryUpdateNotifier> { get<LibraryUpdateNotifier>() }

    single { BackupNotifier(androidApplication()) }
    single<ephyra.domain.backup.service.BackupNotifier> { get<BackupNotifier>() }

    single { AppUpdateNotifier(androidApplication()) }
    single<ephyra.domain.release.service.AppUpdateNotifier> { get<AppUpdateNotifier>() }

    single { ImageSaver(androidApplication()) }

    single { AndroidStorageFolderProvider(androidApplication()) }
    single { LocalSourceFileSystem(get()) }
    single { LocalCoverManager(androidApplication(), get()) }
    single { StorageManager(androidApplication(), get()) }
    single { MatchUnlinkedNotifier(get()) }
    worker { AppUpdateDownloadJob(get(), get(), get()) }
    worker { BackupCreateJob(get(), get(), get()) }
    worker { BackupRestoreJob(get(), get(), get()) }
    worker { DownloadJob(get(), get(), get()) }
    worker { DelayedTrackingUpdateJob(get(), get(), get(), get(), get()) }
    worker { MatchUnlinkedJob(get(), get(), get()) }
    worker { MetadataUpdateJob(get(), get(), get(), get(), get(), get(), get()) }
    worker {
        LibraryUpdateJob(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
}
