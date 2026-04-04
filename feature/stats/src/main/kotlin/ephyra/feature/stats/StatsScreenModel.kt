package ephyra.feature.stats

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.fastCountNot
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.history.interactor.GetTotalReadDuration
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.TrackerManager
import ephyra.feature.stats.data.StatsData
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Factory

@Factory
class StatsScreenModel(
    private val downloadManager: DownloadManager,
    private val getLibraryManga: GetLibraryManga,
    private val getTotalReadDuration: GetTotalReadDuration,
    private val getTracks: GetTracks,
    private val preferences: LibraryPreferences,
    private val trackerManager: TrackerManager,
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers() }

    init {
        screenModelScope.launchIO {
            val libraryManga = getLibraryManga.await()

            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val mangaTrackMap = getMangaTrackMap(distinctLibraryManga)
            val scoredMangaTrackerMap = getScoredMangaTrackMap(mangaTrackMap)

            val meanScore = getTrackMeanScore(scoredMangaTrackerMap)

            val overviewStatData = StatsData.Overview(
                libraryMangaCount = distinctLibraryManga.size,
                completedMangaCount = distinctLibraryManga.count {
                    it.manga.status.toInt() == SManga.COMPLETED && it.unreadCount == 0L
                },
                totalReadDuration = getTotalReadDuration.await(),
            )

            val titlesStatData = StatsData.Titles(
                globalUpdateItemCount = getGlobalUpdateItemCount(libraryManga),
                startedMangaCount = distinctLibraryManga.count { it.hasStarted },
                localMangaCount = distinctLibraryManga.count { it.manga.isLocal() },
            )

            val chaptersStatData = StatsData.Chapters(
                totalChapterCount = distinctLibraryManga.sumOf { it.totalChapters }.toInt(),
                readChapterCount = distinctLibraryManga.sumOf { it.readCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = StatsData.Trackers(
                trackedTitleCount = mangaTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
                trackerCount = loggedInTrackers.size,
            )

            mutableState.update {
                StatsScreenState.Success(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    chapters = chaptersStatData,
                    trackers = trackersStatData,
                )
            }
        }
    }

    private fun getGlobalUpdateItemCount(libraryManga: List<LibraryManga>): Int {
        val includedCategories = preferences.updateCategories().get().mapTo(HashSet()) { it.toLong() }
        val excludedCategories = preferences.updateCategoriesExclude().get().mapTo(HashSet()) { it.toLong() }
        val updateRestrictions = preferences.autoUpdateMangaRestrictions().get()

        return libraryManga.filter {
            val included = includedCategories.isEmpty() || it.categories.any { cat -> cat in includedCategories }
            val excluded = it.categories.any { cat -> cat in excludedCategories }
            included && !excluded
        }
            .fastCountNot {
                (MANGA_NON_COMPLETED in updateRestrictions && it.manga.status.toInt() == SManga.COMPLETED) ||
                    (MANGA_HAS_UNREAD in updateRestrictions && it.unreadCount != 0L) ||
                    (MANGA_NON_READ in updateRestrictions && it.totalChapters > 0 && !it.hasStarted)
            }
    }

    private suspend fun getMangaTrackMap(libraryManga: List<LibraryManga>): Map<Long, List<Track>> {
        val loggedInTrackerIds = loggedInTrackers.mapTo(HashSet()) { it.id }
        return libraryManga.associate { manga ->
            val tracks = getTracks.await(manga.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            manga.id to tracks
        }
    }

    private fun getScoredMangaTrackMap(mangaTrackMap: Map<Long, List<Track>>): Map<Long, List<Track>> {
        return buildMap {
            mangaTrackMap.forEach { (mangaId, tracks) ->
                val trackList = tracks.filter { it.score > 0.0 }
                if (trackList.isNotEmpty()) put(mangaId, trackList)
            }
        }
    }

    private fun getTrackMeanScore(scoredMangaTrackMap: Map<Long, List<Track>>): Double {
        return scoredMangaTrackMap.values
            .mapNotNull { tracks -> tracks.map(::get10PointScore).average().takeIf { !it.isNaN() } }
            .average()
    }

    private fun get10PointScore(track: Track): Double {
        val service = trackerManager.get(track.trackerId)!!
        return service.get10PointScore(track)
    }
}
