package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourcePreferences
import ephyra.app.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ephyra.domain.source.model.Source
import ephyra.domain.source.repository.SourceRepository
import java.util.SortedMap

class GetLanguagesWithSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<SortedMap<String, List<Source>>> {
        return combine(
            preferences.enabledLanguages().changes(),
            preferences.disabledSources().changes(),
            repository.getOnlineSources(),
        ) { enabledLanguage, disabledSource, onlineSources ->
            val disabledIds = disabledSource.mapTo(HashSet()) { it.toLong() }
            val sortedSources = onlineSources.sortedWith(
                compareBy<Source> { it.id in disabledIds }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

            sortedSources
                .groupBy { it.lang }
                .toSortedMap(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
