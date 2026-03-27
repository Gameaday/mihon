package ephyra.feature.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.presentation.ExtensionFilterScreen
import ephyra.presentation.core.util.Screen
import kotlinx.coroutines.flow.collectLatest
import ephyra.core.common.i18n.stringResource
import ephyra.i18n.MR
import ephyra.presentation.core.screens.LoadingScreen

class ExtensionFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ExtensionFilterScreenModel>()
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is ExtensionFilterState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as ExtensionFilterState.Success

        ExtensionFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickToggle = screenModel::toggle,
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest {
                when (it) {
                    ExtensionFilterEvent.FailedFetchingLanguages -> {
                        context.stringResource(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
