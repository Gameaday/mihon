package ephyra.feature.browse.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.source.model.Pin
import ephyra.domain.source.model.Source
import ephyra.feature.browse.presentation.SourceUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.annotation.Factory
import java.util.TreeMap

@Factory
class SourcesScreenModel(
    private val getEnabledSources: GetEnabledSources,
    private val toggleSource: ToggleSource,
    private val toggleSourcePin: ToggleSourcePin,
) : StateScreenModel<SourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getEnabledSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingSources)
                }
                .collectLatest(::collectLatestSources)
        }
    }

    private fun collectLatestSources(sources: List<Source>) {
        mutableState.update { state ->
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                // Sources without a lang defined will be placed at the end
                when {
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) {
                when {
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }

            state.copy(
                isLoading = false,
                items = buildList(sources.size + byLang.size) {
                    byLang.forEach { (key, langSources) ->
                        add(SourceUiModel.Header(key))
                        langSources.mapTo(this) { SourceUiModel.Item(it) }
                    }
                }.toImmutableList(),
            )
        }
    }

    fun onEvent(event: SourcesScreenEvent) {
        when (event) {
            is SourcesScreenEvent.ToggleSource -> toggleSource(event.source)
            is SourcesScreenEvent.TogglePin -> togglePin(event.source)
            is SourcesScreenEvent.ShowSourceDialog -> showSourceDialog(event.source)
            SourcesScreenEvent.CloseDialog -> closeDialog()
        }
    }

    private fun toggleSource(source: Source) {
        screenModelScope.launch { toggleSource.await(source) }
    }

    private fun togglePin(source: Source) {
        screenModelScope.launch { toggleSourcePin.await(source) }
    }

    private fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    private fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: Source)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<SourceUiModel> = persistentListOf(),
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}
