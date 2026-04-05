package ephyra.feature.manga.track

import ephyra.domain.track.service.Tracker
import ephyra.domain.track.model.Track

data class TrackItem(val track: Track?, val tracker: Tracker)
