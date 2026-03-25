package ephyra.app.data.track

import ephyra.app.data.track.anilist.Anilist
import ephyra.app.data.track.bangumi.Bangumi
import ephyra.app.data.track.jellyfin.Jellyfin
import ephyra.app.data.track.kavita.Kavita
import ephyra.app.data.track.kitsu.Kitsu
import ephyra.app.data.track.komga.Komga
import ephyra.app.data.track.mangaupdates.MangaUpdates
import ephyra.app.data.track.myanimelist.MyAnimeList
import ephyra.app.data.track.shikimori.Shikimori
import ephyra.app.data.track.suwayomi.Suwayomi
import ephyra.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import kotlinx.serialization.json.Json
import android.app.Application
import kotlinx.coroutines.flow.combine

class TrackerManager(
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    json: Json,
) {
 
     companion object {
         const val ANILIST = 2L
         const val KITSU = 3L
         const val KAVITA = 8L
         const val JELLYFIN = 10L
     }
 
     val myAnimeList = MyAnimeList(1L, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val aniList = Anilist(ANILIST, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val kitsu = Kitsu(KITSU, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val shikimori = Shikimori(4L, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val bangumi = Bangumi(5L, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val komga = Komga(6L, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val mangaUpdates = MangaUpdates(7L, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val kavita = Kavita(KAVITA, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val suwayomi = Suwayomi(9L, context, trackPreferences, networkService, addTracks, insertTrack, json)
     val jellyfin = Jellyfin(JELLYFIN, context, trackPreferences, networkService, addTracks, insertTrack, json)

    val trackers =
        listOf(myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi, jellyfin)

    private val trackerById: Map<Long, Tracker> = trackers.associateBy { it.id }

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackerById[id]

    fun getAll(ids: Set<Long>) = ids.mapNotNull { trackerById[it] }
}
