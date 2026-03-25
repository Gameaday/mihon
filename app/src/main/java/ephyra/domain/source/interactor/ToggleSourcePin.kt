package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourcePreferences
import ephyra.core.common.preference.getAndSet
import ephyra.domain.source.model.Source

class ToggleSourcePin(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source) {
        val isPinned = source.id.toString() in preferences.pinnedSources().get()
        preferences.pinnedSources().getAndSet { pinned ->
            if (isPinned) pinned.minus("${source.id}") else pinned.plus("${source.id}")
        }
    }
}
