package ephyra.feature.manga

sealed interface CoverSearchScreenEvent {
    data object Search : CoverSearchScreenEvent
    data object Refresh : CoverSearchScreenEvent
}
