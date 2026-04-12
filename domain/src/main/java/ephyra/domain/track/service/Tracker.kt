package ephyra.domain.track.service

import dev.icerock.moko.resources.StringResource
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import kotlinx.coroutines.flow.Flow

interface Tracker {

    val id: Long

    val name: String

    val supportsReadingDates: Boolean

    val supportsPrivateTracking: Boolean

    fun getLogo(): Int

    fun getStatusList(): List<Long>

    fun getStatus(status: Long): StringResource?

    fun getReadingStatus(): Long

    fun getRereadingStatus(): Long

    fun getCompletionStatus(): Long

    fun getScoreList(): List<String>

    fun get10PointScore(track: Track): Double

    fun indexToScore(index: Int): Double

    fun displayScore(track: Track): String

    suspend fun update(track: Track, didReadChapter: Boolean = false): Track

    suspend fun bind(track: Track, hasReadChapters: Boolean = false): Track

    suspend fun search(query: String): List<TrackSearch>

    suspend fun refresh(track: Track): Track

    suspend fun login(username: String, password: String)

    fun logout()

    suspend fun isLoggedIn(): Boolean

    val isLoggedInFlow: Flow<Boolean>

    suspend fun getUsername(): String

    suspend fun getPassword(): String

    fun saveCredentials(username: String, password: String)

    suspend fun register(item: Track, mangaId: Long)

    suspend fun setRemoteStatus(track: Track, status: Long)

    suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int)

    suspend fun setRemoteScore(track: Track, scoreString: String)

    suspend fun setRemoteStartDate(track: Track, epochMillis: Long)

    suspend fun setRemoteFinishDate(track: Track, epochMillis: Long)

    suspend fun setRemotePrivate(track: Track, isPrivate: Boolean)
}
