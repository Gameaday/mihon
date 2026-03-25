package ephyra.domain.extension.interactor

import ephyra.domain.source.service.SourcePreferences
import ephyra.app.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetExtensionSources(
    private val preferences: SourcePreferences,
) {

    fun subscribe(extension: Extension.Installed): Flow<List<ExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledSources().changes().map { disabledSources ->
            val disabledIds = disabledSources.mapTo(HashSet()) { it.toLong() }
            fun Source.isEnabled() = id !in disabledIds

            extension.sources
                .map { source ->
                    ExtensionSourceItem(
                        source = source,
                        enabled = source.isEnabled(),
                        labelAsName = isMultiSource && !isMultiLangSingleSource,
                    )
                }
        }
    }
}

data class ExtensionSourceItem(
    val source: Source,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
