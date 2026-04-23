package ephyra.app.di

import android.app.Application
import ephyra.app.installer.AndroidInstallerCapabilityProvider
import ephyra.app.util.system.isDebugBuildType
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.base.InstallerCapabilityProvider
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.updates.service.UpdatesPreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Annotation-based Koin module for all preference singletons.
 *
 * All types here take only [PreferenceStore] (or [Application] / [android.content.Context])
 * as dependencies, so this module is the base layer of the preference graph.
 *
 * Replaces the legacy [koinPreferenceModule] DSL block to make these types visible to the
 * Koin Compiler Plugin's compile-time safety checker.
 */
@Module
class PreferenceAnnotatedModule {

    @Single
    fun preferenceStore(app: Application): PreferenceStore = DataStorePreferenceStore(app)

    @Single
    fun networkPreferences(preferenceStore: PreferenceStore): NetworkPreferences =
        NetworkPreferences(preferenceStore = preferenceStore, verboseLogging = isDebugBuildType)

    @Single
    fun sourcePreferences(preferenceStore: PreferenceStore): SourcePreferences =
        SourcePreferences(preferenceStore)

    @Single
    fun securityPreferences(preferenceStore: PreferenceStore): SecurityPreferences =
        SecurityPreferences(preferenceStore)

    @Single
    fun privacyPreferences(preferenceStore: PreferenceStore): PrivacyPreferences =
        PrivacyPreferences(preferenceStore)

    @Single
    fun libraryPreferences(preferenceStore: PreferenceStore): LibraryPreferences =
        LibraryPreferences(preferenceStore)

    @Single
    fun updatesPreferences(preferenceStore: PreferenceStore): UpdatesPreferences =
        UpdatesPreferences(preferenceStore)

    @Single
    fun readerPreferences(preferenceStore: PreferenceStore): ReaderPreferences =
        ReaderPreferences(preferenceStore)

    @Single
    fun trackPreferences(preferenceStore: PreferenceStore): TrackPreferences =
        TrackPreferences(preferenceStore)

    @Single
    fun downloadPreferences(preferenceStore: PreferenceStore): DownloadPreferences =
        DownloadPreferences(preferenceStore)

    @Single
    fun backupPreferences(preferenceStore: PreferenceStore): BackupPreferences =
        BackupPreferences(preferenceStore)

    @Single
    fun installerCapabilityProvider(app: Application): InstallerCapabilityProvider =
        AndroidInstallerCapabilityProvider(app)
}
