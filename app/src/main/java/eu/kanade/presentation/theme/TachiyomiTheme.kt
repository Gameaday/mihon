package eu.kanade.presentation.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.AtollaColorScheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CatppuccinColorScheme
import eu.kanade.presentation.theme.colorscheme.EphyraColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NagareColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import tachiyomi.presentation.core.theme.AtollaThemeConfig
import tachiyomi.presentation.core.theme.BrandedThemeConfig
import tachiyomi.presentation.core.theme.EphyraThemeConfig
import tachiyomi.presentation.core.theme.LocalBrandedTheme
import tachiyomi.presentation.core.theme.NagareThemeConfig
import tachiyomi.presentation.core.theme.toShapes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme().get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled().get(),
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(appTheme, isAmoled, content)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val brandedConfig = remember(appTheme) { getBrandedThemeConfig(appTheme) }
    val shapes = remember(brandedConfig) { brandedConfig.toShapes() }

    CompositionLocalProvider(LocalBrandedTheme provides brandedConfig) {
        MaterialExpressiveTheme(
            colorScheme = remember(appTheme, isDark, isAmoled) {
                getThemeColorScheme(
                    context = context,
                    appTheme = appTheme,
                    isDark = isDark,
                    isAmoled = isAmoled,
                ).applyBrandedAlpha(brandedConfig)
            },
            typography = remember(brandedConfig) { buildBrandedTypography(brandedConfig) },
            shapes = shapes,
            content = content,
        )
    }
}

private fun getThemeColorScheme(
    context: Context,
    appTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(context)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isDark = isDark,
        isAmoled = isAmoled,
        overrideDarkSurfaceContainers = appTheme != AppTheme.MONET,
    )
}

/**
 * Applies the branded theme's surface alpha tokens to the color scheme.
 *
 * Glassmorphic themes (Ephyra) use reduced alpha on surface and container
 * colors to create a frosted-glass layering effect. Themes with alpha = 1.0
 * (Nagare, Atolla, defaults) pass through unchanged.
 */
private fun ColorScheme.applyBrandedAlpha(config: BrandedThemeConfig): ColorScheme {
    if (config.surfaceAlpha >= 1.0f && config.containerAlpha >= 1.0f) return this
    return copy(
        surface = surface.copy(alpha = config.surfaceAlpha),
        surfaceVariant = surfaceVariant.copy(alpha = config.surfaceAlpha),
        surfaceContainerLowest = surfaceContainerLowest.copy(alpha = config.containerAlpha),
        surfaceContainerLow = surfaceContainerLow.copy(alpha = config.containerAlpha),
        surfaceContainer = surfaceContainer.copy(alpha = config.containerAlpha),
        surfaceContainerHigh = surfaceContainerHigh.copy(alpha = config.containerAlpha),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = config.containerAlpha),
    )
}

/**
 * Builds a [Typography] that respects the branded theme's font weight tokens.
 *
 * Each branded theme can customize:
 * - **headingWeight**: Applied to display, headline, and title styles.
 *   Ephyra uses SemiBold for modern clarity through glass, Nagare uses
 *   Medium for understated zen elegance, Atolla uses Bold for authority.
 * - **bodyWeight**: Applied to body and label styles. Most themes use Normal;
 *   Atolla uses Medium for increased readability in dense layouts.
 *
 * Non-branded themes (headingWeight=SemiBold, bodyWeight=Normal) match the
 * default Material 3 typography, so there is no visual change.
 */
private fun buildBrandedTypography(config: BrandedThemeConfig): Typography {
    val defaults = Typography()
    val hw = config.headingWeight
    val bw = config.bodyWeight
    // Skip customization when weights match M3 defaults
    if (hw == FontWeight.SemiBold && bw == FontWeight.Normal) return defaults
    return Typography(
        displayLarge = defaults.displayLarge.copy(fontWeight = hw),
        displayMedium = defaults.displayMedium.copy(fontWeight = hw),
        displaySmall = defaults.displaySmall.copy(fontWeight = hw),
        headlineLarge = defaults.headlineLarge.copy(fontWeight = hw),
        headlineMedium = defaults.headlineMedium.copy(fontWeight = hw),
        headlineSmall = defaults.headlineSmall.copy(fontWeight = hw),
        titleLarge = defaults.titleLarge.copy(fontWeight = hw),
        titleMedium = defaults.titleMedium.copy(fontWeight = hw),
        titleSmall = defaults.titleSmall.copy(fontWeight = hw),
        bodyLarge = defaults.bodyLarge.copy(fontWeight = bw),
        bodyMedium = defaults.bodyMedium.copy(fontWeight = bw),
        bodySmall = defaults.bodySmall.copy(fontWeight = bw),
        labelLarge = defaults.labelLarge.copy(fontWeight = bw),
        labelMedium = defaults.labelMedium.copy(fontWeight = bw),
        labelSmall = defaults.labelSmall.copy(fontWeight = bw),
    )
}

/**
 * Returns the [BrandedThemeConfig] for the given [AppTheme].
 *
 * Only branded themes (Ephyra, Nagare, Atolla) have custom configs.
 * All other themes use the default config which preserves the existing
 * Mihon shape system.
 */
private fun getBrandedThemeConfig(appTheme: AppTheme): BrandedThemeConfig = when (appTheme) {
    AppTheme.EPHYRA -> EphyraThemeConfig
    AppTheme.NAGARE -> NagareThemeConfig
    AppTheme.ATOLLA -> AtollaThemeConfig
    else -> BrandedThemeConfig() // Default — uses MihonShapes radii
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CATPPUCCIN to CatppuccinColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
    AppTheme.EPHYRA to EphyraColorScheme,
    AppTheme.NAGARE to NagareColorScheme,
    AppTheme.ATOLLA to AtollaColorScheme,
)
