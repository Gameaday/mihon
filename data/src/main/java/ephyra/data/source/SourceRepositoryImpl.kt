package ephyra.data.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ephyra.data.room.daos.MangaDao
import ephyra.domain.source.model.SourceWithCount
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.SourcePagingSource
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.source.model.Source as DomainSource

class SourceRepositoryImpl(
    private val sourceManager: SourceManager,
    private val mangaDao: MangaDao,
    private val networkToLocalManga: NetworkToLocalManga,
) : SourceRepository {

    override fun getSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<HttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            mangaDao.getSourceIdWithFavoriteCount(),
            sourceManager.catalogueSources,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map { list ->
                list.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun getSourcesWithNonLibraryManga(): Flow<List<SourceWithCount>> {
        return mangaDao.getSourceIdsWithNonLibraryManga().map { list ->
            list.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                val domainSource = mapSourceToDomainSource(source).copy(
                    isStub = source is StubSource,
                )
                SourceWithCount(domainSource, count)
            }
        }
    }

    override fun search(
        sourceId: Long,
        query: String,
        filterList: FilterList,
    ): SourcePagingSource {
        val source = sourceManager.get(sourceId) as CatalogueSource
        return SourceSearchPagingSource(source, query, filterList, networkToLocalManga)
    }

    override fun getPopular(sourceId: Long): SourcePagingSource {
        val source = sourceManager.get(sourceId) as CatalogueSource
        return SourcePopularPagingSource(source, networkToLocalManga)
    }

    override fun getLatest(sourceId: Long): SourcePagingSource {
        val source = sourceManager.get(sourceId) as CatalogueSource
        return SourceLatestPagingSource(source, networkToLocalManga)
    }

    private fun mapSourceToDomainSource(source: Source): DomainSource = DomainSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}
