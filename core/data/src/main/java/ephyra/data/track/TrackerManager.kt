@file:Suppress("ktlint:standard:filename")

package ephyra.data.track

import android.app.Application
import ephyra.data.track.anilist.Anilist
import ephyra.data.track.bangumi.Bangumi
import ephyra.data.track.jellyfin.Jellyfin
import ephyra.data.track.kavita.Kavita
import ephyra.data.track.kitsu.Kitsu
import ephyra.data.track.komga.Komga
import ephyra.data.track.mangaupdates.MangaUpdates
import ephyra.data.track.myanimelist.MyAnimeList
import ephyra.data.track.shikimori.Shikimori
import ephyra.data.track.suwayomi.Suwayomi
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

class TrackerManagerImpl(
    context: Application,
    trackPreferences: TrackPreferences,
    libraryPreferences: LibraryPreferences,
    sourceManager: SourceManager,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    json: Json,
) : TrackerManager {

    companion object {
        const val ANILIST = TrackerManager.ANILIST
        const val KITSU = TrackerManager.KITSU
        const val KAVITA = TrackerManager.KAVITA
        const val JELLYFIN = TrackerManager.JELLYFIN
    }

    val myAnimeList = MyAnimeList(1L, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val aniList = Anilist(ANILIST, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val kitsu = Kitsu(KITSU, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val shikimori = Shikimori(4L, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val bangumi = Bangumi(5L, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val komga = Komga(6L, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val mangaUpdates = MangaUpdates(7L, context, trackPreferences, networkService, addTracks, insertTrack, json)
    val kavita = Kavita(KAVITA, context, trackPreferences, networkService, addTracks, insertTrack, sourceManager, json)
    val suwayomi = Suwayomi(9L, context, trackPreferences, networkService, addTracks, insertTrack, sourceManager, json)
    val jellyfin =
        Jellyfin(JELLYFIN, context, trackPreferences, networkService, addTracks, insertTrack, libraryPreferences, json)

    val trackers =
        listOf(myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi, jellyfin)

    private val trackerById: Map<Long, Tracker> = trackers.associateBy { it.id }

    override suspend fun loggedInTrackers(): List<Tracker> {
        return trackers.filter { it.isLoggedIn() }
    }

    override fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    override fun get(id: Long) = trackerById[id]

    override fun getAll(ids: Set<Long>) = ids.mapNotNull { trackerById[it] }
}
