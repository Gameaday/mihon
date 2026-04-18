package ephyra.feature.browse.source

import ephyra.domain.source.model.Source

sealed interface SourcesFilterScreenEvent {
    data class ToggleSource(val source: Source) : SourcesFilterScreenEvent
    data class ToggleLanguage(val language: String) : SourcesFilterScreenEvent
}
