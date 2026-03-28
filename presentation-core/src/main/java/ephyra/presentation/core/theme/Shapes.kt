package ephyra.presentation.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Default expressive shape system for Ephyra.
 *
 * Uses larger, more organic corner radii than Material 3 defaults to create
 * a softer, more distinctive visual identity. Branded themes (Ephyra, Nagare,
 * Atolla) override the global shapes via [BrandedThemeConfig.toShapes].
 */
val EphyraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Extended shape tokens for components that need non-standard corner radii.
 *
 * When a branded theme is active, the card / coverImage / sheet / dialog
 * shapes are derived from [LocalBrandedTheme] so each theme can express a
 * unique visual identity (e.g., sharp rectangles for Atolla, soft rounds
 * for Ephyra, clean moderate radii for Nagare).
 */
object ShapeTokens {

    /** Pill shape for chips, badges, and status indicators. */
    val pill = RoundedCornerShape(percent = 50)

    /** Card shape — delegates to the branded theme config. */
    val card: Shape
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandedTheme.current.cardShape

    /** Sheet shape — delegates to the branded theme config. */
    val sheet: Shape
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandedTheme.current.sheetShape

    /** Dialog shape — delegates to the branded theme config. */
    val dialog: Shape
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandedTheme.current.dialogShape

    /** Image shape for cover thumbnails — delegates to the branded theme config. */
    val coverImage: Shape
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandedTheme.current.coverImageShape

    /** Badge shape — delegates to the branded theme config. */
    val badge: Shape
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandedTheme.current.badgeShape
}

/**
 * Convenience accessor for extended shape tokens from [MaterialTheme].
 */
val MaterialTheme.shapeTokens: ShapeTokens
    @Composable
    @ReadOnlyComposable
    get() = ShapeTokens

