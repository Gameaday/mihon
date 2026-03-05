package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
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
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LibraryHealthBanner(
    deadCount: Int,
    degradedCount: Int,
    modifier: Modifier = Modifier,
    onClickFilter: () -> Unit,
) {
    AnimatedVisibility(
        visible = deadCount > 0 || degradedCount > 0,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    if (deadCount > 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                )
                .clickable(onClick = onClickFilter)
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (deadCount > 0) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            )

            val contentColor = if (deadCount > 0) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }

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
