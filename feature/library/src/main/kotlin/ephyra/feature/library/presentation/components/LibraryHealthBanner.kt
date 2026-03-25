package ephyra.feature.library.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import ephyra.i18n.MR
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.MotionTokens

@Composable
fun LibraryHealthBanner(
    deadCount: Int,
    degradedCount: Int,
    modifier: Modifier = Modifier,
    onClickFilter: () -> Unit,
) {
    AnimatedVisibility(
        visible = deadCount > 0 || degradedCount > 0,
        enter = expandVertically(
            animationSpec = tween(
                durationMillis = MotionTokens.DURATION_MEDIUM,
                easing = MotionTokens.EasingDecelerate,
            ),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = MotionTokens.DURATION_MEDIUM,
                easing = MotionTokens.EasingStandard,
            ),
        ),
        exit = shrinkVertically(
            animationSpec = tween(
                durationMillis = MotionTokens.DURATION_SHORT,
                easing = MotionTokens.EasingAccelerate,
            ),
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = MotionTokens.DURATION_SHORT,
                easing = MotionTokens.EasingStandard,
            ),
        ),
    ) {
        val isDead = deadCount > 0
        val containerColor = if (isDead) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
        val contentColor = if (isDead) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(containerColor)
                .clickable(
                    onClickLabel = stringResource(MR.strings.source_health_banner_description),
                    onClick = onClickFilter,
                )
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                )
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = if (isDead) {
                    stringResource(MR.strings.source_health_warning_dead)
                } else {
                    stringResource(MR.strings.source_health_warning_degraded)
                },
                tint = contentColor,
            )

            Text(
                text = stringResource(MR.strings.library_health_banner_summary),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )

            val parts = buildList {
                if (deadCount > 0) add(stringResource(MR.strings.library_health_banner_dead, deadCount))
                if (degradedCount > 0) add(stringResource(MR.strings.library_health_banner_degraded, degradedCount))
            }
            Text(
                text = parts.joinToString(", "),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
