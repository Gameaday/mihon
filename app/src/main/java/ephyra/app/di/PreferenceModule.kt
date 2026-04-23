package ephyra.app.di

import ephyra.app.installer.AndroidInstallerCapabilityProvider
import ephyra.app.util.system.isDebugBuildType
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.reader.model.*
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.updates.service.UpdatesPreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val koinPreferenceModule = module {
    single<PreferenceStore> {
        DataStorePreferenceStore(androidApplication())
    }
    single {
        NetworkPreferences(
            preferenceStore = get(),
            verboseLogging = isDebugBuildType,
        )
    }
    singleOf(::SourcePreferences)
    singleOf(::SecurityPreferences)
    singleOf(::PrivacyPreferences)
    singleOf(::LibraryPreferences)
    singleOf(::UpdatesPreferences)
    singleOf(::ReaderPreferences)
    singleOf(::TrackPreferences)
    singleOf(::DownloadPreferences)
    singleOf(::BackupPreferences)
    single<ephyra.domain.base.InstallerCapabilityProvider> {
        AndroidInstallerCapabilityProvider(androidApplication())
    }
}
