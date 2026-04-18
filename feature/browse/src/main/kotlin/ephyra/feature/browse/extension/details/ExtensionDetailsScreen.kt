package ephyra.feature.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.presentation.ExtensionDetailsScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.Screen
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.parameter.parametersOf

data class ExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = koinScreenModel<ExtensionDetailsScreenModel> { parametersOf(pkgName) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        ExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
            onClickEnableAll = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleSources(true)) },
            onClickDisableAll = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleSources(false)) },
            onClickClearCookies = { screenModel.onEvent(ExtensionDetailsScreenEvent.ClearCookies) },
            onClickUninstall = { screenModel.onEvent(ExtensionDetailsScreenEvent.UninstallExtension) },
            onClickSource = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleSource(it)) },
            onClickIncognito = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleIncognito(it)) },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
