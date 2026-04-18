package ephyra.feature.browse.source

import ephyra.domain.source.model.Source

sealed interface SourcesScreenEvent {
    data class ToggleSource(val source: Source) : SourcesScreenEvent
    data class TogglePin(val source: Source) : SourcesScreenEvent
    data class ShowSourceDialog(val source: Source) : SourcesScreenEvent
    data object CloseDialog : SourcesScreenEvent
}
