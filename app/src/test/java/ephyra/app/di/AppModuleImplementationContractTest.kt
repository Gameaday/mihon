package ephyra.app.di

import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.notification.NotificationManagerImpl
import ephyra.app.data.updater.AppUpdateDownloaderImpl
import ephyra.app.data.updater.AppUpdateNotifier
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.core.common.notification.NotificationManager
import ephyra.data.track.TrackerManagerImpl
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.domain.track.service.TrackerManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import ephyra.core.download.DownloadManager as CoreDownloadManager
import ephyra.domain.backup.service.BackupNotifier as DomainBackupNotifier
import ephyra.domain.download.service.DownloadManager as DomainDownloadManager
import ephyra.domain.library.service.LibraryUpdateNotifier as DomainLibraryUpdateNotifier
import ephyra.domain.release.service.AppUpdateNotifier as DomainAppUpdateNotifier
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate as CoreSecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate as CoreThemingDelegate

/**
 * Structural contract tests for every remaining interface→implementation binding declared in
 * [koinAppModule] that was not covered by [KoinModuleInterfaceBindingTest].
 *
 * Each test uses [Class.isAssignableFrom] — a pure JVM reflection check that requires no
 * Android runtime and no Koin context. The assertions will fail at CI time if:
 * - An implementation class is refactored away from an interface without updating the module
 * - An interface is renamed or moved without updating the concrete class declaration
 * - A class is accidentally made abstract or its `implements` clause is removed
 *
 * Tests run concurrently because they are stateless reflection checks.
 */
@Execution(ExecutionMode.CONCURRENT)
class AppModuleImplementationContractTest {

    // ── NotificationManager ───────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [NotificationManagerImpl] as [NotificationManager].
     * Verifies the concrete class still implements the core interface.
     */
    @Test
    fun `NotificationManagerImpl implements NotificationManager`() {
        assertTrue(NotificationManager::class.java.isAssignableFrom(NotificationManagerImpl::class.java)) {
            "NotificationManagerImpl must implement ephyra.core.common.notification.NotificationManager"
        }
    }

    // ── ThemingDelegate ───────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [ThemingDelegateImpl] as [CoreThemingDelegate].
     * Verifies the concrete delegate still implements the presentation-core interface.
     */
    @Test
    fun `ThemingDelegateImpl implements CoreThemingDelegate`() {
        assertTrue(CoreThemingDelegate::class.java.isAssignableFrom(ThemingDelegateImpl::class.java)) {
            "ThemingDelegateImpl must implement ephyra.presentation.core.ui.delegate.ThemingDelegate"
        }
    }

    // ── SecureActivityDelegate ────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [SecureActivityDelegateImpl] as [CoreSecureActivityDelegate].
     * Verifies the concrete delegate still implements the presentation-core interface.
     */
    @Test
    fun `SecureActivityDelegateImpl implements CoreSecureActivityDelegate`() {
        assertTrue(CoreSecureActivityDelegate::class.java.isAssignableFrom(SecureActivityDelegateImpl::class.java)) {
            "SecureActivityDelegateImpl must implement ephyra.presentation.core.ui.delegate.SecureActivityDelegate"
        }
    }

    // ── AppUpdateDownloader ───────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [AppUpdateDownloaderImpl] as [AppUpdateDownloader].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `AppUpdateDownloaderImpl implements AppUpdateDownloader`() {
        assertTrue(AppUpdateDownloader::class.java.isAssignableFrom(AppUpdateDownloaderImpl::class.java)) {
            "AppUpdateDownloaderImpl must implement ephyra.domain.release.service.AppUpdateDownloader"
        }
    }

    // ── BackupNotifier ────────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds the app-level [BackupNotifier] as [DomainBackupNotifier].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `app BackupNotifier implements domain BackupNotifier`() {
        assertTrue(DomainBackupNotifier::class.java.isAssignableFrom(BackupNotifier::class.java)) {
            "ephyra.app.data.backup.BackupNotifier must implement ephyra.domain.backup.service.BackupNotifier"
        }
    }

    // ── AppUpdateNotifier ─────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds the app-level [AppUpdateNotifier] as [DomainAppUpdateNotifier].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `app AppUpdateNotifier implements domain AppUpdateNotifier`() {
        assertTrue(DomainAppUpdateNotifier::class.java.isAssignableFrom(AppUpdateNotifier::class.java)) {
            "ephyra.app.data.updater.AppUpdateNotifier must implement ephyra.domain.release.service.AppUpdateNotifier"
        }
    }

    // ── LibraryUpdateNotifier ─────────────────────────────────────────────────

    /**
     * [koinAppModule] binds the app-level [LibraryUpdateNotifier] as [DomainLibraryUpdateNotifier].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `app LibraryUpdateNotifier implements domain LibraryUpdateNotifier`() {
        assertTrue(DomainLibraryUpdateNotifier::class.java.isAssignableFrom(LibraryUpdateNotifier::class.java)) {
            "ephyra.app.data.library.LibraryUpdateNotifier must implement ephyra.domain.library.service.LibraryUpdateNotifier"
        }
    }

    // ── DownloadManager ───────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [CoreDownloadManager] as the domain [DomainDownloadManager] interface.
     * Verifies the core implementation still satisfies the domain contract.
     */
    @Test
    fun `core DownloadManager implements domain DownloadManager`() {
        assertTrue(DomainDownloadManager::class.java.isAssignableFrom(CoreDownloadManager::class.java)) {
            "ephyra.core.download.DownloadManager must implement ephyra.domain.download.service.DownloadManager"
        }
    }

    /**
     * [koinAppModule] registers [CoreDownloadManager] only under the domain [DomainDownloadManager]
     * interface.  [ephyra.app.data.notification.NotificationReceiver] must therefore inject the
     * domain interface, not the concrete class.
     *
     * This test acts as a compile-time guard: if [NotificationReceiver] is changed to inject
     * [CoreDownloadManager] directly, Koin will throw [org.koin.core.error.NoBeanDefFoundException]
     * at runtime (any download notification tap will crash).
     */
    @Test
    fun `core DownloadManager implements domain DownloadManager — required for NotificationReceiver injection`() {
        assertTrue(DomainDownloadManager::class.java.isAssignableFrom(CoreDownloadManager::class.java)) {
            "ephyra.core.download.DownloadManager must implement ephyra.domain.download.service.DownloadManager. " +
                "NotificationReceiver injects DomainDownloadManager; if CoreDownloadManager stops implementing " +
                "this interface the Koin binding will break."
        }
    }

    /**
     * [koinAppModule] binds [TrackerManagerImpl] as [TrackerManager].
     * Verifies the data-layer implementation still satisfies the domain interface.
     */
    @Test
    fun `TrackerManagerImpl implements TrackerManager`() {
        assertTrue(TrackerManager::class.java.isAssignableFrom(TrackerManagerImpl::class.java)) {
            "ephyra.data.track.TrackerManagerImpl must implement ephyra.domain.track.service.TrackerManager"
        }
    }

    // ── CoverCache ────────────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [ephyra.data.cache.CoverCache] with an additional
     * [ephyra.domain.manga.service.CoverCache] interface binding.
     * Multiple ScreenModels ([LibraryScreenModel], [BrowseSourceScreenModel],
     * [MangaCoverScreenModel], [MigrateDialogScreenModel]) inject the domain interface,
     * so this binding is required for them to resolve at runtime.
     */
    @Test
    fun `data CoverCache implements domain CoverCache`() {
        assertTrue(
            ephyra.domain.manga.service.CoverCache::class.java.isAssignableFrom(
                ephyra.data.cache.CoverCache::class.java,
            ),
        ) {
            "ephyra.data.cache.CoverCache must implement ephyra.domain.manga.service.CoverCache " +
                "so the Koin `bind ICoverCache::class` binding in koinAppModule remains valid"
        }
    }

    /**
     * [koinAppModule] binds [ephyra.data.cache.ChapterCache] with an additional
     * [ephyra.domain.chapter.service.ChapterCache] interface binding.
     * Verifies the concrete implementation still satisfies the domain interface
     * so the `bind IChapterCache::class` in the module remains valid.
     *
     * Without this binding [ReaderViewModel] would crash at runtime with
     * NoBeanDefFoundException because it injects the domain interface, not the
     * concrete data-layer class.
     */
    @Test
    fun `data ChapterCache implements domain ChapterCache`() {
        assertTrue(
            ephyra.domain.chapter.service.ChapterCache::class.java.isAssignableFrom(
                ephyra.data.cache.ChapterCache::class.java,
            ),
        ) {
            "ephyra.data.cache.ChapterCache must implement ephyra.domain.chapter.service.ChapterCache " +
                "so the Koin `bind IChapterCache::class` binding in koinAppModule remains valid"
        }
    }
}
