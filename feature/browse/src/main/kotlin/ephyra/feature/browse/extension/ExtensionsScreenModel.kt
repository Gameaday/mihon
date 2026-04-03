package ephyra.feature.browse.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import org.koin.core.annotation.Factory
import dev.icerock.moko.resources.StringResource
import ephyra.domain.base.BasePreferences
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.components.SEARCH_DEBOUNCE_MILLIS
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.InstallStep
import eu.kanade.tachiyomi.source.online.HttpSource
import ephyra.core.common.util.system.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ephyra.core.common.util.lang.launchIO
import ephyra.i18n.MR
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

@Factory
class ExtensionsScreenModel(
    private val context: Application,
    private val preferences: SourcePreferences,
    private val basePreferences: BasePreferences,
    private val extensionManager: ExtensionManager,
    private val getExtensions: GetExtensionsByType,
) : StateScreenModel<ExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    init {
        val extensionMapper: (Map<String, InstallStep>) -> ((Extension) -> ExtensionUiModel.Item) = { map ->
            {
                ExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
            }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
                    .map { searchQueryPredicate(it ?: "") },
                currentDownloads,
                getExtensions.subscribe(),
            ) { predicate, downloads, (_updates, _installed, _available, _untrusted) ->
                val mapper = extensionMapper(downloads)
                buildMap {
                    val updates = _updates.mapNotNull { if (predicate(it)) mapper(it) else null }
                    if (updates.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
                    }

                    val installed = _installed.mapNotNull { if (predicate(it)) mapper(it) else null }
                    val untrusted = _untrusted.mapNotNull { if (predicate(it)) mapper(it) else null }
                    if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_installed), installed + untrusted)
                    }

                    val langGroups = TreeMap<String, MutableList<Extension.Available>>(LocaleHelper.comparator)
                    _available.forEach { if (predicate(it)) langGroups.getOrPut(it.lang) { mutableListOf() }.add(it) }
                    langGroups.forEach { (lang, exts) ->
                        put(
                            ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)),
                            exts.map(mapper),
                        )
                    }
                }
            }
                .collectLatest { items ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                        )
                    }
                }
        }

        screenModelScope.launchIO { findAvailableExtensions() }

        preferences.extensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
    }

    fun searchQueryPredicate(query: String): (Extension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        // Pre-compute Long ID parsing once per subquery, instead of once per extension
        val parsedSubqueries = subqueries.map { it to it.toLongOrNull() }

        return { extension ->
            parsedSubqueries.any { (subquery, subqueryAsId) ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true
                when (extension) {
                    is Extension.Installed -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.baseUrl?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subqueryAsId
                    }

                    is Extension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subqueryAsId
                    }

                    else -> false
                }
            }
        }
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.forEach { items ->
                items.forEach { item ->
                    val ext = item.extension
                    if (ext is Extension.Installed && ext.hasUpdate) {
                        updateExtension(ext)
                    }
                }
            }
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
        removeDownloadState(extension)
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: Extension) {
        currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: Extension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
    ) {
        val isEmpty = items.isEmpty()
    }
}

typealias ItemGroups = Map<ExtensionUiModel.Header, List<ExtensionUiModel.Item>>

object ExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: Extension,
        val installStep: InstallStep,
    )
}
