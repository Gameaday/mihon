package ephyra.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.compose.koinInject
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.presentation.util.Screen
import ephyra.app.ui.manga.MangaScreen

class UpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<UpcomingScreenModel>()
        val state by screenModel.state.collectAsStateWithLifecycle()

        UpcomingScreenContent(
            state = state,
            setSelectedYearMonth = screenModel::setSelectedYearMonth,
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
        )
    }
}
