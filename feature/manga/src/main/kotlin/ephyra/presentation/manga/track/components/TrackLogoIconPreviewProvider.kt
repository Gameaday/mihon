package ephyra.presentation.manga.track.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import ephyra.domain.track.service.Tracker
import ephyra.presentation.manga.preview.DummyTracker

internal class TrackLogoIconPreviewProvider : PreviewParameterProvider<Tracker> {

    override val values: Sequence<Tracker>
        get() = sequenceOf(
            DummyTracker(
                id = 1L,
                name = "Dummy Tracker",
            ),
        )
}
