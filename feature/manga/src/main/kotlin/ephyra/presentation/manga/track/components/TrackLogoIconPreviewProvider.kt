package ephyra.presentation.manga.track.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import ephyra.data.track.Tracker
import ephyra.test.DummyTracker

internal class TrackLogoIconPreviewProvider : PreviewParameterProvider<Tracker> {

    override val values: Sequence<Tracker>
        get() = sequenceOf(
            DummyTracker(
                id = 1L,
                name = "Dummy Tracker",
            ),
        )
}
