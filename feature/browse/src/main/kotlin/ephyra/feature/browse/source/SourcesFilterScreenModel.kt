package ephyra.feature.browse.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory
import java.util.SortedMap

@Factory
class SourcesFilterScreenModel(
    private val preferences: SourcePreferences,
    private val getLanguagesWithSources: GetLanguagesWithSources,
    private val toggleSource: ToggleSource,
    private val toggleLanguage: ToggleLanguage,
) : StateScreenModel<SourcesFilterScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launch {
            combine(
                getLanguagesWithSources.subscribe(),
                preferences.enabledLanguages().changes(),
                preferences.disabledSources().changes(),
            ) { a, b, c -> Triple(a, b, c) }
                .catch { throwable ->
                    mutableState.update {
                        State.Error(
                            throwable = throwable,
                        )
                    }
                }
                .collectLatest { (languagesWithSources, enabledLanguages, disabledSources) ->
                    mutableState.update {
                        State.Success(
                            items = languagesWithSources,
                            enabledLanguages = enabledLanguages,
                            disabledSources = disabledSources,
                        )
                    }
                }
        }
    }

    fun onEvent(event: SourcesFilterScreenEvent) {
        when (event) {
            is SourcesFilterScreenEvent.ToggleSource -> toggleSource(event.source)
            is SourcesFilterScreenEvent.ToggleLanguage -> toggleLanguage(event.language)
        }
    }

    private fun toggleSource(source: Source) {
        screenModelScope.launch { toggleSource.await(source) }
    }

    private fun toggleLanguage(language: String) {
        screenModelScope.launch { toggleLanguage.await(language) }
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<Source>>,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()

            /** Pre-parsed as `Long` IDs for O(1) membership checks without String allocation. */
            val disabledSourceIds: Set<Long> by lazy { disabledSources.mapTo(HashSet()) { it.toLong() } }
        }
    }
}
