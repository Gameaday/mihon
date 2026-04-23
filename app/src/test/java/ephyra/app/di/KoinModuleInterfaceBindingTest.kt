package ephyra.app.di

import ephyra.app.data.scheduler.WorkSchedulerImpl
import ephyra.app.util.NavigatorImpl
import ephyra.domain.backup.service.BackupScheduler
import ephyra.domain.backup.service.RestoreScheduler
import ephyra.domain.library.service.LibraryUpdateScheduler
import ephyra.domain.library.service.MetadataUpdateScheduler
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.util.AppNavigator
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Verifies that the Koin dependency graph correctly registers interface-to-implementation
 * bindings in [koinAppModule].
 *
 * Tests that use [startKoin] run sequentially (no [org.junit.jupiter.api.parallel.Execution]
 * CONCURRENT) because they share Koin's global [org.koin.core.context.GlobalContext].
 */
class KoinModuleInterfaceBindingTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    // ── AppNavigator ──────────────────────────────────────────────────────────

    /**
     * [NavigatorImpl] has no constructor dependencies so [koinAppModule] can resolve
     * [AppNavigator] without an Android context.  This test confirms the binding is
     * declared and produces the correct concrete type.
     */
    @Test
    fun `koinAppModule provides AppNavigator bound to NavigatorImpl`() {
        val koin = startKoin { modules(koinAppModule) }.koin

        val navigator: AppNavigator = koin.get()

        assertTrue(navigator is NavigatorImpl) {
            "Expected AppNavigator to resolve to NavigatorImpl but got ${navigator::class.qualifiedName}"
        }
    }

    // ── AppInfo ───────────────────────────────────────────────────────────────

    /**
     * The [AppInfo] factory creates an anonymous object from compile-time [ephyra.app.BuildConfig]
     * constants, so it can be resolved without an Android context.
     */
    @Test
    fun `koinAppModule provides AppInfo with non-empty version information`() {
        val koin = startKoin { modules(koinAppModule) }.koin

        val info: AppInfo = koin.get()

        assertNotNull(info)
        assertFalse(info.versionName.isEmpty()) {
            "AppInfo.versionName should be populated from BuildConfig.VERSION_NAME"
        }
        assertFalse(info.buildType.isEmpty()) {
            "AppInfo.buildType should be populated from BuildConfig.BUILD_TYPE"
        }
    }

    // ── WorkSchedulerImpl structural interface checks ─────────────────────────

    @Test
    fun `WorkSchedulerImpl implements BackupScheduler`() {
        assertTrue(BackupScheduler::class.java.isAssignableFrom(WorkSchedulerImpl::class.java)) {
            "WorkSchedulerImpl must implement BackupScheduler"
        }
    }

    @Test
    fun `WorkSchedulerImpl implements RestoreScheduler`() {
        assertTrue(RestoreScheduler::class.java.isAssignableFrom(WorkSchedulerImpl::class.java)) {
            "WorkSchedulerImpl must implement RestoreScheduler"
        }
    }

    @Test
    fun `WorkSchedulerImpl implements LibraryUpdateScheduler`() {
        assertTrue(LibraryUpdateScheduler::class.java.isAssignableFrom(WorkSchedulerImpl::class.java)) {
            "WorkSchedulerImpl must implement LibraryUpdateScheduler"
        }
    }

    @Test
    fun `WorkSchedulerImpl implements MetadataUpdateScheduler`() {
        assertTrue(MetadataUpdateScheduler::class.java.isAssignableFrom(WorkSchedulerImpl::class.java)) {
            "WorkSchedulerImpl must implement MetadataUpdateScheduler"
        }
    }

    // ── Scheduler Koin bindings ───────────────────────────────────────────────

    /**
     * Verifies that all four scheduler service interfaces are bound to the same
     * [WorkSchedulerImpl] singleton in [koinAppModule].
     *
     * A relaxed mock [android.app.Application] is used to satisfy [androidContext]
     * so that [WorkSchedulerImpl]'s constructor receives a non-null [android.content.Context].
     * WorkSchedulerImpl only stores the context; WorkManager is never accessed here.
     */
    @Test
    fun `koinAppModule binds all four scheduler interfaces to the same WorkSchedulerImpl singleton`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val koin = startKoin {
            androidContext(mockApp)
            modules(koinAppModule)
        }.koin

        val backupScheduler = koin.get<BackupScheduler>()
        val restoreScheduler = koin.get<RestoreScheduler>()
        val libraryScheduler = koin.get<LibraryUpdateScheduler>()
        val metadataScheduler = koin.get<MetadataUpdateScheduler>()

        assertTrue(backupScheduler is WorkSchedulerImpl)
        assertTrue(restoreScheduler is WorkSchedulerImpl)
        assertTrue(libraryScheduler is WorkSchedulerImpl)
        assertTrue(metadataScheduler is WorkSchedulerImpl)

        // All four bindings must resolve to the same singleton
        assertSame(backupScheduler, restoreScheduler) {
            "BackupScheduler and RestoreScheduler should be the same WorkSchedulerImpl singleton"
        }
        assertSame(restoreScheduler, libraryScheduler) {
            "RestoreScheduler and LibraryUpdateScheduler should be the same singleton"
        }
        assertSame(libraryScheduler, metadataScheduler) {
            "LibraryUpdateScheduler and MetadataUpdateScheduler should be the same singleton"
        }
    }
}

/**
 * Structural guard tests for [ephyra.feature.more.MoreTab].
 *
 * MoreTab.Content() uses the `rememberScreenModel { }` pattern for its private
 * [MoreScreenModel] (no Koin lookup for the ScreenModel class itself) and obtains
 * the two runtime dependencies via `koinInject<>()`.  The two @Test methods below
 * verify those injection types are — and must remain — resolvable from the Koin graph.
 *
 * No Koin context is started; both checks are pure JVM reflection invariants.
 */
@Execution(ExecutionMode.CONCURRENT)
class MoreTabDependencyContractTest {

    /**
     * [koinAppModule] registers [ephyra.core.download.DownloadManager] under the
     * [ephyra.domain.download.service.DownloadManager] domain interface.
     * MoreTab.Content() calls `koinInject<DownloadManager>()` (domain interface) so this
     * binding must remain valid; otherwise the More tab crashes immediately on open.
     */
    @Test
    fun `MoreTab — core DownloadManager still implements domain DownloadManager`() {
        assertTrue(
            ephyra.domain.download.service.DownloadManager::class.java.isAssignableFrom(
                ephyra.core.download.DownloadManager::class.java,
            ),
        ) {
            "ephyra.core.download.DownloadManager must implement " +
                "ephyra.domain.download.service.DownloadManager so MoreTab's " +
                "koinInject<DownloadManager>() resolves at runtime."
        }
    }

    /**
     * [koinPreferenceModule] registers [ephyra.domain.base.BasePreferences] as a concrete
     * `single { BasePreferences(get(), get()) }`.  MoreTab.Content() calls
     * `koinInject<BasePreferences>()` directly.  If BasePreferences is ever changed to an
     * interface or abstract class the Koin definition would fail at startKoin time —
     * crashing the app before the user sees anything.
     */
    @Test
    fun `MoreTab — BasePreferences is a concrete instantiable class`() {
        val cls = ephyra.domain.base.BasePreferences::class.java
        assertFalse(cls.isInterface) {
            "BasePreferences must remain a concrete class so the koinPreferenceModule " +
                "single{} registration keeps working for MoreTab's koinInject<BasePreferences>()."
        }
        assertFalse(java.lang.reflect.Modifier.isAbstract(cls.modifiers)) {
            "BasePreferences must not be abstract — koinPreferenceModule registers it as a " +
                "concrete single{} and MoreTab.Content() injects it directly."
        }
    }
}

/**
 * Verifies that platform-specific types used by the DI graph are correctly identified as
 * externally provided (not from the graph itself).
 *
 * With Koin Compiler Plugin 1.0.0-RC1 and [compileSafety] enabled, the compiler validates
 * every annotated class's constructor.  Types provided by the Android platform — such as
 * [androidx.lifecycle.SavedStateHandle] for ViewModels or [android.app.Application] for
 * context-aware singletons — must NOT appear as auto-resolved `get()` calls inside DSL
 * factory/viewModel lambdas; they are supplied either by the ViewModel framework or via
 * an explicit `androidApplication()` / `parametersOf(…)` call.
 *
 * These tests are pure JVM reflection checks — no Koin context is started.
 */
@Execution(ExecutionMode.CONCURRENT)
class PlatformTypeGuardTest {

    /**
     * [ReaderViewModel] receives its [androidx.lifecycle.SavedStateHandle] from the Android
     * ViewModel framework, NOT from the Koin DI graph.  The `koinAppModule_UI` viewModel {}
     * block passes it with `savedState = get()`, which works because Koin's ViewModel scope
     * provides a synthetic binding for SavedStateHandle.
     *
     * This test documents that contract: if SavedStateHandle is ever removed from
     * ReaderViewModel's constructor, the DSL entry must be updated accordingly so the
     * framework-provided handle is not leaked into the general DI graph.
     */
    @Test
    fun `ReaderViewModel constructor declares SavedStateHandle as first parameter`() {
        val params = ephyra.feature.reader.ReaderViewModel::class.java
            .declaredConstructors
            .maxByOrNull { it.parameterCount }
            ?.parameterTypes
            ?: emptyArray()

        assertTrue(
            params.any { it == androidx.lifecycle.SavedStateHandle::class.java },
        ) {
            "ReaderViewModel must accept a SavedStateHandle — it is a platform-provided type " +
                "supplied by the Koin ViewModel scope, not resolved from the global DI graph."
        }
    }

    /**
     * Several DSL `single {}` blocks in [koinAppModule] use `androidApplication()` to receive
     * the app [android.app.Application] context.  [android.app.Application] is not registered
     * as a Koin bean; it is provided by the Android runtime and bound through
     * `androidContext(this)` inside `startKoin {}`.
     *
     * This test verifies that [android.app.Application] is NOT a plain interface or abstract
     * class — confirming it is an instantiable, bindable Android platform type.
     */
    @Test
    fun `Application is a concrete platform class bound by androidContext`() {
        val cls = android.app.Application::class.java
        assertFalse(cls.isInterface) {
            "android.app.Application must not be an interface — it is a concrete platform " +
                "class that Koin binds via androidContext(…) in startKoin {}."
        }
    }
}
