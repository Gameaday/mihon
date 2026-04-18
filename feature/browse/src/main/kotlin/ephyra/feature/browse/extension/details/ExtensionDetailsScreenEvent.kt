package ephyra.feature.browse.extension.details

sealed interface ExtensionDetailsScreenEvent {
    data object ClearCookies : ExtensionDetailsScreenEvent
    data object UninstallExtension : ExtensionDetailsScreenEvent
    data class ToggleSource(val sourceId: Long) : ExtensionDetailsScreenEvent
    data class ToggleSources(val enable: Boolean) : ExtensionDetailsScreenEvent
    data class ToggleIncognito(val enable: Boolean) : ExtensionDetailsScreenEvent
}
