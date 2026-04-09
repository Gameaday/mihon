package ephyra.test

import dev.icerock.moko.resources.StringResource
import ephyra.app.R
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.i18n.MR
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class DummyTracker(
    override val id: Long,
    override val name: String,
    override val supportsReadingDates: Boolean = false,
    override val supportsPrivateTracking: Boolean = false,
    override val isLoggedInFlow: Flow<Boolean> = flowOf(false),
    val valLogo: Int = R.drawable.brand_anilist,
    val valStatuses: List<Long> = (1L..6L).toList(),
    val valReadingStatus: Long = 1L,
    val valRereadingStatus: Long = 1L,
    val valCompletionStatus: Long = 2L,
    val valScoreList: ImmutableList<String> = (0..10).map(Int::toString).toImmutableList(),
    val val10PointScore: Double = 5.4,
    val valSearchResults: List<TrackSearch> = listOf(),
) : Tracker {

    override fun getLogo(): Int = valLogo

    override fun getStatusList(): List<Long> = valStatuses

    override fun getStatus(status: Long): StringResource? = when (status) {
        1L -> MR.strings.reading
        2L -> MR.strings.plan_to_read
        3L -> MR.strings.completed
        4L -> MR.strings.on_hold
        5L -> MR.strings.dropped
        6L -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = valReadingStatus

    override fun getRereadingStatus(): Long = valRereadingStatus

    override fun getCompletionStatus(): Long = valCompletionStatus

    override fun getScoreList(): List<String> = valScoreList

    override fun get10PointScore(track: Track): Double = val10PointScore

    override fun indexToScore(index: Int): Double = getScoreList()[index].toDouble()

    override fun displayScore(track: Track): String =
        track.score.toString()

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track = track

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track = track

    override suspend fun search(query: String): List<TrackSearch> = valSearchResults

    override suspend fun refresh(
        track: Track,
    ): Track = track

    override suspend fun login(username: String, password: String) = Unit

    override fun logout() = Unit

    override suspend fun isLoggedIn(): Boolean = isLoggedInFlow.let { false }

    override suspend fun getUsername(): String = "username"

    override suspend fun getPassword(): String = "passw0rd"

    override fun saveCredentials(username: String, password: String) = Unit

    override suspend fun register(
        item: Track,
        mangaId: Long,
    ) = Unit

    override suspend fun setRemoteStatus(
        track: Track,
        status: Long,
    ) = Unit

    override suspend fun setRemoteLastChapterRead(
        track: Track,
        chapterNumber: Int,
    ) = Unit

    override suspend fun setRemoteScore(
        track: Track,
        scoreString: String,
    ) = Unit

    override suspend fun setRemoteStartDate(
        track: Track,
        epochMillis: Long,
    ) = Unit

    override suspend fun setRemoteFinishDate(
        track: Track,
        epochMillis: Long,
    ) = Unit

    override suspend fun setRemotePrivate(
        track: Track,
        private: Boolean,
    ) = Unit
}
