package eu.kanade.tachiyomi.source

import android.content.Context
import ephyra.app.extension.ExtensionManager
import ephyra.core.common.i18n.stringResource
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class AndroidSourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val sourceRepository: StubSourceRepository,
    private val fileSystem: ephyra.source.local.io.LocalSourceFileSystem,
    private val coverManager: ephyra.source.local.image.LocalCoverManager,
    private val downloadManager: DownloadManager,
) : SourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    override val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<CatalogueSource>()
    }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, Source>(
                        mapOf(
                            LocalSource.ID to LocalSource(
                                context,
                                fileSystem,
                                coverManager,
                            ),
                        ),
                    )
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): Source? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): Source {
        sourcesMapFlow.value[sourceKey]?.let { return it }
        stubSourcesMap[sourceKey]?.let { return it }

        if (sourceKey == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID) {
            val authorityStub = StubSource(
                id = sourceKey,
                lang = "all",
                name = context.stringResource(ephyra.i18n.MR.strings.authority_source_name),
            )
            stubSourcesMap[sourceKey] = authorityStub
            return authorityStub
        }

        val fallback = StubSource(id = sourceKey, lang = "", name = "")
        stubSourcesMap[sourceKey] = fallback
        scope.launch {
            val actualStub = createStubSource(sourceKey)
            if (actualStub != fallback) {
                stubSourcesMap[sourceKey] = actualStub
            }
        }
        return fallback
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    override fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        sourceRepository.getStubSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubSource(id = id, lang = "", name = "")
    }
}
