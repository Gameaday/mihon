package ephyra.domain.manga.interactor

import ephyra.data.DatabaseHandler

class SetExcludedScanlators(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long, excludedScanlators: Set<String>) {
        handler.await(inTransaction = true) {
            val currentExcluded = handler.awaitList {
                excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
            }.toSet()
            val toAdd = excludedScanlators.minus(currentExcluded)
            for (scanlator in toAdd) {
                excluded_scanlatorsQueries.insert(mangaId, scanlator)
            }
            val toRemove = currentExcluded.minus(excludedScanlators)
            excluded_scanlatorsQueries.remove(mangaId, toRemove)
        }
    }
}
