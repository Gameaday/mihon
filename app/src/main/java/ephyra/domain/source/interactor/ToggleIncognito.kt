package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourcePreferences
import ephyra.core.common.preference.getAndSet

class ToggleIncognito(
    private val preferences: SourcePreferences,
) {
    fun await(extensions: String, enable: Boolean) {
        preferences.incognitoExtensions().getAndSet {
            if (enable) it.plus(extensions) else it.minus(extensions)
        }
    }
}
