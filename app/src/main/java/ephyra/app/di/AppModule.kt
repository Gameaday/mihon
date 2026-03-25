package ephyra.app.di

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.app.BuildConfig
import ephyra.app.data.cache.ChapterCache
import ephyra.app.data.cache.CoverCache
import ephyra.app.data.download.DownloadCache
import ephyra.app.data.download.DownloadManager
import ephyra.app.data.download.DownloadProvider
import ephyra.app.data.saver.ImageSaver
import ephyra.app.data.track.TrackerManager
import ephyra.app.extension.ExtensionManager
import eu.kanade.ephyra.network.JavaScriptEngine
import eu.kanade.ephyra.network.NetworkHelper
import eu.kanade.ephyra.source.AndroidSourceManager
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
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory<SqlDriver> {
            AndroidSqliteDriver(
                schema = Database.Schema,
                context = app,
                name = "tachiyomi.db",
                factory = if (BuildConfig.DEBUG) {
                    // Support database inspector in Android Studio
                    FrameworkSQLiteOpenHelperFactory()
                } else {
                    RequerySQLiteOpenHelperFactory()
                },
                callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        // Enable incremental auto-vacuum so the database file shrinks
                        // as pages are freed by deletions. For new databases this takes
                        // effect immediately. For existing databases already in a
                        // different auto-vacuum mode, this PRAGMA alone does NOT change
                        // the mode — a full VACUUM would be required. However, we still
                        // set it so that any future fresh install or database recreation
                        // picks up the setting automatically. The incremental_vacuum in
                        // onOpen is only effective once the database is actually in
                        // incremental mode.
                        setPragma(db, "auto_vacuum = INCREMENTAL")
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        setPragma(db, "foreign_keys = ON")
                        setPragma(db, "journal_mode = WAL")
                        setPragma(db, "synchronous = NORMAL")
                        // Use memory for temporary tables instead of disk
                        setPragma(db, "temp_store = MEMORY")
                        // 8 MB page cache (negative = KB) — helps with large library views
                        setPragma(db, "cache_size = -8192")
                        // Memory-mapped I/O for faster reads (64 MB)
                        setPragma(db, "mmap_size = 67108864")
                        // Reclaim up to 256 free pages (~1 MB) left by previous sessions'
                        // deletions, so the database file doesn't grow unboundedly over time.
                        setPragma(db, "incremental_vacuum(256)")
                        // Run expensive maintenance PRAGMAs on a background thread
                        // to avoid adding cold-start latency. wal_checkpoint(TRUNCATE)
                        // and optimize can be slow on large databases but are safe to
                        // run concurrently under WAL mode.
                        Thread {
                            try {
                                // Flush any leftover WAL frames from a previous unclean
                                // shutdown into the main database file.
                                setPragma(db, "wal_checkpoint(TRUNCATE)")
                                // Let SQLite re-analyze tables whose stats are stale,
                                // keeping query plans optimal as the library grows.
                                setPragma(db, "optimize")
                            } catch (_: Exception) {
                                // Non-critical maintenance — swallow failures silently.
                            }
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
        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                ),
            )
        }
        addSingletonFactory<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { ChapterCache(app, get()) }
        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<SourceManager> { AndroidSourceManager(app, get(), get()) }
        addSingletonFactory { ExtensionManager(app) }

        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { DownloadCache(app) }

        addSingletonFactory { TrackerManager() }
        addSingletonFactory { DelayedTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }
        addSingletonFactory { LocalSourceFileSystem(get()) }
        addSingletonFactory { LocalCoverManager(app, get()) }
        addSingletonFactory { StorageManager(app, get()) }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DownloadManager>()
        }
    }
}
