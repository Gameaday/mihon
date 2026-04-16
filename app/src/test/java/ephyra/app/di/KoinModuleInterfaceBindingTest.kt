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
