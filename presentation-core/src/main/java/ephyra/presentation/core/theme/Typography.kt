package ephyra.presentation.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Existing extension ---

val Typography.header: TextStyle
    @Composable
    get() = bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = LocalBrandedTheme.current.headingWeight,
    )

// --- Expressive extensions ---

/**
 * Bold display style for hero sections and splash content.
 * Respects the branded theme's heading weight for consistent visual identity.
 */
val Typography.displayEmphasis: TextStyle
    @Composable
    get() = displaySmall.copy(
        fontWeight = LocalBrandedTheme.current.headingWeight,
        letterSpacing = (-0.5).sp,
    )

/**
 * Prominent title style for screen headers and key section headings.
 * Respects the branded theme's heading weight for consistent visual identity.
 */
val Typography.titleEmphasis: TextStyle
    @Composable
    get() = titleLarge.copy(
        fontWeight = LocalBrandedTheme.current.headingWeight,
        letterSpacing = (-0.25).sp,
    )

/**
 * Compact section label for category dividers and grouped content.
 * Respects the branded theme's heading weight for consistent visual identity.
 */
val Typography.sectionLabel: TextStyle
    @Composable
    get() = labelLarge.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = LocalBrandedTheme.current.headingWeight,
        letterSpacing = 0.5.sp,
    )

/**
 * Subtle metadata style for timestamps, counts, and auxiliary info.
 */
val Typography.metadata: TextStyle
    @Composable
    get() = bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.25.sp,
    )

/**
 * Compact label for badge counts and small indicators.
 */
val Typography.badgeLabel: TextStyle
    get() = labelSmall.copy(fontSize = 10.sp)
