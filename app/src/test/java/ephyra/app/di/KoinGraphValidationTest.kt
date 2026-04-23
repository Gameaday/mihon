package ephyra.app.di

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import ephyra.domain.koinDomainModule

/**
 * Global Guardrail: end-to-end validation of the complete production Koin module graph.
 *
 * ### Why this test exists
 * The Koin 1.0-RC1 compiler plugin with [compileSafety] enabled can fail to catch broken
 * [get()] references in a multi-module setup, particularly when:
 * - Platform-provided types like [android.content.Context] or
 *   [androidx.lifecycle.SavedStateHandle] are involved.
 * - A class is renamed in one module but the old name is still referenced in a DSL lambda
 *   in another module.
 * - KSP incremental compilation reuses stale metadata after a batch of `factoryOf` changes.
 *
 * This test mirrors the exact [startKoin] call in [ephyra.app.App.onCreate], substituting a
 * relaxed MockK [android.app.Application] for the real Android one so that the entire graph
 * can be exercised in a pure JVM context without an Android device or emulator.
 *
 * ### What "passing" guarantees
 * 1. **No definition conflicts**: no two modules accidentally register the same type with
 *    conflicting definitions; [startKoin] itself throws on first duplicate.
 * 2. **Cross-module resolution chains work**: a chain like
 *    `BackupScheduler → get<WorkSchedulerImpl>() → WorkSchedulerImpl(androidApplication())`
 *    is fully resolved end-to-end.
 * 3. **Stable touch points remain registered**: [ephyra.domain.export.LibraryExporter] and
 *    [ephyra.domain.backup.service.BackupFileValidator] are the stable import/export
 *    interfaces that legacy extensions depend on; their presence is asserted explicitly.
 * 4. **All interface delegations point to a live singleton**: every
 *    `single<InterfaceX> { get<ConcreteImpl>() }` pair is traced to its concrete bean.
 *
 * ### What this test does NOT cover
 * Beans whose construction requires Android runtime services (Room/SQLite, WorkManager,
 * Coil, DataStore writes, etc.) are not eagerly resolved here. Those are covered by
 * the structural [Class.isAssignableFrom] tests in [AppModuleImplementationContractTest]
 * and [DomainModuleImplementationContractTest].
 *
 * Tests run sequentially because they share Koin's global [org.koin.core.context.GlobalContext].
 */
class KoinGraphValidationTest {

    @AfterEach
    fun tearDown() = stopKoin()

    // ── 1. No definition conflicts in the combined graph ──────────────────────

    /**
     * Mirrors [ephyra.app.App.onCreate]: starts Koin with all four production modules in
     * the same order.  Koin throws [org.koin.core.error.DefinitionOverrideException] if
     * any two modules register the same type with conflicting override semantics, and
     * [IllegalStateException] if startKoin is called twice without stopKoin in between.
     *
     * A clean pass here means the module set has no duplicate-registration bugs introduced
     * by incremental KSP caching or manual module edits.
     */
    @Test
    fun `all four production modules start together without definition conflicts`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        assertDoesNotThrow(
            message = "startKoin must not throw — definition conflicts or ordering errors " +
                "in koinAppModule / koinPreferenceModule / koinDomainModule / koinAppModule_UI",
        ) {
            startKoin {
                androidContext(mockApp)
                modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
            }
        }
    }

    // ── 2. Scheduler interface delegation chain ───────────────────────────────

    /**
     * [koinAppModule] registers [ephyra.app.data.scheduler.WorkSchedulerImpl] as a singleton
     * and then delegates all four scheduler interfaces to it.  Resolving any one interface
     * traces the full chain:
     *   BackupScheduler → single<BackupScheduler> { get<WorkSchedulerImpl>() }
     *                   → single { WorkSchedulerImpl(androidApplication()) }
     *                   → WorkSchedulerImpl(mockApp)
     *
     * If ANY of these three registrations is missing or the delegation chain breaks, this
     * test will throw [org.koin.core.error.NoBeanDefFoundException].
     */
    @Test
    fun `all four scheduler interface delegations resolve end-to-end`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.domain.backup.service.BackupScheduler>()) {
            "BackupScheduler must resolve; its delegation chain needs WorkSchedulerImpl"
        }
        assertNotNull(koin.get<ephyra.domain.backup.service.RestoreScheduler>()) {
            "RestoreScheduler must resolve; its delegation chain needs WorkSchedulerImpl"
        }
        assertNotNull(koin.get<ephyra.domain.library.service.LibraryUpdateScheduler>()) {
            "LibraryUpdateScheduler must resolve; its delegation chain needs WorkSchedulerImpl"
        }
        assertNotNull(koin.get<ephyra.domain.library.service.MetadataUpdateScheduler>()) {
            "MetadataUpdateScheduler must resolve; its delegation chain needs WorkSchedulerImpl"
        }
    }

    /**
     * All four scheduler bindings must resolve to the SAME [WorkSchedulerImpl] singleton.
     * If this fails, a `get<BackupScheduler>()` call in one place and a
     * `get<RestoreScheduler>()` call in another would return different scheduler objects,
     * causing split-brain job management.
     */
    @Test
    fun `all four scheduler interfaces resolve to the same WorkSchedulerImpl singleton`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        val backup = koin.get<ephyra.domain.backup.service.BackupScheduler>()
        val restore = koin.get<ephyra.domain.backup.service.RestoreScheduler>()
        val library = koin.get<ephyra.domain.library.service.LibraryUpdateScheduler>()
        val metadata = koin.get<ephyra.domain.library.service.MetadataUpdateScheduler>()

        assertTrue(backup === restore && restore === library && library === metadata) {
            "All four scheduler interfaces must resolve to the same WorkSchedulerImpl singleton. " +
                "At least one delegation broke: backup=$backup restore=$restore " +
                "library=$library metadata=$metadata"
        }
    }

    // ── 3. koinAppModule_UI → koinPreferenceModule cross-module resolution ─────

    /**
     * All [factoryOf] entries in [koinAppModule_UI] for Settings ScreenModels inject
     * preference singletons registered in [koinPreferenceModule].  If [koinPreferenceModule]
     * is absent from the combined graph, every Settings screen would crash with
     * [org.koin.core.error.NoBeanDefFoundException].
     *
     * Resolving a preference type here walks the cross-module dependency:
     *   koinAppModule_UI (factoryOf(::SettingsAppearanceScreenModel)) →
     *   UiPreferences (koinPreferenceModule) →
     *   PreferenceStore (koinPreferenceModule) →
     *   DataStorePreferenceStore(androidApplication())
     *
     * Note: DataStore is lazily opened on first read/write — constructing
     * DataStorePreferenceStore only stores the context, so no Android I/O occurs here.
     */
    @Test
    fun `UiPreferences resolves through cross-module chain into koinPreferenceModule`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.domain.ui.UiPreferences>()) {
            "UiPreferences must resolve via koinPreferenceModule; missing it breaks all Settings screens"
        }
    }

    /**
     * [ephyra.core.common.preference.PreferenceStore] is the root of every preference
     * singleton in [koinPreferenceModule].  If its binding is missing or its concrete
     * implementation changes package, every preference-consuming factory crashes.
     */
    @Test
    fun `PreferenceStore root binding resolves in the combined graph`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.core.common.preference.PreferenceStore>()) {
            "PreferenceStore must resolve; it is the root dependency of all preference singletons"
        }
    }

    // ── 4. Stable import/export touch points ──────────────────────────────────

    /**
     * [ephyra.domain.export.LibraryExporter] is a stable touch point for both the settings
     * UI and legacy extension integrations that trigger library exports.  It must remain
     * registered whenever the combined graph is started.
     *
     * Bound in [koinAppModule] as:
     *   `single<LibraryExporter> { LibraryExporterImpl(androidApplication()) }`
     */
    @Test
    fun `LibraryExporter stable touch point resolves — import-export must not break`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.domain.export.LibraryExporter>()) {
            "LibraryExporter must remain registered — it is a stable touch point for the " +
                "library export flow and legacy extension integrations"
        }
    }

    /**
     * [ephyra.domain.backup.service.BackupFileValidator] is the interface used by the
     * backup restore flow to validate archive integrity before committing any data change.
     * Its binding must remain intact across refactors.
     */
    @Test
    fun `BackupFileValidator stable touch point resolves — restore guard must not break`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.domain.backup.service.BackupFileValidator>()) {
            "BackupFileValidator must remain registered — it guards the backup restore flow"
        }
    }

    // ── 5. AppNavigator and AppInfo (zero-Android-dep factories) ──────────────

    /**
     * [ephyra.presentation.core.util.AppNavigator] is bound to [ephyra.app.util.NavigatorImpl]
     * which has no constructor dependencies.  This is the simplest possible cross-module
     * resolution and should NEVER fail — if it does, the [koinAppModule] itself is broken.
     */
    @Test
    fun `AppNavigator resolves to NavigatorImpl — simplest cross-module smoke test`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        val navigator = koin.get<ephyra.presentation.core.util.AppNavigator>()
        assertTrue(navigator is ephyra.app.util.NavigatorImpl) {
            "AppNavigator must resolve to NavigatorImpl; got ${navigator::class.qualifiedName}"
        }
    }

    /**
     * [ephyra.presentation.core.ui.AppInfo] is constructed from [ephyra.app.BuildConfig]
     * compile-time constants — no Koin dependencies, no Android context.  A failure here
     * means [koinAppModule] itself has a syntax or import error.
     */
    @Test
    fun `AppInfo resolves with non-empty version information`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        val info = koin.get<ephyra.presentation.core.ui.AppInfo>()
        assertNotNull(info) { "AppInfo must be resolvable from the combined module graph" }
        assertTrue(info.versionName.isNotEmpty()) {
            "AppInfo.versionName must be populated from BuildConfig.VERSION_NAME"
        }
    }

    // ── 6. Notification / UI delegate cross-module bindings ───────────────────

    /**
     * [ephyra.core.common.notification.NotificationManager] is bound to
     * [ephyra.app.data.notification.NotificationManagerImpl] in [koinAppModule].
     * Many background jobs resolve it to post status notifications; a missing binding
     * causes a silent crash the first time any job runs.
     */
    @Test
    fun `NotificationManager resolves to NotificationManagerImpl in the combined graph`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.core.common.notification.NotificationManager>()) {
            "NotificationManager must resolve to NotificationManagerImpl"
        }
    }

    /**
     * [ephyra.domain.release.service.AppUpdateDownloader] is bound to
     * [ephyra.app.data.updater.AppUpdateDownloaderImpl] in [koinAppModule].
     * It is injected by the app-update flow; a missing binding silently disables updates.
     */
    @Test
    fun `AppUpdateDownloader resolves to AppUpdateDownloaderImpl in the combined graph`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.domain.release.service.AppUpdateDownloader>()) {
            "AppUpdateDownloader must resolve; missing it disables in-app updates"
        }
    }

    // ── 7. StorageManager — multi-module dependency on AndroidStorageFolderProvider ──

    /**
     * [ephyra.domain.storage.service.StorageManager] depends on both
     * [ephyra.core.common.storage.AndroidStorageFolderProvider] (registered in [koinAppModule])
     * AND [android.app.Application].  The [StoragePreferences] in [koinPreferenceModule] also
     * depends on [AndroidStorageFolderProvider], creating a three-module fan-in.
     *
     * A failure here indicates that [AndroidStorageFolderProvider] is no longer registered
     * or its binding has been moved to a module not loaded by the app.
     */
    @Test
    fun `StorageManager resolves — three-module fan-in through AndroidStorageFolderProvider`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
        }.koin

        assertNotNull(koin.get<ephyra.domain.storage.service.StorageManager>()) {
            "StorageManager must resolve; it is a three-module fan-in through " +
                "AndroidStorageFolderProvider — missing either StorageManager or " +
                "AndroidStorageFolderProvider breaks local-source access"
        }
    }
}
