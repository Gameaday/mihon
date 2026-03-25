package ephyra.domain.track.interactor

import ephyra.domain.track.model.toDbTrack
import ephyra.domain.track.model.toDomainTrack
import ephyra.app.data.track.Tracker
import ephyra.app.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import ephyra.core.common.util.system.logcat
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack

class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     *
     * @return Failed updates.
     */
    suspend fun await(mangaId: Long): List<Pair<Tracker?, Throwable>> {
        return supervisorScope {
            return@supervisorScope getTracks.await(mangaId)
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                .map { (track, service) ->
                    async {
                        return@async try {
                            refreshWithRetry(service!!, track, mangaId)
                            null
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    /**
     * Refreshes a single tracker with retry on transient failures (5xx, timeouts).
     */
    private suspend fun refreshWithRetry(
        service: Tracker,
        track: ephyra.domain.track.model.Track,
        mangaId: Long,
        maxRetries: Int = MAX_RETRIES,
    ) {
        repeat(maxRetries) { attempt ->
            try {
                val updatedTrack = service.refresh(track.toDbTrack()).toDomainTrack()!!
                insertTrack.await(updatedTrack)
                syncChapterProgressWithTrack.await(mangaId, updatedTrack, service)
                return
            } catch (e: Throwable) {
                if (!isRetryable(e) || attempt >= maxRetries - 1) {
                    throw e
                }
                val backoffMs = INITIAL_BACKOFF_MS * (1L shl attempt)
                logcat(LogPriority.WARN) {
                    "${service.name}: refresh attempt ${attempt + 1} failed, retrying in ${backoffMs}ms"
                }
                delay(backoffMs)
            }
        }
    }

    private fun isRetryable(e: Throwable): Boolean {
        return when (e) {
            is HttpException -> e.code in 500..599 || e.code == 429
            is java.net.SocketTimeoutException -> true
            is java.net.UnknownHostException -> false
            is java.io.IOException -> true
            else -> false
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }
}
