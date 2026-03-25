package ephyra.domain.source.service

import eu.kanade.ephyra.source.CatalogueSource
import eu.kanade.ephyra.source.Source
import eu.kanade.ephyra.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ephyra.domain.source.model.StubSource

interface SourceManager {

    val isInitialized: StateFlow<Boolean>

    val catalogueSources: Flow<List<CatalogueSource>>

    fun get(sourceKey: Long): Source?

    fun getOrStub(sourceKey: Long): Source

    fun getOnlineSources(): List<HttpSource>

    fun getCatalogueSources(): List<CatalogueSource>

    fun getStubSources(): List<StubSource>
}
