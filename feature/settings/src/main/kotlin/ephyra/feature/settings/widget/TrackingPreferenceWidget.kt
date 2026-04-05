package ephyra.feature.settings.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ephyra.domain.track.service.Tracker
import ephyra.feature.settings.LocalPreferenceHighlighted
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.manga.track.components.TrackLogoIcon

@Composable
fun TrackingPreferenceWidget(
    modifier: Modifier = Modifier,
    tracker: Tracker,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val highlighted = LocalPreferenceHighlighted.current
    Box(modifier = Modifier.highlightBackground(highlighted)) {
        Row(
            modifier = modifier
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .fillMaxWidth()
                .padding(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackLogoIcon(tracker)
            Text(
                text = tracker.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge,
                fontSize = TitleFontSize,
            )
            AnimatedVisibility(
                visible = checked,
                enter = fadeIn(MotionTokens.tweenEnter()) + scaleIn(MotionTokens.tweenEnter()),
                exit = fadeOut(MotionTokens.tweenExit()) + scaleOut(MotionTokens.tweenExit()),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = stringResource(MR.strings.login_success),
                )
            }
        }
    }
}
