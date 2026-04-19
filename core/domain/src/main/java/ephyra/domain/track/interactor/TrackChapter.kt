package ephyra.domain.track.interactor

import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.track.service.TrackingJobScheduler
import ephyra.domain.track.store.TrackingQueueStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import logcat.LogPriority

class TrackChapter(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val trackingQueue: TrackingQueueStore,
    private val trackingJobScheduler: TrackingJobScheduler,
) {

    suspend fun await(mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {
        withNonCancellableContext {
            val tracks = getTracks.await(mangaId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            val trackersToUpdate = tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn() || chapterNumber <= track.lastChapterRead) {
                    null
                } else {
                    track to service
                }
            }

            trackersToUpdate.map { (track, service) ->
                async {
                    runCatching {
                        // Stagger concurrent tracker updates to reduce burst API load
                        delay(track.trackerId * STAGGER_DELAY_PER_TRACKER_MS)
                        try {
                            val updatedTrack = service.refresh(track)
                                .copy(lastChapterRead = chapterNumber)
                            service.update(updatedTrack, true)
                            insertTrack.await(updatedTrack)
                            trackingQueue.remove(track.id)
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) {
                                "Failed to update ${service.name} for manga $mangaId, queuing for retry"
                            }
                            trackingQueue.add(track.id, chapterNumber)
                            if (setupJobOnFailure) {
                                trackingJobScheduler.scheduleDelayedSync()
                            }
                            throw e
                        }
                    }
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.WARN, it) }
        }
    }

    companion object {
        /**
         * Per-tracker stagger delay (multiplied by tracker ID) to spread concurrent
         * updates across different tracker APIs and avoid burst requests.
         */
        private const val STAGGER_DELAY_PER_TRACKER_MS = 100L
    }
}
