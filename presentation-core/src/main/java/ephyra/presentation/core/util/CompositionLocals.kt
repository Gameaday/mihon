package ephyra.presentation.core.util

import androidx.compose.runtime.compositionLocalOf
import ephyra.domain.ui.UiPreferences

val LocalUiPreferences = compositionLocalOf<UiPreferences> {
    error("No UiPreferences provided")
}
