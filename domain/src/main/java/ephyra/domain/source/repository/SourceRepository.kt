package ephyra.domain.source.repository

import androidx.paging.PagingSource
import eu.kanade.ephyra.source.model.FilterList
import kotlinx.coroutines.flow.Flow
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.Source
import ephyra.domain.source.model.SourceWithCount

typealias SourcePagingSource = PagingSource<Long, Manga>

interface SourceRepository {

    fun getSources(): Flow<List<Source>>

    fun getOnlineSources(): Flow<List<Source>>

    fun getSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>>

    fun getSourcesWithNonLibraryManga(): Flow<List<SourceWithCount>>

    fun search(sourceId: Long, query: String, filterList: FilterList): SourcePagingSource

    fun getPopular(sourceId: Long): SourcePagingSource

    fun getLatest(sourceId: Long): SourcePagingSource
}
