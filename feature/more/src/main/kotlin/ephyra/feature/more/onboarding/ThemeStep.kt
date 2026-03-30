package ephyra.feature.more.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import ephyra.domain.ui.model.setAppCompatDelegateThemeMode
import ephyra.feature.settings.widget.AppThemeModePreferenceWidget
import ephyra.feature.settings.widget.AppThemePreferenceWidget
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.LocalUiPreferences

internal class ThemeStep : OnboardingStep {

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val uiPreferences = LocalUiPreferences.current
        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsState()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        Column {
            AppThemeModePreferenceWidget(
                value = themeMode,
                onItemClick = {
                    themeModePref.set(it)
                    setAppCompatDelegateThemeMode(it)
                },
            )

            AppThemePreferenceWidget(
                value = appTheme,
                amoled = amoled,
                onItemClick = { appThemePref.set(it) },
            )
        }
    }
}
