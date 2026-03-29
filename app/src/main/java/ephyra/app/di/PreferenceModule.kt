package ephyra.app.di

import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.app.util.system.isDebugBuildType
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.base.BasePreferences
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.reader.model.*
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.domain.updates.service.UpdatesPreferences
import ephyra.feature.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import org.koin.android.ext.koin.androidApplication
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
    single {
        SourcePreferences(get())
    }
    single {
        SecurityPreferences(get())
    }
    single {
        PrivacyPreferences(get())
    }
    single {
        LibraryPreferences(get())
    }
    single {
        UpdatesPreferences(get())
    }
    single {
        ReaderPreferences(get())
    }
    single {
        TrackPreferences(get())
    }
    single {
        DownloadPreferences(get())
    }
    single {
        BackupPreferences(get())
    }
    single {
        StoragePreferences(
            folderProvider = get<AndroidStorageFolderProvider>(),
            preferenceStore = get(),
        )
    }
    single {
        UiPreferences(get())
    }
    single {
        BasePreferences(androidApplication(), get())
    }
}
