package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

private const val MAX_LENGTH = 250
private const val MAX_LENGTH_WARN = MAX_LENGTH * 0.9

@Composable
fun MangaNotesTextArea(
    state: MangaNotesScreen.State,
    onUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var textFieldValue by remember { mutableStateOf(TextFieldValue(state.notes)) }

    DisposableEffect(scope) {
        snapshotFlow { textFieldValue.text }
            .debounce(0.25.seconds)
            .distinctUntilChanged()
            .onEach { onUpdate(it) }
            .launchIn(scope)

        onDispose {
            onUpdate(textFieldValue.text)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.small)
            .fillMaxSize(),
    ) {
        TextField(
            value = textFieldValue,
            onValueChange = { if (it.text.length <= MAX_LENGTH) textFieldValue = it },
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(text = stringResource(MR.strings.notes_placeholder))
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .fillMaxWidth(),
        ) {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    MangaNotesTextAreaButton(
                        onClick = {
                            textFieldValue = textFieldValue.applyIfWithinLimit { wrapMarkdownSyntax("**") }
                        },
                        icon = Icons.Outlined.FormatBold,
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = {
                            textFieldValue = textFieldValue.applyIfWithinLimit { wrapMarkdownSyntax("*") }
                        },
                        icon = Icons.Outlined.FormatItalic,
                    )
                }
                item {
                    VerticalDivider(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.extraSmall)
                            .height(MaterialTheme.padding.large),
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = {
                            textFieldValue = textFieldValue.applyIfWithinLimit { toggleLinePrefix("- ") }
                        },
                        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = {
                            textFieldValue = textFieldValue.applyIfWithinLimit { toggleLinePrefix("1. ") }
                        },
                        icon = Icons.Outlined.FormatListNumbered,
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (MAX_LENGTH - textFieldValue.text.length).toString(),
                    color = if (textFieldValue.text.length > MAX_LENGTH_WARN) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                    modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                )
            }
        }
    }
}

/**
 * Wraps the current selection with the given markdown syntax (e.g., `**` for bold, `*` for italic).
 * If no text is selected, inserts the syntax pair at the cursor position.
 */
private fun TextFieldValue.wrapMarkdownSyntax(syntax: String): TextFieldValue {
    val text = this.text
    val selection = this.selection

    if (selection.collapsed) {
        val newText = text.substring(0, selection.start) + syntax + syntax + text.substring(selection.start)
        return this.copy(
            text = newText,
            selection = TextRange(selection.start + syntax.length),
        )
    }

    val selStart = selection.min
    val selEnd = selection.max

    val newText = text.substring(0, selStart) + syntax +
        text.substring(selStart, selEnd) + syntax +
        text.substring(selEnd)
    return this.copy(
        text = newText,
        selection = TextRange(selStart + syntax.length, selEnd + syntax.length),
    )
}

/**
 * Toggles a line prefix (e.g., `- ` for bullet lists, `1. ` for numbered lists) at the
 * beginning of the current line. Removes the prefix if already present.
 */
private fun TextFieldValue.toggleLinePrefix(prefix: String): TextFieldValue {
    val text = this.text
    val cursorPos = this.selection.min

    val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
    val lineFromStart = text.substring(lineStart)

    if (lineFromStart.startsWith(prefix)) {
        val newText = text.substring(0, lineStart) + lineFromStart.removePrefix(prefix)
        return this.copy(
            text = newText,
            selection = TextRange(maxOf(lineStart, cursorPos - prefix.length)),
        )
    }

    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return this.copy(
        text = newText,
        selection = TextRange(cursorPos + prefix.length),
    )
}

/**
 * Applies a transformation to this [TextFieldValue] only if the result stays within [MAX_LENGTH].
 * Returns the original value unchanged if the transformation would exceed the limit.
 */
private inline fun TextFieldValue.applyIfWithinLimit(
    transform: TextFieldValue.() -> TextFieldValue,
): TextFieldValue {
    val result = transform()
    return if (result.text.length <= MAX_LENGTH) result else this
}

@Composable
private fun MangaNotesTextAreaButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                onClick = onClick,
                enabled = true,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = icon.name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
        )
    }
}
