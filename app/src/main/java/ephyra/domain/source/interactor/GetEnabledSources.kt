package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import ephyra.domain.source.model.Pin
import ephyra.domain.source.model.Pins
import ephyra.domain.source.model.Source
import ephyra.domain.source.repository.SourceRepository
import ephyra.source.local.isLocal

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<Source>> {
        return combine(
            preferences.pinnedSources().changes(),
            preferences.enabledLanguages().changes(),
            preferences.disabledSources().changes(),
            preferences.lastUsedSource().changes(),
            repository.getSources(),
        ) { pinnedSourceIds, enabledLanguages, disabledSources, lastUsedSource, sources ->
            // Parse Set<String> IDs to Set<Long> once per emission to avoid creating a String
            // for every source on every filter/flatMap call.
            val disabledIds = disabledSources.mapTo(HashSet()) { it.toLong() }
            val pinnedIds = pinnedSourceIds.mapTo(HashSet()) { it.toLong() }
            val sortedSources = sources
                .filter { (it.lang in enabledLanguages || it.isLocal()) && it.id !in disabledIds }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            buildList(sortedSources.size + 1) {
                for (it in sortedSources) {
                    val flag = if (it.id in pinnedIds) Pins.pinned else Pins.unpinned
                    val source = it.copy(pin = flag)
                    add(source)
                    if (source.id == lastUsedSource) {
                        add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                }
            }
        }
            .distinctUntilChanged()
    }
}
