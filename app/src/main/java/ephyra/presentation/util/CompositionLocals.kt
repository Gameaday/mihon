package ephyra.presentation.util

import androidx.compose.runtime.compositionLocalOf
import ephyra.domain.ui.UiPreferences

val LocalUiPreferences = compositionLocalOf<UiPreferences> {
    error("No UiPreferences provided")
}

val LocalBasePreferences = compositionLocalOf<ephyra.domain.base.BasePreferences> {
    error("No BasePreferences provided")
}

val LocalPrivacyPreferences = compositionLocalOf<ephyra.app.core.security.PrivacyPreferences> {
    error("No PrivacyPreferences provided")
}

val LocalLibraryPreferences = compositionLocalOf<ephyra.domain.library.service.LibraryPreferences> {
    error("No LibraryPreferences provided")
}

val LocalStoragePreferences = compositionLocalOf<ephyra.domain.storage.service.StoragePreferences> {
    error("No StoragePreferences provided")
}
