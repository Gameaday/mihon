package ephyra.feature.reader.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.ui.graphics.vector.ImageVector
import ephyra.domain.reader.model.ReaderOrientation

val ReaderOrientation.icon: ImageVector
    get() = when (this) {
        ReaderOrientation.DEFAULT -> Icons.Default.ScreenRotation
        ReaderOrientation.FREE -> Icons.Default.ScreenRotation
        ReaderOrientation.PORTRAIT -> Icons.Default.StayCurrentPortrait
        ReaderOrientation.LANDSCAPE -> Icons.Default.StayCurrentLandscape
        ReaderOrientation.LOCKED_PORTRAIT -> Icons.Default.ScreenLockPortrait
        ReaderOrientation.LOCKED_LANDSCAPE -> Icons.Default.ScreenLockLandscape
        ReaderOrientation.REVERSE_PORTRAIT -> Icons.Default.StayCurrentPortrait
    }
