package ephyra.presentation.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Per-theme design tokens that go beyond Material 3 color schemes.
 *
 * Each branded theme (Ephyra, Nagare, Atolla) defines its own configuration
 * to express a distinct visual identity through shapes, spacing, surface
 * transparency, typography weight, and borders — not just color swaps.
 *
 * Components read these tokens via [LocalBrandedTheme] to adapt their
 * appearance to the active branded theme.
 */
@Immutable
data class BrandedThemeConfig(
    /** Human-readable theme identity name. */
    val name: String = "Default",

    // --- Shape tokens ---

    /** Corner radius for library cards and manga covers. */
    val cardCornerRadius: Dp = 16.dp,

    /** Corner radius for cover image thumbnails. */
    val coverImageCornerRadius: Dp = 12.dp,

    /** Corner radius for badges (unread, download, etc.). */
    val badgeCornerRadius: Dp = 50.dp,

    /** Corner radius for bottom sheets. */
    val sheetCornerRadius: Dp = 28.dp,

    /** Corner radius for dialogs. */
    val dialogCornerRadius: Dp = 28.dp,

    // --- Spacing & elevation tokens ---

    /** Elevation for card-like surfaces (0.dp = flat/glassmorphic). */
    val cardElevation: Dp = 0.dp,

    /** Border width for cards (0.dp = no border). */
    val cardBorderWidth: Dp = 0.dp,

    /** Horizontal grid spacing between library items. */
    val gridHorizontalSpacing: Dp = 8.dp,

    /** Vertical grid spacing between library items. */
    val gridVerticalSpacing: Dp = 8.dp,

    // --- Surface & transparency tokens ---

    /**
     * Alpha for card/surface backgrounds (1.0 = fully opaque, 0.0 = fully transparent).
     * Glassmorphic themes (Ephyra) use lower values for a frosted-glass effect.
     * Industrial themes (Atolla) use 1.0 for solid, structured panels.
     */
    val surfaceAlpha: Float = 1.0f,

    /**
     * Alpha for container backgrounds (e.g., surfaceContainer, surfaceContainerHigh).
     * Allows glassmorphic themes to create depth through transparency layers.
     */
    val containerAlpha: Float = 1.0f,

    // --- Typography tokens ---

    /**
     * Font weight for headings and titles.
     * Zen/clean themes (Nagare) use Medium for understated elegance.
     * Industrial themes (Atolla) use Bold for authority and structure.
     * Glassmorphic themes (Ephyra) use SemiBold for modern clarity.
     */
    val headingWeight: FontWeight = FontWeight.SemiBold,

    /**
     * Font weight for body text and labels.
     * Most themes use Normal. Industrial themes may use Medium for readability.
     */
    val bodyWeight: FontWeight = FontWeight.Normal,

    // --- Content padding tokens ---

    /**
     * Inner padding for card content (e.g., title text inside a library card).
     * Glassmorphic themes use more padding for a spacious, airy feel.
     * Industrial themes use tighter padding for information density.
     */
    val cardContentPadding: Dp = 8.dp,
)

/**
 * CompositionLocal providing the current [BrandedThemeConfig].
 *
 * Defaults to the base/default config. Branded themes (Ephyra, Nagare, Atolla)
 * override this with their specific design tokens.
 */
val LocalBrandedTheme = staticCompositionLocalOf { BrandedThemeConfig() }

// ── Pre-defined branded theme configurations ──────────────────────────

/**
 * **Ephyra** — Glassmorphic aesthetic.
 *
 * Translucent, modern, premium. Inspired by the translucent bell of a young
 * jellyfish. Larger corner radii for a soft, airy feel. No card borders —
 * the "frosted glass" effect comes from translucent surface alphas layered
 * over the color scheme's gradient backgrounds.
 *
 * Key traits:
 * - Large, rounded shapes (20dp cards, 32dp sheets)
 * - Translucent surfaces (0.85 alpha) for glassmorphic depth
 * - No elevation, no borders — depth through transparency
 * - Generous spacing for a spacious, breathable layout
 * - SemiBold headings for modern clarity through glass
 */
val EphyraThemeConfig = BrandedThemeConfig(
    name = "Ephyra",
    cardCornerRadius = 20.dp,
    coverImageCornerRadius = 16.dp,
    badgeCornerRadius = 50.dp,
    sheetCornerRadius = 32.dp,
    dialogCornerRadius = 32.dp,
    cardElevation = 0.dp,
    cardBorderWidth = 0.dp,
    gridHorizontalSpacing = 10.dp,
    gridVerticalSpacing = 10.dp,
    surfaceAlpha = 0.85f,
    containerAlpha = 0.78f,
    headingWeight = FontWeight.SemiBold,
    bodyWeight = FontWeight.Normal,
    cardContentPadding = 10.dp,
)

/**
 * **Nagare** — Minimalist Zen aesthetic.
 *
 * Fluid, lightweight, effortless. Very Material You. Clean, tight spacing
 * with moderate corner radii. No borders, no elevation — pure content focus.
 * The most "standard" Material 3 of the branded themes, but refined:
 * lighter heading weight and tighter spacing create a calm, focused feel.
 *
 * Key traits:
 * - Moderate shapes (12dp cards, 24dp sheets) — clean M3 proportions
 * - Fully opaque surfaces — no transparency gimmicks
 * - No elevation, no borders — minimalist clarity
 * - Tight, efficient spacing for content density
 * - Medium weight headings for understated elegance
 */
val NagareThemeConfig = BrandedThemeConfig(
    name = "Nagare",
    cardCornerRadius = 12.dp,
    coverImageCornerRadius = 8.dp,
    badgeCornerRadius = 50.dp,
    sheetCornerRadius = 24.dp,
    dialogCornerRadius = 24.dp,
    cardElevation = 0.dp,
    cardBorderWidth = 0.dp,
    gridHorizontalSpacing = 6.dp,
    gridVerticalSpacing = 6.dp,
    surfaceAlpha = 1.0f,
    containerAlpha = 1.0f,
    headingWeight = FontWeight.Medium,
    bodyWeight = FontWeight.Normal,
    cardContentPadding = 6.dp,
)

/**
 * **Atolla** — System Hub / Industrial aesthetic.
 *
 * Bold, stable, structured. High contrast with crisp borders and tight,
 * square-ish corners. Cards feel tactile and organized like a control panel.
 * Designed for users who want maximum information density and clear visual
 * hierarchy — every element has a defined boundary.
 *
 * Key traits:
 * - Sharp shapes (6dp cards, 16dp sheets) — angular, industrial
 * - Subtle elevation (2dp) for tactile depth
 * - 1dp borders for crisp panel definition
 * - Fully opaque surfaces — solid, functional panels
 * - Bold headings for authority and structure
 */
val AtollaThemeConfig = BrandedThemeConfig(
    name = "Atolla",
    cardCornerRadius = 6.dp,
    coverImageCornerRadius = 4.dp,
    badgeCornerRadius = 4.dp,
    sheetCornerRadius = 16.dp,
    dialogCornerRadius = 16.dp,
    cardElevation = 2.dp,
    cardBorderWidth = 1.dp,
    gridHorizontalSpacing = 8.dp,
    gridVerticalSpacing = 8.dp,
    surfaceAlpha = 1.0f,
    containerAlpha = 1.0f,
    headingWeight = FontWeight.Bold,
    bodyWeight = FontWeight.Medium,
    cardContentPadding = 6.dp,
)

// ── Shape helpers ────────────────────────────────────────────────────

/**
 * Builds Material 3 [Shapes] from the current [BrandedThemeConfig].
 *
 * Branded themes adjust the global shape system so that all M3 components
 * (cards, chips, sheets, dialogs) reflect the theme's visual identity.
 */
fun BrandedThemeConfig.toShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(badgeCornerRadius.coerceAtMost(8.dp)),
    small = RoundedCornerShape(coverImageCornerRadius),
    medium = RoundedCornerShape(cardCornerRadius),
    large = RoundedCornerShape(cardCornerRadius + 4.dp),
    extraLarge = RoundedCornerShape(dialogCornerRadius),
)

/**
 * Card shape derived from the branded theme config.
 */
val BrandedThemeConfig.cardShape: Shape
    get() = RoundedCornerShape(cardCornerRadius)

/**
 * Cover image shape derived from the branded theme config.
 */
val BrandedThemeConfig.coverImageShape: Shape
    get() = RoundedCornerShape(coverImageCornerRadius)

/**
 * Badge shape derived from the branded theme config.
 */
val BrandedThemeConfig.badgeShape: Shape
    get() = RoundedCornerShape(badgeCornerRadius)

/**
 * Sheet shape derived from the branded theme config.
 */
val BrandedThemeConfig.sheetShape: Shape
    get() = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius)

/**
 * Dialog shape derived from the branded theme config.
 */
val BrandedThemeConfig.dialogShape: Shape
    get() = RoundedCornerShape(dialogCornerRadius)
