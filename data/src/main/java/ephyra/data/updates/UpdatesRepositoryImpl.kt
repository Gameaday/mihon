package ephyra.data.updates

import kotlinx.coroutines.flow.Flow
import ephyra.core.common.util.lang.toLong
import ephyra.data.DatabaseHandler
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.domain.updates.repository.UpdatesRepository

class UpdatesRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
) : UpdatesRepository {

    override suspend fun awaitWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> {
        return databaseHandler.awaitList {
            updatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
    ): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.getRecentUpdatesWithFilters(
                after = after,
                limit = limit,
                // invert because unread in Kotlin -> read column in SQL
                read = unread?.let { !it },
                started = started?.toLong(),
                bookmarked = bookmarked,
                hideExcludedScanlators = hideExcludedScanlators.toLong(),
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    override fun subscribeWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    private fun mapUpdatesWithRelations(
        mangaId: Long,
        mangaTitle: String,
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        chapterUrl: String,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateUpload: Long,
        dateFetch: Long,
        excludedScanlator: String?,
    ): UpdatesWithRelations = UpdatesWithRelations(
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        chapterUrl = chapterUrl,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
