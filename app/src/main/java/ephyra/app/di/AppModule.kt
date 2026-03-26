package ephyra.app.di

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.app.BuildConfig
import ephyra.app.data.cache.ChapterCache
import ephyra.app.data.cache.CoverCache
import ephyra.app.data.backup.BackupDecoder
import ephyra.app.data.backup.BackupFileValidator
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.app.data.backup.restore.BackupRestorer
import ephyra.app.data.backup.restore.restorers.CategoriesRestorer
import ephyra.app.data.backup.restore.restorers.ExtensionRepoRestorer
import ephyra.app.data.backup.create.BackupCreator
import ephyra.app.data.backup.create.creators.CategoriesBackupCreator
import ephyra.app.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.app.data.backup.create.creators.MangaBackupCreator
import ephyra.app.data.backup.create.creators.PreferenceBackupCreator
import ephyra.app.data.backup.create.creators.SourcesBackupCreator
import ephyra.app.data.download.DownloadCache
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.extension.util.ExtensionLoader
import ephyra.app.util.CrashLogUtil
import ephyra.app.data.download.DownloadNotifier
import ephyra.app.data.download.DownloadPendingDeleter
import ephyra.app.data.download.Downloader
import ephyra.app.data.saver.ImageSaver
import ephyra.app.data.coil.MangaCoverKeyer
import ephyra.app.data.coil.MangaKeyer
import ephyra.app.ui.base.delegate.ThemingDelegate
import ephyra.app.ui.base.delegate.SecureActivityDelegate
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.domain.track.interactor.GetExtensionRepo
import ephyra.domain.track.interactor.UpdateExtensionRepo
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.data.AndroidDatabaseHandler
import ephyra.data.Database
import ephyra.data.DatabaseHandler
import ephyra.data.DateColumnAdapter
import ephyra.data.History
import ephyra.data.Mangas
import ephyra.data.StringListColumnAdapter
import ephyra.data.UpdateStrategyColumnAdapter
import ephyra.domain.source.service.SourceManager
import ephyra.domain.storage.service.StorageManager
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import ephyra.app.data.download.DownloadJob
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.library.MetadataUpdateJob
import ephyra.app.data.updater.AppUpdateDownloadJob
import ephyra.domain.track.service.DelayedTrackingUpdateJob
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val koinAppModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = Database.Schema,
            context = androidApplication(),
            name = "tachiyomi.db",
            factory = if (BuildConfig.DEBUG) {
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    setPragma(db, "auto_vacuum = INCREMENTAL")
                }
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                    setPragma(db, "temp_store = MEMORY")
                    setPragma(db, "cache_size = -8192")
                    setPragma(db, "mmap_size = 67108864")
                    setPragma(db, "incremental_vacuum(256)")
                    Thread {
                        try {
                            setPragma(db, "wal_checkpoint(TRUNCATE)")
                            setPragma(db, "optimize")
                        } catch (_: Exception) {}
                    }.apply { name = "db-maintenance" }.start()
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )
    }

    single {
        Database(
            driver = get(),
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
            ),
        )
    }

    single<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

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
    single { ChapterCache(androidApplication(), get()) }
    single { CoverCache(androidApplication()) }
    single { MangaKeyer() }
    single<ThemingDelegate> { ThemingDelegateImpl(get()) }
    single<SecureActivityDelegate> { SecureActivityDelegateImpl(get(), get()) }
    single { MangaCoverKeyer(get()) }
    single { MangaCoverFetcher.MangaFactory(lazy { get<okhttp3.Call.Factory>() }, get(), get()) }
    single { MangaCoverFetcher.MangaCoverFactory(lazy { get<okhttp3.Call.Factory>() }, get(), get()) }

    single { NetworkHelper(androidApplication(), get()) }
    single { JavaScriptEngine(androidApplication()) }
    single { ExtensionApi(get(), get(), get(), get(), get(), get(), get()) }

    single<SourceManager> { AndroidSourceManager(androidApplication(), get(), get(), get(), get(), get()) }
    single { ExtensionManager(androidApplication(), get(), get(), get(), get(), get()) }

    single { DownloadStore(androidApplication(), get(), get(), get(), get()) }
    single { DownloadProvider(androidApplication(), get(), get()) }
    single { DownloadCache(androidApplication(), get(), get(), get(), get()) }
    single { DownloadManager(androidApplication(), get(), get(), get(), get(), get(), get(), get()) }
    single { Downloader(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DownloadPendingDeleter(androidApplication(), get()) }
    single { DownloadNotifier(androidApplication(), get()) }

    single { TrackerManager(androidApplication(), get(), get(), get(), get(), get()) }
    single { DelayedTrackingStore(androidApplication()) }

    single { BackupDecoder(androidApplication(), get()) }
    single { BackupFileValidator(androidApplication(), get(), get(), get()) }

    single { CategoriesBackupCreator(get()) }
    single { MangaBackupCreator(get(), get(), get()) }
    single { PreferenceBackupCreator(get(), get()) }
    single { ExtensionRepoBackupCreator(get()) }
    single { SourcesBackupCreator(get()) }
    single { BackupCreator(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    single { CategoriesRestorer(get(), get(), get()) }
    single { BackupRestorer(androidApplication(), get(), get(), get(), get()) }
    single { AppUpdateChecker(get()) }

    single { PreferenceRestorer(androidApplication(), get(), get(), get(), get()) }
    single { MangaRestorer(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { LibraryUpdateNotifier(androidApplication(), get(), get()) }
    single { BackupNotifier(androidApplication(), get()) }

    single { ImageSaver(androidApplication()) }

    single { AndroidStorageFolderProvider(androidApplication()) }
    single { LocalSourceFileSystem(get()) }
    single { LocalCoverManager(androidApplication(), get()) }
    single { StorageManager(androidApplication(), get()) }
    worker { AppUpdateDownloadJob(get(), get(), get()) }
    worker { BackupCreateJob(get(), get(), get(), get(), get(), get()) }
    worker { BackupRestoreJob(get(), get(), get(), get()) }
    worker { DownloadJob(get(), get(), get(), get()) }
    worker { DelayedTrackingUpdateJob(get(), get(), get(), get(), get()) }
    worker { MetadataUpdateJob(get(), get(), get(), get(), get(), get(), get()) }
    worker { 
        LibraryUpdateJob(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        ) 
    }
}
