package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Atolla theme
 * System Hub aesthetic — bold, stable, industrial
 * Inspired by the Atolla jellyfish's concentric rings
 *
 * Key colors:
 * Primary Deep Sea Blue #1E3A5F
 * Secondary Amber #F59E0B
 * Tertiary Warm Orange #FB923C
 * Neutral #0F172A (dark) / #F8FAFC (light)
 *
 * Design philosophy:
 * - Blue-slate neutrals create a functional, "control panel" base
 * - Amber accent is high-visibility for action items and badges
 * - Wider surface container gaps create distinct panel boundaries
 *   that work with the 1dp border + 2dp elevation from BrandedThemeConfig
 * - Strong outline colors define clear element boundaries
 * - Designed for information density — every element has a defined edge
 */
internal object AtollaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF93C5FD),
        onPrimary = Color(0xFF0C2340),
        primaryContainer = Color(0xFF1E3A5F),
        onPrimaryContainer = Color(0xFFDBEAFE),
        inversePrimary = Color(0xFF1D4ED8),
        secondary = Color(0xFFFCD34D), // Unread badge — amber glow
        onSecondary = Color(0xFF451A03), // Unread badge text
        secondaryContainer = Color(0xFF92400E), // Navigation bar selector pill
        onSecondaryContainer = Color(0xFFFEF3C7), // Navigation bar selector icon
        tertiary = Color(0xFFFDBA74), // Downloaded badge — warm orange
        onTertiary = Color(0xFF431407), // Downloaded badge text
        tertiaryContainer = Color(0xFF9A3412),
        onTertiaryContainer = Color(0xFFFFEDD5),
        background = Color(0xFF0C1222),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF0C1222),
        onSurface = Color(0xFFE2E8F0),
        surfaceVariant = Color(0xFF1A2742),
        onSurfaceVariant = Color(0xFFCBD5E1),
        surfaceTint = Color(0xFF93C5FD),
        inverseSurface = Color(0xFFE2E8F0),
        inverseOnSurface = Color(0xFF0F172A),
        outline = Color(0xFF526580), // Strong — defines panel boundaries
        outlineVariant = Color(0xFF334155), // Secondary borders
        surfaceContainerLowest = Color(0xFF080D1A),
        surfaceContainerLow = Color(0xFF0F1729),
        surfaceContainer = Color(0xFF1A2742),
        surfaceContainerHigh = Color(0xFF243352),
        surfaceContainerHighest = Color(0xFF2F4063),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF1D4ED8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBFDBFE),
        onPrimaryContainer = Color(0xFF1E3A5F),
        inversePrimary = Color(0xFF93C5FD),
        secondary = Color(0xFFD97706), // Unread badge — amber
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFFDE68A), // Navigation bar selector pill
        onSecondaryContainer = Color(0xFF451A03), // Navigation bar selector icon
        tertiary = Color(0xFFEA580C), // Downloaded badge — orange
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFFED7AA),
        onTertiaryContainer = Color(0xFF431407),
        background = Color(0xFFF1F5F9),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFF1F5F9),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF334155),
        surfaceTint = Color(0xFF1D4ED8),
        inverseSurface = Color(0xFF1E293B),
        inverseOnSurface = Color(0xFFF1F5F9),
        outline = Color(0xFF94A3B8), // Clear boundaries
        outlineVariant = Color(0xFFCBD5E1), // Subtle separators
        surfaceContainerLowest = Color(0xFFDDE3EA),
        surfaceContainerLow = Color(0xFFE5EBF1),
        surfaceContainer = Color(0xFFEBF0F5),
        surfaceContainerHigh = Color(0xFFF1F5F9),
        surfaceContainerHighest = Color(0xFFF8FAFC),
    )
}
