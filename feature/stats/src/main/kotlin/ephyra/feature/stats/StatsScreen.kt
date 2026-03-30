package ephyra.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.more.stats.StatsScreenContent
import ephyra.presentation.more.stats.StatsScreenState
import ephyra.presentation.core.util.Screen

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { StatsScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state is StatsScreenState.Loading) {
                LoadingScreen()
                return@Scaffold
            }

            StatsScreenContent(
                state = state as StatsScreenState.Success,
                paddingValues = paddingValues,
            )
        }
    }
}
