package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Ephyra theme
 * Glassmorphic aesthetic — translucent, modern, premium
 * Inspired by the translucent bell of a young jellyfish
 *
 * Key colors:
 * Primary Electric Indigo #4F46E5
 * Secondary Cyan #0891B2
 * Tertiary Violet #7C3AED
 * Neutral #1E1B4B (dark) / #EEF2FF (light)
 */
internal object EphyraColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA5B4FC),
        onPrimary = Color(0xFF1E1B4B),
        primaryContainer = Color(0xFF3730A3),
        onPrimaryContainer = Color(0xFFE0E7FF),
        inversePrimary = Color(0xFF4F46E5),
        secondary = Color(0xFF67E8F9), // Unread badge
        onSecondary = Color(0xFF083344), // Unread badge text
        secondaryContainer = Color(0xFF155E75), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFCFFAFE), // Navigation bar selector icon
        tertiary = Color(0xFFC4B5FD), // Downloaded badge
        onTertiary = Color(0xFF2E1065), // Downloaded badge text
        tertiaryContainer = Color(0xFF5B21B6),
        onTertiaryContainer = Color(0xFFEDE9FE),
        background = Color(0xFF0F0D23),
        onBackground = Color(0xFFE0E7FF),
        surface = Color(0xFF0F0D23),
        onSurface = Color(0xFFE0E7FF),
        surfaceVariant = Color(0xFF1A1744),
        onSurfaceVariant = Color(0xFFC7D2FE),
        surfaceTint = Color(0xFFA5B4FC),
        inverseSurface = Color(0xFFE0E7FF),
        inverseOnSurface = Color(0xFF1E1B4B),
        outline = Color(0xFF6366F1),
        surfaceContainerLowest = Color(0xFF0C0A1D),
        surfaceContainerLow = Color(0xFF110F28),
        surfaceContainer = Color(0xFF1A1744),
        surfaceContainerHigh = Color(0xFF211E50),
        surfaceContainerHighest = Color(0xFF28255C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF4F46E5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC7D2FE),
        onPrimaryContainer = Color(0xFF1E1B4B),
        inversePrimary = Color(0xFFA5B4FC),
        secondary = Color(0xFF0891B2), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFA5F3FC), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF083344), // Navigation bar selector icon
        tertiary = Color(0xFF7C3AED), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFDDD6FE),
        onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFF5F3FF),
        onBackground = Color(0xFF1E1B4B),
        surface = Color(0xFFF5F3FF),
        onSurface = Color(0xFF1E1B4B),
        surfaceVariant = Color(0xFFEEF2FF),
        onSurfaceVariant = Color(0xFF3730A3),
        surfaceTint = Color(0xFF4F46E5),
        inverseSurface = Color(0xFF1E1B4B),
        inverseOnSurface = Color(0xFFE0E7FF),
        outline = Color(0xFF818CF8),
        surfaceContainerLowest = Color(0xFFE8E5F8),
        surfaceContainerLow = Color(0xFFECEAFA),
        surfaceContainer = Color(0xFFEEF2FF),
        surfaceContainerHigh = Color(0xFFF1F0FF),
        surfaceContainerHighest = Color(0xFFF8F7FF),
    )
}
