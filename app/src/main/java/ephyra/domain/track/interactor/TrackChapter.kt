package ephyra.domain.track.interactor

import android.content.Context
import ephyra.domain.track.model.toDbTrack
import ephyra.domain.track.model.toDomainTrack
import ephyra.domain.track.service.DelayedTrackingUpdateJob
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.app.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import logcat.LogPriority
import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack

class TrackChapter(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val delayedTrackingStore: DelayedTrackingStore,
) {

    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {
        withNonCancellableContext {
            val tracks = getTracks.await(mangaId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        // Stagger concurrent tracker updates to reduce burst API load
                        delay(track.trackerId * STAGGER_DELAY_PER_TRACKER_MS)
                        try {
                            val updatedTrack = service.refresh(track.toDbTrack())
                                .toDomainTrack(idRequired = true)!!
                                .copy(lastChapterRead = chapterNumber)
                            service.update(updatedTrack.toDbTrack(), true)
                            insertTrack.await(updatedTrack)
                            delayedTrackingStore.remove(track.id)
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) {
                                "Failed to update ${service.name} for manga $mangaId, queuing for retry"
                            }
                            delayedTrackingStore.add(track.id, chapterNumber)
                            if (setupJobOnFailure) {
                                DelayedTrackingUpdateJob.setupTask(context)
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
