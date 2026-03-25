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
import kotlinx.coroutines.flow.combine

class TrackerManager {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L
        const val JELLYFIN = 10L
    }

    val myAnimeList = MyAnimeList(1L)
    val aniList = Anilist(ANILIST)
    val kitsu = Kitsu(KITSU)
    val shikimori = Shikimori(4L)
    val bangumi = Bangumi(5L)
    val komga = Komga(6L)
    val mangaUpdates = MangaUpdates(7L)
    val kavita = Kavita(KAVITA)
    val suwayomi = Suwayomi(9L)
    val jellyfin = Jellyfin(JELLYFIN)

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
