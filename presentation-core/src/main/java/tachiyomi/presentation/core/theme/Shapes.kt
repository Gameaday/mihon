package tachiyomi.presentation.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Expressive shape system for Mihon.
 *
 * Uses larger, more organic corner radii than Material 3 defaults to create
 * a softer, more distinctive visual identity. These shapes are applied globally
 * via [MaterialTheme.shapes].
 */
val MihonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Extended shape tokens for components that need non-standard corner radii.
 */
object ShapeTokens {

    /** Pill shape for chips, badges, and status indicators. */
    val pill = RoundedCornerShape(percent = 50)

    /** Card shape for library items and manga covers. */
    val card get() = MihonShapes.medium

    /** Sheet shape for bottom sheets and modal surfaces. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Dialog shape for alert dialogs and confirmation popups. */
    val dialog get() = MihonShapes.extraLarge

    /** Image shape for cover thumbnails. */
    val coverImage get() = MihonShapes.small
}

/**
 * Convenience accessor for extended shape tokens from [MaterialTheme].
 */
val MaterialTheme.shapeTokens: ShapeTokens
    @Composable
    @ReadOnlyComposable
    get() = ShapeTokens
