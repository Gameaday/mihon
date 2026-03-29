package ephyra.feature.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.presentation.SourcesFilterScreen
import ephyra.presentation.util.Screen
import ephyra.presentation.core.util.system.toast
import ephyra.i18n.MR
import ephyra.presentation.core.screens.LoadingScreen

class SourcesFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SourcesFilterScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is SourcesFilterScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        if (state is SourcesFilterScreenModel.State.Error) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.toast(MR.strings.internal_error)
                navigator.pop()
            }
            return
        }

        val successState = state as SourcesFilterScreenModel.State.Success

        SourcesFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickLanguage = screenModel::toggleLanguage,
            onClickSource = screenModel::toggleSource,
        )
    }
}
