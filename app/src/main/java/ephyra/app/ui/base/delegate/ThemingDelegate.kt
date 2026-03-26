package ephyra.app.ui.base.delegate

import android.app.Activity
import ephyra.domain.ui.UiPreferences
import ephyra.domain.ui.model.AppTheme
import ephyra.app.R
import ephyra.presentation.core.ui.delegate.ThemingDelegate as CoreThemingDelegate

class ThemingDelegateImpl(
    private val uiPreferences: UiPreferences,
) : CoreThemingDelegate {
    override fun applyAppTheme(activity: Activity) {
        getThemeResIds(uiPreferences.appTheme().get(), uiPreferences.themeDarkAmoled().get())
            .forEach(activity::setTheme)
    }

    companion object {
        private val themeResources: Map<AppTheme, Int> = mapOf(
            AppTheme.MONET to R.style.Theme_Tachiyomi_Monet,
            AppTheme.CATPPUCCIN to R.style.Theme_Tachiyomi_Catppuccin,
            AppTheme.GREEN_APPLE to R.style.Theme_Tachiyomi_GreenApple,
            AppTheme.LAVENDER to R.style.Theme_Tachiyomi_Lavender,
            AppTheme.MIDNIGHT_DUSK to R.style.Theme_Tachiyomi_MidnightDusk,
            AppTheme.MONOCHROME to R.style.Theme_Tachiyomi_Monochrome,
            AppTheme.NORD to R.style.Theme_Tachiyomi_Nord,
            AppTheme.STRAWBERRY_DAIQUIRI to R.style.Theme_Tachiyomi_StrawberryDaiquiri,
            AppTheme.TAKO to R.style.Theme_Tachiyomi_Tako,
            AppTheme.TEALTURQUOISE to R.style.Theme_Tachiyomi_TealTurquoise,
            AppTheme.YINYANG to R.style.Theme_Tachiyomi_YinYang,
            AppTheme.YOTSUBA to R.style.Theme_Tachiyomi_Yotsuba,
            AppTheme.TIDAL_WAVE to R.style.Theme_Tachiyomi_TidalWave,
            AppTheme.EPHYRA to R.style.Theme_Tachiyomi_Ephyra,
            AppTheme.NAGARE to R.style.Theme_Tachiyomi_Nagare,
            AppTheme.ATOLLA to R.style.Theme_Tachiyomi_Atolla,
        )

        fun getThemeResIds(appTheme: AppTheme, isAmoled: Boolean): List<Int> {
            return buildList(2) {
                add(themeResources.getOrDefault(appTheme, R.style.Theme_Tachiyomi))
                if (isAmoled) add(R.style.ThemeOverlay_Tachiyomi_Amoled)
            }
        }
    }
}
