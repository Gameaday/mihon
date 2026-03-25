package ephyra.domain.source.interactor

import kotlinx.coroutines.flow.Flow
import ephyra.domain.source.model.SourceWithCount
import ephyra.domain.source.repository.SourceRepository

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
