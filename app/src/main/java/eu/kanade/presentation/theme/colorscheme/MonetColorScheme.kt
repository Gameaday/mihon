package eu.kanade.presentation.theme.colorscheme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.DynamicScheme
import com.materialkolor.toColorScheme

internal class MonetColorScheme(context: Context) : BaseColorScheme() {

    override val lightScheme = dynamicLightColorScheme(context)
    override val darkScheme = dynamicDarkColorScheme(context)
}

internal class MonetCompatColorScheme(seed: Color) : BaseColorScheme() {
    override val lightScheme = generateColorSchemeFromSeed(seed = seed, dark = false)
    override val darkScheme = generateColorSchemeFromSeed(seed = seed, dark = true)

    companion object {
        fun generateColorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
            return DynamicScheme(
                seedColor = seed,
                isDark = dark,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                style = PaletteStyle.Expressive,
            )
                .toColorScheme(isAmoled = false)
        }
    }
}
