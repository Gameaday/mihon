package ephyra.presentation.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ephyra.presentation.core.components.material.DISABLED_ALPHA
import ephyra.presentation.core.components.material.SECONDARY_ALPHA

val ColorScheme.active: Color
    @Composable
    get() {
        return if (isSystemInDarkTheme()) Color(255, 235, 59) else Color(255, 193, 7)
    }

/**
 * Primary content color at secondary (de-emphasized) alpha.
 * Useful for subtitles and auxiliary text sitting alongside primary-emphasis content.
 */
val ColorScheme.onSurfaceSecondary: Color
    get() = onSurface.copy(alpha = SECONDARY_ALPHA)

/**
 * Primary content color at disabled alpha.
 * Use for placeholder text, disabled controls, and unavailable items.
 */
val ColorScheme.onSurfaceDisabled: Color
    get() = onSurface.copy(alpha = DISABLED_ALPHA)
