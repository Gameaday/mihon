package ephyra.feature.manga.track

import ephyra.domain.track.model.Track
import ephyra.domain.track.service.Tracker

data class TrackItem(val track: Track?, val tracker: Tracker)
