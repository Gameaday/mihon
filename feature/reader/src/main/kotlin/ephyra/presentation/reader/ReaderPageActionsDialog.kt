package ephyra.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ephyra.i18n.MR
import ephyra.presentation.core.components.ActionButton
import ephyra.presentation.core.components.AdaptiveSheet
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBlockPage: () -> Unit,
    onUnblockPage: (String) -> Unit,
    findMatchingBlockedHash: suspend () -> String?,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    var showBlockPageDialog by remember { mutableStateOf(false) }
    var showUnblockPageDialog by remember { mutableStateOf(false) }
    var matchingHash by remember { mutableStateOf<String?>(null) }
    var checkComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        matchingHash = findMatchingBlockedHash()
        checkComplete = true
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Row(
            modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.set_as_cover),
                icon = Icons.Outlined.Photo,
                onClick = { showSetCoverDialog = true },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_copy_to_clipboard),
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    onShare(true)
                    onDismissRequest()
                },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_share),
                icon = Icons.Outlined.Share,
                onClick = {
                    onShare(false)
                    onDismissRequest()
                },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_save),
                icon = Icons.Outlined.Save,
                onClick = {
                    onSave()
                    onDismissRequest()
                },
            )
            if (checkComplete && matchingHash != null) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_unblock_page),
                    icon = Icons.Outlined.CheckCircleOutline,
                    onClick = { showUnblockPageDialog = true },
                )
            } else {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_block_page),
                    icon = Icons.Outlined.Block,
                    onClick = { showBlockPageDialog = true },
                )
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                onSetAsCover()
                showSetCoverDialog = false
            },
            onDismiss = { showSetCoverDialog = false },
        )
    }

    if (showBlockPageDialog) {
        BlockPageDialog(
            onConfirm = {
                onBlockPage()
                showBlockPageDialog = false
                onDismissRequest()
            },
            onDismiss = { showBlockPageDialog = false },
        )
    }

    if (showUnblockPageDialog) {
        UnblockPageDialog(
            onConfirm = {
                matchingHash?.let { onUnblockPage(it) }
                showUnblockPageDialog = false
                onDismissRequest()
            },
            onDismiss = { showUnblockPageDialog = false },
        )
    }
}

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun BlockPageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(MR.strings.action_block_page))
        },
        text = {
            Text(stringResource(MR.strings.confirm_block_page))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun UnblockPageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(MR.strings.action_unblock_page))
        },
        text = {
            Text(stringResource(MR.strings.confirm_unblock_page))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
