package ephyra.presentation.manga.preview

import dev.icerock.moko.resources.StringResource
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.i18n.MR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A minimal [Tracker] implementation used exclusively for Compose UI previews.
 * It contains no runtime logic and must not be used in production code.
 */
internal data class DummyTracker(
    override val id: Long,
    override val name: String,
    override val supportsReadingDates: Boolean = false,
    override val supportsPrivateTracking: Boolean = false,
    override val isLoggedInFlow: Flow<Boolean> = flowOf(false),
) : Tracker {

    override fun getLogo(): Int = 0

    override fun getStatusList(): List<Long> = (1L..6L).toList()

    override fun getStatus(status: Long): StringResource? = when (status) {
        1L -> MR.strings.reading
        2L -> MR.strings.plan_to_read
        3L -> MR.strings.completed
        4L -> MR.strings.on_hold
        5L -> MR.strings.dropped
        6L -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = 1L

    override fun getRereadingStatus(): Long = 6L

    override fun getCompletionStatus(): Long = 2L

    override fun getScoreList(): List<String> = (0..10).map(Int::toString)

    override fun get10PointScore(track: Track): Double = 5.0

    override fun indexToScore(index: Int): Double = index.toDouble()

    override fun displayScore(track: Track): String = track.score.toString()

    override suspend fun update(track: Track, didReadChapter: Boolean): Track = track

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()

    override suspend fun refresh(track: Track): Track = track

    override suspend fun login(username: String, password: String) = Unit

    override fun logout() = Unit

    override suspend fun isLoggedIn(): Boolean = false

    override suspend fun getUsername(): String = "preview-user"

    override suspend fun getPassword(): String = "preview-pass"

    override fun saveCredentials(username: String, password: String) = Unit

    override suspend fun register(item: Track, mangaId: Long) = Unit

    override suspend fun setRemoteStatus(track: Track, status: Long) = Unit

    override suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int) = Unit

    override suspend fun setRemoteScore(track: Track, scoreString: String) = Unit

    override suspend fun setRemoteStartDate(track: Track, epochMillis: Long) = Unit

    override suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) = Unit

    override suspend fun setRemotePrivate(track: Track, private: Boolean) = Unit
}
