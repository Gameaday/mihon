package ephyra.app.di

import android.app.Application
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.app.core.security.PrivacyPreferences
import ephyra.app.core.security.SecurityPreferences
import eu.kanade.ephyra.network.NetworkPreferences
import ephyra.app.ui.reader.setting.ReaderPreferences
import ephyra.app.util.system.isDebugBuildType
import ephyra.core.common.preference.AndroidPreferenceStore
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.domain.updates.service.UpdatesPreferences
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class PreferenceModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(app)
        }
        addSingletonFactory {
            NetworkPreferences(
                preferenceStore = get(),
                verboseLogging = isDebugBuildType,
            )
        }
        addSingletonFactory {
            SourcePreferences(get())
        }
        addSingletonFactory {
            SecurityPreferences(get())
        }
        addSingletonFactory {
            PrivacyPreferences(get())
        }
        addSingletonFactory {
            LibraryPreferences(get())
        }
        addSingletonFactory {
            UpdatesPreferences(get())
        }
        addSingletonFactory {
            ReaderPreferences(get())
        }
        addSingletonFactory {
            TrackPreferences(get())
        }
        addSingletonFactory {
            DownloadPreferences(get())
        }
        addSingletonFactory {
            BackupPreferences(get())
        }
        addSingletonFactory {
            StoragePreferences(
                folderProvider = get<AndroidStorageFolderProvider>(),
                preferenceStore = get(),
            )
        }
        addSingletonFactory {
            UiPreferences(get())
        }
        addSingletonFactory {
            BasePreferences(app, get())
        }
    }
}
