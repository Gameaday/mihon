package ephyra.feature.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.domain.manga.model.Manga
import ephyra.presentation.core.util.Screen
import org.koin.core.parameter.parametersOf

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<MangaNotesScreenModel> { parametersOf(manga) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }
}
