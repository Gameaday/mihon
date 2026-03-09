package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Nagare theme
 * Minimalist Zen aesthetic — fluid, lightweight, effortless
 * "Flow" — content flowing from web to server to screen
 *
 * Key colors:
 * Primary Charcoal #374151
 * Secondary Mint #10B981
 * Tertiary Sage #6EE7B7
 * Neutral #F9FAFB (light) / #111827 (dark)
 *
 * Design philosophy:
 * - Strong M3 surface hierarchy with clean contrast steps
 * - Charcoal neutrals with warm undertones for comfortable reading
 * - Mint accent is vivid but not loud — "understated elegance"
 * - Outline colors are deliberately subtle for minimal visual noise
 * - Tight surface container steps create refined layering without
 *   transparency effects — pure Material 3 expression
 */
internal object NagareColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF6EE7B7),
        onPrimary = Color(0xFF064E3B),
        primaryContainer = Color(0xFF065F46),
        onPrimaryContainer = Color(0xFFD1FAE5),
        inversePrimary = Color(0xFF059669),
        secondary = Color(0xFF6EE7B7), // Unread badge — mint
        onSecondary = Color(0xFF064E3B), // Unread badge text
        secondaryContainer = Color(0xFF065F46), // Navigation bar selector pill
        onSecondaryContainer = Color(0xFFD1FAE5), // Navigation bar selector icon
        tertiary = Color(0xFF9CA3AF), // Downloaded badge — neutral grey
        onTertiary = Color(0xFF1F2937), // Downloaded badge text
        tertiaryContainer = Color(0xFF374151),
        onTertiaryContainer = Color(0xFFF3F4F6),
        background = Color(0xFF0F1419),
        onBackground = Color(0xFFF3F4F6),
        surface = Color(0xFF0F1419),
        onSurface = Color(0xFFF3F4F6),
        surfaceVariant = Color(0xFF1C2430),
        onSurfaceVariant = Color(0xFFD1D5DB),
        surfaceTint = Color(0xFF6EE7B7),
        inverseSurface = Color(0xFFF3F4F6),
        inverseOnSurface = Color(0xFF111827),
        outline = Color(0xFF3D4B5C), // Subtle — keeps focus on content
        outlineVariant = Color(0xFF283444), // Very subtle separators
        surfaceContainerLowest = Color(0xFF0B0F14),
        surfaceContainerLow = Color(0xFF121821),
        surfaceContainer = Color(0xFF1C2430),
        surfaceContainerHigh = Color(0xFF242E3C),
        surfaceContainerHighest = Color(0xFF2E3A4A),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF059669),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA7F3D0),
        onPrimaryContainer = Color(0xFF064E3B),
        inversePrimary = Color(0xFF6EE7B7),
        secondary = Color(0xFF059669), // Unread badge — mint
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFA7F3D0), // Navigation bar selector pill
        onSecondaryContainer = Color(0xFF064E3B), // Navigation bar selector icon
        tertiary = Color(0xFF6B7280), // Downloaded badge — neutral grey
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFE5E7EB),
        onTertiaryContainer = Color(0xFF1F2937),
        background = Color(0xFFF9FAFB),
        onBackground = Color(0xFF111827),
        surface = Color(0xFFF9FAFB),
        onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFF0F2F4),
        onSurfaceVariant = Color(0xFF374151),
        surfaceTint = Color(0xFF059669),
        inverseSurface = Color(0xFF1F2937),
        inverseOnSurface = Color(0xFFF9FAFB),
        outline = Color(0xFFD1D5DB), // Clean, light border
        outlineVariant = Color(0xFFE5E7EB), // Very subtle
        surfaceContainerLowest = Color(0xFFECEEF0),
        surfaceContainerLow = Color(0xFFF0F1F3),
        surfaceContainer = Color(0xFFF3F4F6),
        surfaceContainerHigh = Color(0xFFF7F8F9),
        surfaceContainerHighest = Color(0xFFFCFCFD),
    )
}
