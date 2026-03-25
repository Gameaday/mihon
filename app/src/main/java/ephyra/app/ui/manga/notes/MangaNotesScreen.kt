package ephyra.app.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.presentation.manga.MangaNotesScreen
import ephyra.presentation.util.Screen
import kotlinx.coroutines.flow.update
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { Model(manga) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }

    private class Model(
        private val manga: Manga,
        private val updateMangaNotes: UpdateMangaNotes = Injekt.get(),
    ) : StateScreenModel<State>(State(manga, manga.notes)) {

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            mutableState.update {
                it.copy(notes = content)
            }

            screenModelScope.launchNonCancellable {
                updateMangaNotes(manga.id, content)
            }
        }
    }

    @Immutable
    data class State(
        val manga: Manga,
        val notes: String,
    )
}
