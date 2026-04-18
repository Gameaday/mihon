package ephyra.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.manga.MangaScreen
import ephyra.presentation.core.util.Screen

class UpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<UpcomingScreenModel>()
        val state by screenModel.state.collectAsStateWithLifecycle()

        UpcomingScreenContent(
            state = state,
            setSelectedYearMonth = { screenModel.onEvent(UpcomingScreenEvent.SetSelectedYearMonth(it)) },
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
        )
    }
}
