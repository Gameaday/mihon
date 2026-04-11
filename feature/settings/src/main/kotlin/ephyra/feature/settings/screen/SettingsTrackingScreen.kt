package ephyra.feature.settings.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import dev.icerock.moko.resources.StringResource
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.withUIContext
import ephyra.data.track.anilist.AnilistApi
import ephyra.data.track.bangumi.BangumiApi
import ephyra.data.track.myanimelist.MyAnimeListApi
import ephyra.data.track.shikimori.ShikimoriApi
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.model.AutoTrackState
import ephyra.domain.track.service.EnhancedTracker
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.feature.settings.Preference
import ephyra.i18n.MR
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.MatchUnlinkedJobRunner
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.runBlocking

object SettingsTrackingScreen : SearchableSettings {

    private data class LoginDialog(val tracker: Tracker, val uNameStringRes: StringResource)

    private data class LogoutDialog(val tracker: Tracker)

    private data class ImportConfirmDialog(val trackerName: String)

    private data class JellyfinLogin(val tracker: ephyra.data.track.jellyfin.Jellyfin)

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://github.com/Gameaday/Ephyra#about-this-fork") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val screenModel = koinScreenModel<SettingsTrackingScreenModel>()

        val trackPreferences = screenModel.trackPreferences
        val trackerManager = screenModel.trackerManager
        val sourceManager = screenModel.sourceManager
        val libraryPreferences = screenModel.libraryPreferences
        val trackerListImporter = screenModel.trackerListImporter
        val matchUnlinkedJobRunner = screenModel.matchUnlinkedJobRunner

        val scope = rememberCoroutineScope()

        var dialog by remember { mutableStateOf<Any?>(null) }
        var importingFromMal by remember { mutableStateOf(false) }
        var resolveResultText by remember { mutableStateOf<String?>(null) }
        dialog?.run {
            when (this) {
                is LoginDialog -> {
                    TrackingLoginDialog(
                        tracker = tracker,
                        uNameStringRes = uNameStringRes,
                        onDismissRequest = { dialog = null },
                    )
                }

                is LogoutDialog -> {
                    TrackingLogoutDialog(
                        tracker = tracker,
                        onDismissRequest = { dialog = null },
                    )
                }

                is ImportConfirmDialog -> {
                    TrackingImportConfirmDialog(
                        trackerName = trackerName,
                        onConfirm = {
                            dialog = null
                            importingFromMal = true
                            scope.launchIO {
                                val result = trackerListImporter.importFromMal()
                                withUIContext {
                                    importingFromMal = false
                                    if (result.isSuccess) {
                                        val msg = context.stringResource(
                                            MR.strings.tracker_import_success,
                                            result.imported,
                                        ) + if (result.skipped > 0) {
                                            context.stringResource(
                                                MR.strings.tracker_import_skipped,
                                                result.skipped,
                                            )
                                        } else {
                                            ""
                                        }
                                        context.toast(msg)
                                    } else {
                                        context.toast(
                                            context.stringResource(
                                                MR.strings.tracker_import_error,
                                                result.error.orEmpty(),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onDismissRequest = { dialog = null },
                    )
                }

                is JellyfinLogin -> {
                    JellyfinLoginDialog(
                        tracker = tracker,
                        onDismissRequest = { dialog = null },
                    )
                }
            }
        }

        val enhancedTrackers = trackerManager.getAll()
            .filter { it is EnhancedTracker }
            .partition { service ->
                val enhanced = service as EnhancedTracker
                val acceptedSources = enhanced.getAcceptedSources()
                // Trackers that accept all sources (empty accepted list + accept returns true)
                // are always considered "installed"
                acceptedSources.isEmpty() ||
                    sourceManager.getCatalogueSources().any { it::class.qualifiedName in acceptedSources }
            }
        var enhancedTrackerInfo = stringResource(MR.strings.enhanced_tracking_info)
        if (enhancedTrackers.second.isNotEmpty()) {
            val missingSourcesInfo = stringResource(
                MR.strings.enhanced_services_not_installed,
                enhancedTrackers.second.joinToString { it.name },
            )
            enhancedTrackerInfo += "\n\n$missingSourcesInfo"
        }

        val malName = trackerManager.get(TrackerManager.MYANIMELIST)!!.name
        val importPreferences = buildList {
            if (runBlocking { trackerManager.get(TrackerManager.MYANIMELIST)!!.isLoggedIn() }) {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = if (importingFromMal) {
                            stringResource(MR.strings.tracker_import_loading, malName)
                        } else {
                            stringResource(MR.strings.tracker_import_label, malName)
                        },
                        subtitle = stringResource(MR.strings.tracker_import_subtitle, malName),
                        enabled = !importingFromMal,
                        onClick = { dialog = ImportConfirmDialog(malName) },
                    ),
                )
            }
        }

        return buildList {
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = trackPreferences.autoUpdateTrack(),
                    title = stringResource(MR.strings.pref_auto_update_manga_sync),
                ),
            )
            add(
                Preference.PreferenceItem.ListPreference(
                    preference = trackPreferences.autoUpdateTrackOnMarkRead(),
                    entries = AutoTrackState.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toPersistentMap(),
                    title = stringResource(MR.strings.pref_auto_update_manga_on_mark_read),
                ),
            )
            add(
                Preference.PreferenceGroup(
                    title = stringResource(MR.strings.services),
                    preferenceItems = persistentListOf(
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.get(TrackerManager.MYANIMELIST)!!,
                            login = {
                                context.openInBrowser(
                                    MyAnimeListApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.get(TrackerManager.MYANIMELIST)!!) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.get(TrackerManager.ANILIST)!!,
                            login = {
                                context.openInBrowser(
                                    AnilistApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.get(TrackerManager.ANILIST)!!) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.get(TrackerManager.KITSU)!!,
                            login = {
                                dialog = LoginDialog(trackerManager.get(TrackerManager.KITSU)!!, MR.strings.email)
                            },
                            logout = { dialog = LogoutDialog(trackerManager.get(TrackerManager.KITSU)!!) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.get(TrackerManager.MANGAUPDATES)!!,
                            login = {
                                dialog =
                                    LoginDialog(trackerManager.get(TrackerManager.MANGAUPDATES)!!, MR.strings.username)
                            },
                            logout = { dialog = LogoutDialog(trackerManager.get(TrackerManager.MANGAUPDATES)!!) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.get(TrackerManager.SHIKIMORI)!!,
                            login = {
                                context.openInBrowser(
                                    ShikimoriApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.get(TrackerManager.SHIKIMORI)!!) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.get(TrackerManager.BANGUMI)!!,
                            login = {
                                context.openInBrowser(
                                    BangumiApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.get(TrackerManager.BANGUMI)!!) },
                        ),
                        Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
                    ),
                ),
            )
            // Authority management: consolidated import + link in one group
            // MangaUpdates is always available (public search — no login required).
            val hasAuthoritativeTracker = true
            if (hasAuthoritativeTracker) {
                val isJobRunning = matchUnlinkedJobRunner.isRunning(context)

                // --- Authority tracker order (reorderable) ---
                val orderPref = trackPreferences.authorityTrackerOrder()
                var currentOrder by remember { mutableStateOf(runBlocking { orderPref.get() }) }

                // Build label map for all canonical trackers
                val trackerLabels: Map<Long, String> = buildMap {
                    put(
                        trackerManager.get(TrackerManager.MANGAUPDATES)!!.id,
                        trackerManager.get(TrackerManager.MANGAUPDATES)!!.name,
                    )
                    put(
                        trackerManager.get(TrackerManager.ANILIST)!!.id,
                        trackerManager.get(TrackerManager.ANILIST)!!.name,
                    )
                    put(
                        trackerManager.get(TrackerManager.MYANIMELIST)!!.id,
                        trackerManager.get(TrackerManager.MYANIMELIST)!!.name,
                    )
                    put(
                        (trackerManager.get(TrackerManager.JELLYFIN) as ephyra.data.track.jellyfin.Jellyfin).id,
                        (trackerManager.get(TrackerManager.JELLYFIN) as ephyra.data.track.jellyfin.Jellyfin).name,
                    )
                }

                fun isAvailable(trackerId: Long): Boolean {
                    val tracker = trackerManager.get(trackerId) ?: return false
                    if (trackerId in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH) return true
                    return runBlocking { tracker.isLoggedIn() }
                }

                val authorityItems = buildList {
                    add(
                        Preference.PreferenceItem.CustomPreference(
                            title = stringResource(MR.strings.pref_authority_order_title),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.pref_authority_order_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                currentOrder.forEachIndexed { index, trackerId ->
                                    val label = trackerLabels[trackerId] ?: stringResource(MR.strings.unknown)
                                    val available = isAvailable(trackerId)
                                    androidx.compose.material3.Surface(
                                        color = if (available) {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                                        },
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (available) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!available) {
                                                Text(
                                                    text = stringResource(MR.strings.pref_authority_not_available),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.5f,
                                                    ),
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val newOrder = currentOrder.toMutableList()
                                                        java.util.Collections.swap(newOrder, index, index - 1)
                                                        currentOrder = newOrder
                                                        orderPref.set(newOrder)
                                                    }
                                                },
                                                enabled = index > 0,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ArrowUpward,
                                                    contentDescription = null,
                                                    tint = if (index > 0) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                                    },
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < currentOrder.lastIndex) {
                                                        val newOrder = currentOrder.toMutableList()
                                                        java.util.Collections.swap(newOrder, index, index + 1)
                                                        currentOrder = newOrder
                                                        orderPref.set(newOrder)
                                                    }
                                                },
                                                enabled = index < currentOrder.lastIndex,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ArrowDownward,
                                                    contentDescription = null,
                                                    tint = if (index < currentOrder.lastIndex) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                    addAll(importPreferences)
                    // --- Content source priority per field ---
                    val csPriorityPref = trackPreferences.contentSourcePriorityFields()
                    var csPriorityMask by remember { mutableLongStateOf(runBlocking { csPriorityPref.get() }) }

                    add(
                        Preference.PreferenceItem.CustomPreference(
                            title = stringResource(MR.strings.pref_content_source_priority_title),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.pref_content_source_priority_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                ephyra.domain.manga.model.LockedField.ALL_FIELDS.forEach { field ->
                                    val fieldLabel = lockedFieldLabel(field)
                                    val prefersContent = ephyra.domain.manga.model.LockedField.isLocked(
                                        csPriorityMask,
                                        field,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = fieldLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        androidx.compose.material3.FilterChip(
                                            selected = !prefersContent,
                                            onClick = {
                                                if (prefersContent) {
                                                    csPriorityMask = csPriorityMask and field.inv()
                                                    csPriorityPref.set(csPriorityMask)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(MR.strings.source_priority_authority),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        androidx.compose.material3.FilterChip(
                                            selected = prefersContent,
                                            onClick = {
                                                if (!prefersContent) {
                                                    csPriorityMask = csPriorityMask or field
                                                    csPriorityPref.set(csPriorityMask)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(MR.strings.source_priority_content),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = if (isJobRunning) {
                                stringResource(MR.strings.tracker_match_all_running)
                            } else {
                                stringResource(MR.strings.tracker_match_all_action)
                            },
                            subtitle = if (isJobRunning) {
                                stringResource(MR.strings.tracker_match_all_running_subtitle)
                            } else if (resolveResultText != null) {
                                resolveResultText
                            } else {
                                stringResource(MR.strings.tracker_match_all_subtitle)
                            },
                            enabled = !isJobRunning,
                            onClick = {
                                resolveResultText = null
                                matchUnlinkedJobRunner.start(context)
                                context.toast(MR.strings.tracker_match_all_started)
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = libraryPreferences.jellyfinSyncEnabled(),
                            title = stringResource(MR.strings.pref_jellyfin_sync_enabled),
                            subtitle = stringResource(MR.strings.pref_jellyfin_sync_enabled_summary),
                        ),
                    )
                    // Show connection info & settings when Jellyfin is logged in
                    if (runBlocking {
                            (
                                trackerManager.get(
                                    TrackerManager.JELLYFIN,
                                ) as ephyra.data.track.jellyfin.Jellyfin
                                ).isLoggedIn()
                        }
                    ) {
                        var showUpdateServerUrlDialog by remember { mutableStateOf(false) }
                        if (showUpdateServerUrlDialog) {
                            JellyfinUpdateServerUrlDialog(
                                jellyfin = (
                                    trackerManager.get(
                                        TrackerManager.JELLYFIN,
                                    ) as ephyra.data.track.jellyfin.Jellyfin
                                    ),
                                onDismissRequest = { showUpdateServerUrlDialog = false },
                            )
                        }

                        // Server info display
                        val serverName = runBlocking { trackPreferences.jellyfinServerName().get() }
                        val jellyfinUser = runBlocking { trackPreferences.jellyfinUsername().get() }
                        if (serverName.isNotBlank() || jellyfinUser.isNotBlank()) {
                            add(
                                Preference.PreferenceItem.TextPreference(
                                    title = stringResource(MR.strings.jellyfin_server_info),
                                    subtitle = buildString {
                                        if (serverName.isNotBlank()) append(serverName)
                                        if (jellyfinUser.isNotBlank()) {
                                            if (isNotEmpty()) append(" — ")
                                            append(
                                                context.stringResource(
                                                    MR.strings.jellyfin_user_selected,
                                                    jellyfinUser,
                                                ),
                                            )
                                        }
                                    },
                                ),
                            )
                        }

                        // Jellyfin library selection
                        var jellyfinLibraryName by remember { mutableStateOf<String?>(null) }
                        val currentLibraryId = runBlocking { libraryPreferences.jellyfinLibraryId().get() }

                        // Resolve library name on composition
                        if (currentLibraryId.isNotBlank()) {
                            androidx.compose.runtime.LaunchedEffect(currentLibraryId) {
                                try {
                                    val serverUrl = (
                                        trackerManager.get(
                                            TrackerManager.JELLYFIN,
                                        ) as ephyra.data.track.jellyfin.Jellyfin
                                        ).getServerUrl()
                                    val userId = trackPreferences.jellyfinUserId().get()
                                    if (userId.isNotBlank()) {
                                        val libs = (
                                            trackerManager.get(
                                                TrackerManager.JELLYFIN,
                                            ) as ephyra.data.track.jellyfin.Jellyfin
                                            ).api.getLibraries(serverUrl, userId)
                                        jellyfinLibraryName = libs.firstOrNull {
                                            it.id == currentLibraryId
                                        }?.name
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.jellyfin_library),
                                subtitle = if (currentLibraryId.isBlank()) {
                                    stringResource(MR.strings.jellyfin_library_all)
                                } else {
                                    jellyfinLibraryName ?: currentLibraryId
                                },
                                onClick = {
                                    scope.launchIO {
                                        try {
                                            val serverUrl =
                                                (
                                                    trackerManager.get(
                                                        TrackerManager.JELLYFIN,
                                                    ) as ephyra.data.track.jellyfin.Jellyfin
                                                    ).getServerUrl()
                                            val userId = trackPreferences.jellyfinUserId().get()
                                            if (userId.isNotBlank()) {
                                                val libs = (
                                                    trackerManager.get(
                                                        TrackerManager.JELLYFIN,
                                                    ) as ephyra.data.track.jellyfin.Jellyfin
                                                    ).api.getLibraries(
                                                    serverUrl,
                                                    userId,
                                                )
                                                // Show selection — for now cycle through or clear
                                                val currentIdx = libs.indexOfFirst {
                                                    it.id == currentLibraryId
                                                }
                                                val nextLib = if (currentIdx < libs.lastIndex) {
                                                    libs[currentIdx + 1]
                                                } else {
                                                    null // cycle back to "All"
                                                }
                                                libraryPreferences.jellyfinLibraryId().set(
                                                    nextLib?.id ?: "",
                                                )
                                                withUIContext {
                                                    context.toast(
                                                        if (nextLib != null) {
                                                            context.stringResource(
                                                                MR.strings.jellyfin_library_selected,
                                                                nextLib.name,
                                                            )
                                                        } else {
                                                            context.stringResource(
                                                                MR.strings.jellyfin_library_all,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withUIContext {
                                                context.toast(MR.strings.jellyfin_test_failed)
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.jellyfin_test_connection),
                                subtitle = stringResource(MR.strings.jellyfin_test_connection_summary),
                                onClick = {
                                    scope.launchIO {
                                        try {
                                            val info = (
                                                trackerManager.get(
                                                    TrackerManager.JELLYFIN,
                                                ) as ephyra.data.track.jellyfin.Jellyfin
                                                ).api.getSystemInfo(
                                                (
                                                    trackerManager.get(
                                                        TrackerManager.JELLYFIN,
                                                    ) as ephyra.data.track.jellyfin.Jellyfin
                                                    ).getServerUrl(),
                                            )
                                            // Refresh stored server name on successful test
                                            trackPreferences.jellyfinServerName().set(info.serverName)
                                            withUIContext {
                                                context.toast(
                                                    context.stringResource(
                                                        MR.strings.jellyfin_test_success,
                                                        info.serverName,
                                                        info.version,
                                                    ),
                                                )
                                            }
                                        } catch (e: Exception) {
                                            withUIContext {
                                                context.toast(MR.strings.jellyfin_test_failed)
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.jellyfin_update_server_url),
                                subtitle = stringResource(MR.strings.jellyfin_update_server_url_summary),
                                onClick = {
                                    showUpdateServerUrlDialog = true
                                },
                            ),
                        )
                    }
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = libraryPreferences.jellyfinCompatibleNaming(),
                            title = stringResource(MR.strings.pref_jellyfin_compatible_naming),
                            subtitle = stringResource(MR.strings.pref_jellyfin_compatible_naming_summary),
                        ),
                    )
                }
                add(
                    Preference.PreferenceGroup(
                        title = stringResource(MR.strings.tracker_authority_group_title),
                        preferenceItems = authorityItems.toImmutableList(),
                    ),
                )
            }
            add(
                Preference.PreferenceGroup(
                    title = stringResource(MR.strings.enhanced_services),
                    preferenceItems = (
                        enhancedTrackers.first
                            .map { service ->
                                Preference.PreferenceItem.TrackerPreference(
                                    tracker = service,
                                    login = {
                                        if (service is ephyra.data.track.jellyfin.Jellyfin) {
                                            dialog = JellyfinLogin(service)
                                        } else {
                                            (service as EnhancedTracker).loginNoop()
                                        }
                                    },
                                    logout = { dialog = LogoutDialog(service) },
                                )
                            } + listOf(Preference.PreferenceItem.InfoPreference(enhancedTrackerInfo))
                        ).toImmutableList(),
                ),
            )
        }
    }

    @Composable
    private fun TrackingLoginDialog(
        tracker: Tracker,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf(TextFieldValue(runBlocking { tracker.getUsername() })) }
        var password by remember { mutableStateOf(TextFieldValue(runBlocking { tracker.getPassword() })) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.login_title, tracker.name),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(text = stringResource(uNameStringRes)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = inputError && !processing,
                    )

                    var hidePassword by remember { mutableStateOf(true) }
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Password },
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(MR.strings.password)) },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        visualTransformation = if (hidePassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        isError = inputError && !processing,
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && username.text.isNotBlank() && password.text.isNotBlank(),
                    onClick = {
                        scope.launchIO {
                            processing = true
                            val result = checkLogin(
                                context = context,
                                tracker = tracker,
                                username = username.text,
                                password = password.text,
                            )
                            inputError = !result
                            if (result) onDismissRequest()
                            processing = false
                        }
                    },
                ) {
                    val id = if (processing) MR.strings.logging_in else MR.strings.login
                    Text(text = stringResource(id))
                }
            },
        )
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(MR.strings.logout_title, tracker.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launchIO {
                            tracker.logout()
                            withUIContext { onDismissRequest() }
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.logout))
                }
            },
            dismissButton = {
                Button(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun TrackingImportConfirmDialog(
        trackerName: String,
        onConfirm: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(MR.strings.tracker_import_title, trackerName)) },
            text = { Text(stringResource(MR.strings.tracker_import_confirm_body, trackerName)) },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                Button(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun JellyfinLoginDialog(
        tracker: ephyra.data.track.jellyfin.Jellyfin,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var serverUrl by remember { mutableStateOf(TextFieldValue(tracker.getServerUrl())) }
        var username by remember { mutableStateOf(TextFieldValue("")) }
        var password by remember { mutableStateOf(TextFieldValue("")) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.login_title, tracker.name),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(MR.strings.jellyfin_server_url)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = inputError && !processing,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(MR.strings.jellyfin_username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = inputError && !processing,
                    )
                    var hidePassword by remember { mutableStateOf(true) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(MR.strings.jellyfin_password)) },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        visualTransformation = if (hidePassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        isError = inputError && !processing,
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && serverUrl.text.isNotBlank() && username.text.isNotBlank(),
                    onClick = {
                        scope.launchIO {
                            processing = true
                            try {
                                tracker.loginWithCredentials(
                                    serverUrl.text,
                                    username.text,
                                    password.text,
                                )
                                withUIContext { onDismissRequest() }
                            } catch (e: Exception) {
                                inputError = true
                                withUIContext { context.toast(e.message ?: "") }
                            }
                            processing = false
                        }
                    },
                ) {
                    Text(stringResource(if (processing) MR.strings.logging_in else MR.strings.login))
                }
            },
        )
    }

    @Composable
    private fun JellyfinUpdateServerUrlDialog(
        jellyfin: ephyra.data.track.jellyfin.Jellyfin,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var newUrl by remember { mutableStateOf(TextFieldValue(jellyfin.getServerUrl())) }
        var processing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(MR.strings.jellyfin_update_server_url)) },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text(stringResource(MR.strings.jellyfin_server_url)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = !processing && newUrl.text.isNotBlank(),
                    onClick = {
                        scope.launchIO {
                            processing = true
                            try {
                                jellyfin.updateServerUrl(newUrl.text)
                                withUIContext {
                                    context.toast(MR.strings.jellyfin_server_updated)
                                    onDismissRequest()
                                }
                            } catch (e: Exception) {
                                withUIContext { context.toast(e.message ?: "") }
                            }
                            processing = false
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                Button(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    private suspend fun checkLogin(
        context: Context,
        tracker: Tracker,
        username: String,
        password: String,
    ): Boolean {
        return try {
            tracker.login(username, password)
            true
        } catch (e: Exception) {
            withUIContext { context.toast(e.message ?: "") }
            false
        }
    }

    private fun lockedFieldLabel(field: Long): String =
        ephyra.domain.manga.model.LockedField.label(field)
}
