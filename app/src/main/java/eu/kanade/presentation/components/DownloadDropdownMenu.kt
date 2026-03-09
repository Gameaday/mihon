package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadDropdownMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    isJellyfinLinked: Boolean = false,
    offset: DpOffset? = null,
) {
    if (offset != null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            content = {
                DownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                    isJellyfinLinked = isJellyfinLinked,
                )
            },
        )
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = {
                DownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                    isJellyfinLinked = isJellyfinLinked,
                )
            },
        )
    }
}

@Composable
private fun DownloadDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    isJellyfinLinked: Boolean = false,
) {
    // Show Jellyfin sync options at the top when the series is linked to Jellyfin
    if (isJellyfinLinked) {
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.download_sync_to_jellyfin)) },
            onClick = {
                onDownloadClicked(DownloadAction.SYNC_TO_JELLYFIN)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.download_sync_read_to_jellyfin)) },
            onClick = {
                onDownloadClicked(DownloadAction.SYNC_READ_TO_JELLYFIN)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.download_sync_all_to_jellyfin)) },
            onClick = {
                onDownloadClicked(DownloadAction.SYNC_ALL_TO_JELLYFIN)
                onDismissRequest()
            },
        )
        HorizontalDivider()
    }

    val options = persistentListOf(
        DownloadAction.NEXT_1_CHAPTER to pluralStringResource(MR.plurals.download_amount, 1, 1),
        DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 5, 5),
        DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 10, 10),
        DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 25, 25),
        DownloadAction.UNREAD_CHAPTERS to stringResource(MR.strings.download_unread),
        DownloadAction.BOOKMARKED_CHAPTERS to stringResource(MR.strings.download_bookmarked),
    )

    options.map { (downloadAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onDownloadClicked(downloadAction)
                onDismissRequest()
            },
        )
    }
}
