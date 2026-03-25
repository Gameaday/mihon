package ephyra.feature.browse.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import org.koin.core.annotation.Factory
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.service.SourcePreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.logcat

@Factory
class ExtensionFilterScreenModel(
    private val preferences: SourcePreferences,
    private val getExtensionLanguages: GetExtensionLanguages,
    private val toggleLanguage: ToggleLanguage,
) : StateScreenModel<ExtensionFilterState>(ExtensionFilterState.Loading) {

    private val _events: Channel<ExtensionFilterEvent> = Channel()
    val events: Flow<ExtensionFilterEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            combine(
                getExtensionLanguages.subscribe(),
                preferences.enabledLanguages().changes(),
            ) { a, b -> a to b }
                .catch { throwable ->
                    logcat(LogPriority.ERROR, throwable)
                    _events.send(ExtensionFilterEvent.FailedFetchingLanguages)
                }
                .collectLatest { (extensionLanguages, enabledLanguages) ->
                    mutableState.update {
                        ExtensionFilterState.Success(
                            languages = extensionLanguages.toImmutableList(),
                            enabledLanguages = enabledLanguages.toImmutableSet(),
                        )
                    }
                }
        }
    }

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface ExtensionFilterEvent {
    data object FailedFetchingLanguages : ExtensionFilterEvent
}

sealed interface ExtensionFilterState {

    @Immutable
    data object Loading : ExtensionFilterState

    @Immutable
    data class Success(
        val languages: ImmutableList<String>,
        val enabledLanguages: ImmutableSet<String> = persistentSetOf(),
    ) : ExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
