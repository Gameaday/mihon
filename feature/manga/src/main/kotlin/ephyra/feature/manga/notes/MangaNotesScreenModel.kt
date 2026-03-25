package ephyra.feature.manga.notes

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam

@Factory
class MangaNotesScreenModel(
    @InjectedParam private val manga: Manga,
    private val updateMangaNotes: UpdateMangaNotes,
) : StateScreenModel<MangaNotesState>(MangaNotesState(manga, manga.notes)) {

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
data class MangaNotesState(
    val manga: Manga,
    val notes: String,
)
