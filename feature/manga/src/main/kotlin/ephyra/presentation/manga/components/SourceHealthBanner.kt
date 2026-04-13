package ephyra.feature.manga.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import ephyra.domain.manga.model.SourceStatus
import ephyra.i18n.MR
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.MotionTokens

/**
 * Banner displayed on the manga detail screen when the source health is not HEALTHY.
 * Shows a warning for DEGRADED sources and an error for DEAD sources.
 * When [deadSince] is provided for DEAD sources, shows how long the source has been dead.
 * When [onMigrateClick] is provided and source is DEAD, shows a "Migrate" button.
 */
@Composable
fun SourceHealthBanner(
    sourceStatus: SourceStatus,
    modifier: Modifier = Modifier,
    deadSince: Long? = null,
    onMigrateClick: (() -> Unit)? = null,
) {
    val isVisible = sourceStatus != SourceStatus.HEALTHY && sourceStatus != SourceStatus.REPLACED

    AnimatedVisibility(
        visible = isVisible,
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
        val containerColor = when (sourceStatus) {
            SourceStatus.DEAD -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        }
        val contentColor = when (sourceStatus) {
            SourceStatus.DEAD -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }
        val text = when (sourceStatus) {
            SourceStatus.DEAD -> {
                val baseText = stringResource(MR.strings.source_health_dead)
                val durationText = deadSince?.let { formatDeadDuration(it) }
                if (durationText != null) "$baseText ($durationText)" else baseText
            }

            else -> stringResource(MR.strings.source_health_degraded)
        }

        Surface(
            modifier = modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    )
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = when (sourceStatus) {
                        SourceStatus.DEAD -> stringResource(MR.strings.source_health_warning_dead)
                        else -> stringResource(MR.strings.source_health_warning_degraded)
                    },
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (sourceStatus == SourceStatus.DEAD && onMigrateClick != null) {
                    TextButton(onClick = onMigrateClick) {
                        Text(
                            text = stringResource(MR.strings.migrate),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a dead_since timestamp into a human-readable duration string.
 */
fun formatDeadDuration(deadSince: Long): String? {
    if (deadSince <= 0) return null
    val elapsed = System.currentTimeMillis() - deadSince
    if (elapsed < 0) return null
    val days = elapsed / (24 * 60 * 60 * 1000)
    return when {
        days >= 1 -> "${days}d"
        else -> "<1d"
    }
}
