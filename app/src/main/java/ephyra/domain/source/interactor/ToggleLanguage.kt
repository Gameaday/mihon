package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourcePreferences
import ephyra.core.common.preference.getAndSet

class ToggleLanguage(
    val preferences: SourcePreferences,
) {

    fun await(language: String) {
        val isEnabled = language in preferences.enabledLanguages().get()
        preferences.enabledLanguages().getAndSet { enabled ->
            if (isEnabled) enabled.minus(language) else enabled.plus(language)
        }
    }
}
