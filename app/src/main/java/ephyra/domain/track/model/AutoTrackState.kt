package ephyra.domain.track.model

import dev.icerock.moko.resources.StringResource
import ephyra.i18n.MR

enum class AutoTrackState(val titleRes: StringResource) {
    ALWAYS(MR.strings.auto_track_always),
    ASK(MR.strings.auto_track_ask),
    NEVER(MR.strings.auto_track_never),
}
