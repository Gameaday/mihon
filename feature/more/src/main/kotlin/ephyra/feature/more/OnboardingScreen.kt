package ephyra.app.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.domain.base.BasePreferences
import ephyra.presentation.more.onboarding.OnboardingScreen
import ephyra.presentation.more.settings.screen.SearchableSettings
import ephyra.presentation.more.settings.screen.SettingsDataScreen
import ephyra.presentation.util.Screen
import ephyra.app.ui.setting.SettingsScreen
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import org.koin.compose.koinInject

class OnboardingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = koinInject<BasePreferences>()
        val shownOnboardingFlow by basePreferences.shownOnboardingFlow().collectAsState()

        val finishOnboarding: () -> Unit = {
            basePreferences.shownOnboardingFlow().set(true)
            navigator.pop()
        }

        val restoreSettingKey = stringResource(SettingsDataScreen.restorePreferenceKeyString)

        BackHandler(enabled = !shownOnboardingFlow) {
            // Prevent exiting if onboarding hasn't been completed
        }

        OnboardingScreen(
            onComplete = finishOnboarding,
            onRestoreBackup = {
                finishOnboarding()
                SearchableSettings.highlightKey = restoreSettingKey
                navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
            },
        )
    }
}
