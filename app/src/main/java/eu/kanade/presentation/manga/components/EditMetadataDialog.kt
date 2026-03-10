package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.LockedField
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Jellyfin-style metadata editor dialog.
 * Combines field editing with inline lock toggles — editing a field
 * auto-locks it so manual edits are preserved during authority refresh.
 *
 * Mirrors Jellyfin's series metadata editor where each field is editable
 * and individually lockable, with an "Identify" button to search providers.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMetadataDialog(
    title: String,
    author: String?,
    artist: String?,
    description: String?,
    status: Long,
    genres: List<String>,
    lockedFields: Long,
    hasAuthority: Boolean,
    authorityLabel: String?,
    onSaveTitle: (String) -> Unit,
    onSaveAuthor: (String) -> Unit,
    onSaveArtist: (String) -> Unit,
    onSaveDescription: (String) -> Unit,
    onSaveStatus: (Long) -> Unit,
    onSaveGenres: (List<String>) -> Unit,
    onToggleLock: (Long) -> Unit,
    onSetAllLocks: (Long) -> Unit,
    onIdentify: (() -> Unit)?,
    onUnlinkAuthority: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    var editTitle by remember { mutableStateOf(title) }
    var editAuthor by remember { mutableStateOf(author ?: "") }
    var editArtist by remember { mutableStateOf(artist ?: "") }
    var editDescription by remember { mutableStateOf(description ?: "") }
    var editStatus by remember { mutableStateOf(status) }
    var editGenres by remember { mutableStateOf(genres) }
    var newGenre by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(MR.strings.edit_metadata_title))
                if (onIdentify != null) {
                    FilledTonalButton(onClick = onIdentify) {
                        Icon(
                            imageVector = if (hasAuthority) {
                                Icons.Outlined.Refresh
                            } else {
                                Icons.Outlined.Search
                            },
                            contentDescription = if (hasAuthority) {
                                stringResource(MR.strings.edit_metadata_refresh)
                            } else {
                                stringResource(MR.strings.edit_metadata_identify)
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasAuthority) {
                                stringResource(MR.strings.edit_metadata_refresh)
                            } else {
                                stringResource(MR.strings.edit_metadata_identify)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(),
            ) {
                // Authority provider badge shown when linked to a metadata provider
                if (hasAuthority && authorityLabel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = stringResource(MR.strings.authority_badge_description),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(MR.strings.edit_metadata_linked_to, authorityLabel),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            if (onUnlinkAuthority != null) {
                                TextButton(
                                    onClick = onUnlinkAuthority,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LinkOff,
                                        contentDescription = stringResource(MR.strings.edit_metadata_unlink),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(MR.strings.edit_metadata_unlink),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = stringResource(MR.strings.edit_metadata_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Lock All / Unlock All
                if (hasAuthority) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { onSetAllLocks(LockedField.ALL) }) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = stringResource(MR.strings.metadata_locks_lock_all),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(MR.strings.metadata_locks_lock_all))
                        }
                        TextButton(onClick = { onSetAllLocks(LockedField.NONE) }) {
                            Icon(
                                imageVector = Icons.Outlined.LockOpen,
                                contentDescription = stringResource(MR.strings.metadata_locks_unlock_all),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(MR.strings.metadata_locks_unlock_all))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // -- Basic info section header --
                SectionHeader(text = stringResource(MR.strings.edit_metadata_section_basic))

                // Title
                MetadataTextField(
                    label = stringResource(MR.strings.locked_field_title),
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    isLocked = LockedField.isLocked(lockedFields, LockedField.TITLE),
                    onToggleLock = if (hasAuthority) {
                        { onToggleLock(LockedField.TITLE) }
                    } else {
                        null
                    },
                    onSave = { onSaveTitle(editTitle) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Author
                MetadataTextField(
                    label = stringResource(MR.strings.locked_field_author),
                    value = editAuthor,
                    onValueChange = { editAuthor = it },
                    isLocked = LockedField.isLocked(lockedFields, LockedField.AUTHOR),
                    onToggleLock = if (hasAuthority) {
                        { onToggleLock(LockedField.AUTHOR) }
                    } else {
                        null
                    },
                    onSave = { onSaveAuthor(editAuthor) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Artist
                MetadataTextField(
                    label = stringResource(MR.strings.locked_field_artist),
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    isLocked = LockedField.isLocked(lockedFields, LockedField.ARTIST),
                    onToggleLock = if (hasAuthority) {
                        { onToggleLock(LockedField.ARTIST) }
                    } else {
                        null
                    },
                    onSave = { onSaveArtist(editArtist) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                MetadataTextField(
                    label = stringResource(MR.strings.locked_field_description),
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    isLocked = LockedField.isLocked(lockedFields, LockedField.DESCRIPTION),
                    onToggleLock = if (hasAuthority) {
                        { onToggleLock(LockedField.DESCRIPTION) }
                    } else {
                        null
                    },
                    singleLine = false,
                    maxLines = 5,
                    onSave = { onSaveDescription(editDescription) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // -- Details section header --
                SectionHeader(text = stringResource(MR.strings.edit_metadata_section_details))

                // Status dropdown
                StatusDropdown(
                    status = editStatus,
                    isLocked = LockedField.isLocked(lockedFields, LockedField.STATUS),
                    onToggleLock = if (hasAuthority) {
                        { onToggleLock(LockedField.STATUS) }
                    } else {
                        null
                    },
                    onStatusChange = { newStatus ->
                        editStatus = newStatus
                        onSaveStatus(newStatus)
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // -- Tags & genres section header --
                SectionHeader(text = stringResource(MR.strings.edit_metadata_section_tags))

                // Genres
                GenreEditor(
                    genres = editGenres,
                    newGenre = newGenre,
                    onNewGenreChange = { newGenre = it },
                    isLocked = LockedField.isLocked(lockedFields, LockedField.GENRE),
                    onToggleLock = if (hasAuthority) {
                        { onToggleLock(LockedField.GENRE) }
                    } else {
                        null
                    },
                    onAddGenre = {
                        val trimmed = newGenre.trim()
                        if (trimmed.isNotBlank() && !editGenres.any { it.equals(trimmed, ignoreCase = true) }) {
                            editGenres = editGenres + trimmed
                            newGenre = ""
                            onSaveGenres(editGenres)
                        }
                    },
                    onRemoveGenre = { genre ->
                        editGenres = editGenres.filter { it != genre }
                        onSaveGenres(editGenres)
                    },
                )
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
private fun MetadataTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isLocked: Boolean,
    onToggleLock: (() -> Unit)?,
    onSave: () -> Unit,
    singleLine: Boolean = true,
    maxLines: Int = 1,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label)
                if (isLocked) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = maxLines,
        trailingIcon = {
            if (onToggleLock != null) {
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (isLocked) {
                            stringResource(MR.strings.metadata_unlock_field)
                        } else {
                            stringResource(MR.strings.metadata_lock_field)
                        },
                        modifier = Modifier.size(20.dp),
                        tint = if (isLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(
            onDone = {
                onSave()
                focusManager.clearFocus()
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(
    status: Long,
    isLocked: Boolean,
    onToggleLock: (() -> Unit)?,
    onStatusChange: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val statusEntries = remember {
        listOf(
            SManga.UNKNOWN.toLong() to MR.strings.unknown_status,
            SManga.ONGOING.toLong() to MR.strings.ongoing,
            SManga.COMPLETED.toLong() to MR.strings.completed,
            SManga.LICENSED.toLong() to MR.strings.licensed,
            SManga.PUBLISHING_FINISHED.toLong() to MR.strings.publishing_finished,
            SManga.CANCELLED.toLong() to MR.strings.cancelled,
            SManga.ON_HIATUS.toLong() to MR.strings.on_hiatus,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = stringResource(
                    statusEntries.find { it.first == status }?.second ?: MR.strings.unknown_status,
                ),
                onValueChange = {},
                readOnly = true,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(MR.strings.locked_field_status))
                        if (isLocked) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = stringResource(MR.strings.metadata_lock_field),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onToggleLock != null) {
                            IconButton(onClick = onToggleLock) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = if (isLocked) {
                                        stringResource(MR.strings.metadata_unlock_field)
                                    } else {
                                        stringResource(MR.strings.metadata_lock_field)
                                    },
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isLocked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                statusEntries.forEach { (statusValue, stringRes) ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(stringRes)) },
                        onClick = {
                            onStatusChange(statusValue)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreEditor(
    genres: List<String>,
    newGenre: String,
    onNewGenreChange: (String) -> Unit,
    isLocked: Boolean,
    onToggleLock: (() -> Unit)?,
    onAddGenre: () -> Unit,
    onRemoveGenre: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.locked_field_genre),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isLocked) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(MR.strings.metadata_lock_field),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (onToggleLock != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (isLocked) {
                            stringResource(MR.strings.metadata_unlock_field)
                        } else {
                            stringResource(MR.strings.metadata_lock_field)
                        },
                        modifier = Modifier.size(16.dp),
                        tint = if (isLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            modifier = Modifier.animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            genres.forEach { genre ->
                FilterChip(
                    selected = false,
                    onClick = { },
                    label = { Text(text = genre, style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_remove_genre, genre),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveGenre(genre) },
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = newGenre,
            onValueChange = onNewGenreChange,
            label = { Text(text = stringResource(MR.strings.locked_field_genre)) },
            placeholder = { Text(text = stringResource(MR.strings.edit_metadata_add_genre)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = onAddGenre,
                    enabled = newGenre.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(MR.strings.action_add_genre),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onAddGenre()
                    focusManager.clearFocus()
                },
            ),
        )
    }
}

/**
 * Jellyfin-style section header with a divider and label.
 * Groups related metadata fields visually, using Material Expression
 * with primary color accent and proper spacing hierarchy.
 */
@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
        )
    }
}
