package ephyra.data.track

import android.app.Application
import androidx.annotation.CallSuper
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.lang.withUIContext
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.toast
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.model.toDbTrack
import ephyra.domain.track.model.toDomainTrack
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.Tracker
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import okhttp3.OkHttpClient
import ephyra.data.database.models.Track as DbTrack

abstract class BaseTracker(
    override val id: Long,
    override val name: String,
    private val context: Application,
    val trackPreferences: TrackPreferences,
    val networkService: NetworkHelper,
    private val addTracks: AddTracks,
    private val insertTrack: InsertTrack,
) : Tracker {

    open val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for reading dates
    override val supportsReadingDates: Boolean = false

    override val supportsPrivateTracking: Boolean = false

    override fun get10PointScore(track: Track): Double {
        return track.score
    }

    override fun indexToScore(index: Int): Double {
        return index.toDouble()
    }

    @CallSuper
    override fun logout() {
        trackPreferences.setCredentials(this, "", "")
    }

    override suspend fun isLoggedIn(): Boolean {
        return getUsername().isNotEmpty() && getPassword().isNotEmpty()
    }

    override val isLoggedInFlow: Flow<Boolean> by lazy {
        combine(
            trackPreferences.trackUsername(this).changes(),
            trackPreferences.trackPassword(this).changes(),
        ) { username, password ->
            username.isNotEmpty() && password.isNotEmpty()
        }
    }

    override suspend fun getUsername() = trackPreferences.trackUsername(this).get()

    override suspend fun getPassword() = trackPreferences.trackPassword(this).get()

    fun getUsernameSync() = kotlinx.coroutines.runBlocking { trackPreferences.trackUsername(this@BaseTracker).get() }

    fun getPasswordSync() = kotlinx.coroutines.runBlocking { trackPreferences.trackPassword(this@BaseTracker).get() }

    override fun saveCredentials(username: String, password: String) {
        trackPreferences.setCredentials(this, username, password)
    }

    override suspend fun register(item: Track, mangaId: Long) {
        try {
            addTracks.bind(this, item, mangaId)
        } catch (e: Throwable) {
            val errorDetail = when (e) {
                is HttpException -> "$name: HTTP ${e.code}"
                else -> "$name: ${e.message}"
            }
            withUIContext { context.toast(errorDetail) }
        }
    }

    override suspend fun setRemoteStatus(track: Track, status: Long) {
        val updatedTrack = track.copy(status = status).let {
            if (it.status == getCompletionStatus() && it.totalChapters != 0L) {
                it.copy(lastChapterRead = it.totalChapters.toDouble())
            } else {
                it
            }
        }
        updateRemote(updatedTrack)
    }

    override suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int) {
        var updatedTrack = track.copy(lastChapterRead = chapterNumber.toDouble())
        if (
            track.lastChapterRead == 0.0 &&
            track.lastChapterRead < chapterNumber &&
            track.status != getRereadingStatus()
        ) {
            updatedTrack = updatedTrack.copy(status = getReadingStatus())
        }
        if (updatedTrack.totalChapters != 0L && updatedTrack.lastChapterRead.toLong() == updatedTrack.totalChapters) {
            updatedTrack = updatedTrack.copy(
                status = getCompletionStatus(),
                finishDate = System.currentTimeMillis(),
            )
        }
        updateRemote(updatedTrack)
    }

    override suspend fun setRemoteScore(track: Track, scoreString: String) {
        val updatedTrack = track.copy(score = indexToScore(getScoreList().indexOf(scoreString)))
        updateRemote(updatedTrack)
    }

    override suspend fun setRemoteStartDate(track: Track, epochMillis: Long) {
        updateRemote(track.copy(startDate = epochMillis))
    }

    override suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) {
        updateRemote(track.copy(finishDate = epochMillis))
    }

    override suspend fun setRemotePrivate(track: Track, private: Boolean) {
        updateRemote(track.copy(private = private))
    }

    private suspend fun updateRemote(track: Track): Unit = withIOContext {
        try {
            val updated = update(track)
            insertTrack.await(updated)
        } catch (e: Exception) {
            val errorDetail = when (e) {
                is HttpException -> "$name: HTTP ${e.code}"
                else -> "$name: ${e.message}"
            }
            logcat(LogPriority.ERROR, e) { "Failed to update remote track data id=$id ($errorDetail)" }
            withUIContext { context.toast(errorDetail) }
        }
    }

    // Default implementations for domain Tracker that call internal DbTrack versions if needed
    // or are overridden by subclasses.

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        return updateInternal(track.toDbTrack(), didReadChapter).toDomainTrack(idRequired = false)!!
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return bindInternal(track.toDbTrack(), hasReadChapters).toDomainTrack(idRequired = false)!!
    }

    override suspend fun refresh(track: Track): Track {
        return refreshInternal(track.toDbTrack()).toDomainTrack(idRequired = false)!!
    }

    // Internal versions that subclasses should implement using DbTrack
    abstract suspend fun updateInternal(track: DbTrack, didReadChapter: Boolean = false): DbTrack
    abstract suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean = false): DbTrack
    abstract suspend fun refreshInternal(track: DbTrack): DbTrack
}
