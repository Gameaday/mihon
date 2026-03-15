package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import logcat.LogPriority
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val parallelSourceLimit by downloadPreferences.parallelSourceLimit().collectAsState()
        val parallelPageLimit by downloadPreferences.parallelPageLimit().collectAsState()
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.saveChaptersAsCBZ(),
                title = stringResource(MR.strings.save_chapter_as_cbz),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.splitTallImages(),
                title = stringResource(MR.strings.split_tall_images),
                subtitle = stringResource(MR.strings.split_tall_images_summary),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = parallelSourceLimit,
                valueRange = 1..10,
                title = stringResource(MR.strings.pref_download_concurrent_sources),
                onValueChanged = { downloadPreferences.parallelSourceLimit().set(it) },
            ),
            Preference.PreferenceItem.SliderPreference(
                value = parallelPageLimit,
                valueRange = 1..15,
                title = stringResource(MR.strings.pref_download_concurrent_pages),
                subtitle = stringResource(MR.strings.pref_download_concurrent_pages_summary),
                onValueChanged = { downloadPreferences.parallelPageLimit().set(it) },
            ),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            getPageFilterGroup(downloadPreferences = downloadPreferences),
            getJellyfinSyncGroup(downloadPreferences = downloadPreferences),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(MR.strings.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.removeAfterReadSlots(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(MR.strings.last_read_chapter),
                        1 to stringResource(MR.strings.second_to_last),
                        2 to stringResource(MR.strings.third_to_last),
                        3 to stringResource(MR.strings.fourth_to_last),
                        4 to stringResource(MR.strings.fifth_to_last),
                    ),
                    title = stringResource(MR.strings.pref_remove_after_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(MR.strings.pref_remove_bookmarked_chapters),
                ),
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeCategories(),
            entries = categories()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
            title = stringResource(MR.strings.pref_remove_exclude_categories),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnreadChaptersOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            val categoryById = remember(allCategories) { allCategories.associateBy { it.id.toString() } }
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { categoryById[it] },
                initialInversed = excluded.mapNotNull { categoryById[it] },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(newIncluded.fastMap { it.id.toString() }.toSet())
                    downloadNewChapterCategoriesExcludePref.set(newExcluded.fastMap { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewChaptersPref,
                    title = stringResource(MR.strings.pref_download_new),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnreadChaptersOnlyPref,
                    title = stringResource(MR.strings.pref_download_new_unread_chapters_only),
                    enabled = downloadNewChapters,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    enabled = downloadNewChapters,
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileReading(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(MR.plurals.next_unread_chapters, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.auto_download_while_reading),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.download_ahead_info)),
            ),
        )
    }

    @Composable
    private fun getPageFilterGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val blockedHashes by downloadPreferences.blockedPageHashes().collectAsState()
        val count = blockedHashes.size
        var showClearDialog by rememberSaveable { mutableStateOf(false) }
        var showManageDialog by rememberSaveable { mutableStateOf(false) }
        var hashToRemove by rememberSaveable { mutableStateOf<String?>(null) }

        if (showClearDialog && count > 0) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                text = {
                    Text(stringResource(MR.strings.pref_clear_blocked_pages_confirm, count))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            downloadPreferences.blockedPageHashes().set(emptySet())
                            showClearDialog = false
                            context.toast(MR.strings.blocked_pages_cleared)
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        val currentHashToRemove = hashToRemove
        if (currentHashToRemove != null) {
            AlertDialog(
                onDismissRequest = { hashToRemove = null },
                text = {
                    Text(stringResource(MR.strings.pref_remove_blocked_page_confirm, currentHashToRemove))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val pref = downloadPreferences.blockedPageHashes()
                            val current = pref.get().toMutableSet()
                            current.remove(currentHashToRemove)
                            pref.set(current)
                            hashToRemove = null
                            context.toast(MR.strings.page_unblocked)
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { hashToRemove = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showManageDialog && count > 0) {
            BlockedPagesManageDialog(
                hashes = blockedHashes,
                onRemoveHash = { hex -> hashToRemove = hex },
                onDismiss = { showManageDialog = false },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_page_filter_group),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.InfoPreference(
                    if (count > 0) {
                        stringResource(MR.strings.pref_blocked_pages_summary, count)
                    } else {
                        stringResource(MR.strings.pref_blocked_pages_empty)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_manage_blocked_pages),
                    enabled = count > 0,
                    subtitle = if (count > 0) {
                        stringResource(MR.strings.pref_manage_blocked_pages_subtitle)
                    } else {
                        null
                    },
                    onClick = { showManageDialog = true },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_blocked_pages),
                    enabled = count > 0,
                    onClick = { showClearDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun BlockedPagesManageDialog(
        hashes: Set<String>,
        onRemoveHash: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val sortedHashes = remember(hashes) { hashes.sorted() }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(MR.strings.pref_manage_blocked_pages))
            },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(sortedHashes.size) { index ->
                        val hex = sortedHashes[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = hex,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                            IconButton(
                                onClick = { onRemoveHash(hex) },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(MR.strings.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
        )
    }

    @Composable
    private fun getJellyfinSyncGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val isLoggedIn = trackerManager.jellyfin.isLoggedIn
        val isAdmin by trackPreferences.jellyfinIsAdmin().collectAsState()
        val autoSync by downloadPreferences.autoSyncToJellyfin().collectAsState()

        // Jellyfin library folder picker (SAF — supports network shares via third-party providers)
        val jellyfinFolderPref = downloadPreferences.jellyfinLibraryFolder()
        val jellyfinFolder by jellyfinFolderPref.collectAsState()
        val pickJellyfinFolder = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    logcat(LogPriority.ERROR, e)
                    context.toast(MR.strings.file_picker_uri_permission_unsupported)
                }
                UniFile.fromUri(context, uri)?.let {
                    jellyfinFolderPref.set(it.uri.toString())
                }
            }
        }

        val jellyfinFolderSubtitle = if (jellyfinFolder.isBlank()) {
            stringResource(MR.strings.pref_jellyfin_library_folder_not_set)
        } else {
            remember(jellyfinFolder) {
                UniFile.fromUri(context, jellyfinFolder.toUri())?.displayablePath
            } ?: jellyfinFolder
        }

        val items = buildList<Preference.PreferenceItem<out Any, out Any>> {
            if (!isLoggedIn) {
                add(
                    Preference.PreferenceItem.InfoPreference(
                        stringResource(MR.strings.pref_jellyfin_not_logged_in),
                    ),
                )
            }

            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.autoSyncToJellyfin(),
                    title = stringResource(MR.strings.pref_auto_sync_to_jellyfin),
                    subtitle = stringResource(MR.strings.pref_auto_sync_to_jellyfin_summary),
                    enabled = isLoggedIn,
                ),
            )

            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.jellyfinCompatibleNaming(),
                    title = stringResource(MR.strings.pref_jellyfin_compatible_naming),
                    subtitle = stringResource(MR.strings.pref_jellyfin_compatible_naming_summary),
                    enabled = isLoggedIn && autoSync,
                ),
            )

            add(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_jellyfin_library_folder),
                    subtitle = jellyfinFolderSubtitle,
                    enabled = isLoggedIn && autoSync,
                    onClick = {
                        try {
                            pickJellyfinFolder.launch(null)
                        } catch (e: ActivityNotFoundException) {
                            context.toast(MR.strings.file_picker_error)
                        }
                    },
                ),
            )

            if (jellyfinFolder.isNotBlank() && isLoggedIn && autoSync) {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_jellyfin_library_folder_clear),
                        subtitle = null,
                        enabled = true,
                        onClick = { jellyfinFolderPref.set("") },
                    ),
                )
            }

            if (jellyfinFolder.isBlank() && isLoggedIn && autoSync) {
                add(
                    Preference.PreferenceItem.InfoPreference(
                        stringResource(MR.strings.pref_jellyfin_library_folder_hint),
                    ),
                )
            }

            add(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.jellyfinUploadScope(),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.jellyfin_scope_all),
                        1 to stringResource(MR.strings.jellyfin_scope_read),
                        2 to stringResource(MR.strings.jellyfin_scope_downloaded),
                    ),
                    title = stringResource(MR.strings.pref_jellyfin_upload_scope),
                    subtitle = stringResource(MR.strings.pref_jellyfin_upload_scope_summary),
                    enabled = isLoggedIn && autoSync,
                ),
            )

            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.jellyfinScanAfterSync(),
                    title = stringResource(MR.strings.pref_jellyfin_scan_after_sync),
                    subtitle = if (!isAdmin && isLoggedIn) {
                        stringResource(MR.strings.pref_jellyfin_not_admin_hint)
                    } else {
                        stringResource(MR.strings.pref_jellyfin_scan_after_sync_summary)
                    },
                    enabled = isLoggedIn && autoSync && isAdmin,
                ),
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_jellyfin_sync),
            preferenceItems = items.toImmutableList(),
        )
    }
}
