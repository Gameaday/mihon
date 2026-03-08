package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tachiyomi.domain.manga.model.LockedField
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Dialog for toggling per-field metadata locks (Jellyfin-style).
 * Locked fields are preserved during authority refresh — the user's
 * custom edits won't be overwritten by the canonical source.
 */
@Composable
fun MetadataLocksDialog(
    lockedFields: Long,
    onToggleField: (Long) -> Unit,
    onSetAllFields: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.metadata_locks_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(MR.strings.metadata_locks_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = { onSetAllFields(LockedField.ALL) }) {
                        Text(text = stringResource(MR.strings.metadata_locks_lock_all))
                    }
                    TextButton(onClick = { onSetAllFields(LockedField.NONE) }) {
                        Text(text = stringResource(MR.strings.metadata_locks_unlock_all))
                    }
                }
                LockedField.ALL_FIELDS.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) { onToggleField(field) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = LockedField.isLocked(lockedFields, field),
                            onCheckedChange = null,
                        )
                        Text(
                            text = fieldLabel(field),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun fieldLabel(field: Long): String = when (field) {
    LockedField.DESCRIPTION -> stringResource(MR.strings.locked_field_description)
    LockedField.AUTHOR -> stringResource(MR.strings.locked_field_author)
    LockedField.ARTIST -> stringResource(MR.strings.locked_field_artist)
    LockedField.COVER -> stringResource(MR.strings.locked_field_cover)
    LockedField.STATUS -> stringResource(MR.strings.locked_field_status)
    LockedField.CONTENT_TYPE -> stringResource(MR.strings.locked_field_content_type)
    else -> error("Unknown locked field: $field")
}
