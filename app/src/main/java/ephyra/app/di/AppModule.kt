package ephyra.app.di

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.app.BuildConfig
import ephyra.app.data.cache.ChapterCache
import ephyra.app.data.cache.CoverCache
import ephyra.app.data.backup.restore.restorers.ExtensionRepoRestorer
import ephyra.app.data.backup.restore.restorers.MangaRestorer
import ephyra.app.data.backup.restore.restorers.PreferenceRestorer
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.app.data.backup.create.BackupCreator
import ephyra.app.data.backup.create.creators.CategoriesBackupCreator
import ephyra.app.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.app.data.backup.create.creators.MangaBackupCreator
import ephyra.app.data.backup.create.creators.PreferenceBackupCreator
import ephyra.app.data.backup.create.creators.SourcesBackupCreator
import ephyra.app.data.download.DownloadCache
import ephyra.app.data.saver.ImageSaver
import ephyra.app.data.track.TrackerManager
import ephyra.app.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
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

    single { ChapterCache(androidApplication(), get()) }
    single { CoverCache(androidApplication()) }

    single { NetworkHelper(androidApplication(), get()) }
    single { JavaScriptEngine(androidApplication()) }

    single<SourceManager> { AndroidSourceManager(androidApplication(), get(), get()) }
    single { ExtensionManager(androidApplication()) }

    single { DownloadStore(androidApplication(), get(), get(), get(), get()) }
    single { DownloadProvider(androidApplication(), get(), get()) }
    single { DownloadCache(androidApplication(), get(), get(), get(), get()) }
    single { DownloadManager(androidApplication(), get(), get(), get(), get(), get(), get(), get()) }

    single { TrackerManager() }
    single { DelayedTrackingStore(androidApplication()) }

    single { CategoriesBackupCreator(get()) }
    single { MangaBackupCreator(get(), get(), get()) }
    single { PreferenceBackupCreator(get(), get()) }
    single { ExtensionRepoBackupCreator(get()) }
    single { SourcesBackupCreator(get()) }
    single { BackupCreator(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    single { PreferenceRestorer(androidApplication(), get(), get()) }
    single { ExtensionRepoRestorer(get(), get()) }
    single { MangaRestorer(get(), get(), get(), get(), get(), get(), get(), get()) }

    single { ImageSaver(androidApplication()) }

    single { AndroidStorageFolderProvider(androidApplication()) }
    single { LocalSourceFileSystem(get()) }
    single { LocalCoverManager(androidApplication(), get()) }
    single { StorageManager(androidApplication(), get()) }
    worker { BackupCreateJob(get(), get(), get(), get(), get()) }
    worker { DownloadJob(get(), get(), get(), get()) }
    worker { DelayedTrackingUpdateJob(get(), get(), get(), get(), get()) }
    worker { 
        LibraryUpdateJob(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        ) 
    }
}
