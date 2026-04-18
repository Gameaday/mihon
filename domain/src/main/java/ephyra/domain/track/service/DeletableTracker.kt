package ephyra.domain.track.service

import ephyra.domain.track.model.Track

/**
 * Marker interface for trackers that support deleting an entry from a user's list.
 */
interface DeletableTracker {

    suspend fun delete(track: Track)
}
