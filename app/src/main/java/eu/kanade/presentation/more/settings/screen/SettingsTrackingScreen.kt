package eu.kanade.presentation.more.settings.screen

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.MatchUnlinkedJob
import eu.kanade.domain.track.interactor.TrackerListImporter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTrackingScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://mihon.app/docs/guides/tracking") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }
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
                                val importer = Injekt.get<TrackerListImporter>()
                                val result = importer.importFromMal()
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
            }
        }

        val enhancedTrackers = trackerManager.trackers
            .filter { it is EnhancedTracker }
            .partition { service ->
                val acceptedSources = (service as EnhancedTracker).getAcceptedSources()
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

        val malName = trackerManager.myAnimeList.name
        val importPreferences = buildList {
            if (trackerManager.myAnimeList.isLoggedIn) {
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
                            tracker = trackerManager.myAnimeList,
                            login = {
                                context.openInBrowser(
                                    MyAnimeListApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.myAnimeList) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.aniList,
                            login = {
                                context.openInBrowser(
                                    AnilistApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.aniList) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.kitsu,
                            login = { dialog = LoginDialog(trackerManager.kitsu, MR.strings.email) },
                            logout = { dialog = LogoutDialog(trackerManager.kitsu) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.mangaUpdates,
                            login = {
                                dialog = LoginDialog(trackerManager.mangaUpdates, MR.strings.username)
                            },
                            logout = { dialog = LogoutDialog(trackerManager.mangaUpdates) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.shikimori,
                            login = {
                                context.openInBrowser(
                                    ShikimoriApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.shikimori) },
                        ),
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = trackerManager.bangumi,
                            login = {
                                context.openInBrowser(
                                    BangumiApi.authUrl(),
                                    forceDefaultBrowser = true,
                                )
                            },
                            logout = { dialog = LogoutDialog(trackerManager.bangumi) },
                        ),
                        Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
                    ),
                ),
            )
            // Authority management: consolidated import + link in one group
            // MangaUpdates is always available (public search — no login required).
            val hasAuthoritativeTracker = true
            if (hasAuthoritativeTracker) {
                val isJobRunning = MatchUnlinkedJob.isRunning(context)

                // --- Authority tracker order (reorderable) ---
                val orderPref = trackPreferences.authorityTrackerOrder()
                var currentOrder by remember { mutableStateOf(orderPref.get()) }

                // Build label map for all canonical trackers
                val trackerLabels: Map<Long, String> = buildMap {
                    put(trackerManager.mangaUpdates.id, trackerManager.mangaUpdates.name)
                    put(trackerManager.aniList.id, trackerManager.aniList.name)
                    put(trackerManager.myAnimeList.id, trackerManager.myAnimeList.name)
                }

                fun isAvailable(trackerId: Long): Boolean {
                    val tracker = trackerManager.get(trackerId) ?: return false
                    if (trackerId in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH) return true
                    return tracker.isLoggedIn
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
                                )
                                currentOrder.forEachIndexed { index, trackerId ->
                                    val label = trackerLabels[trackerId] ?: "Unknown"
                                    val available = isAvailable(trackerId)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium,
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                        },
                    )
                    addAll(importPreferences)
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
                                MatchUnlinkedJob.start(context)
                                context.toast(MR.strings.tracker_match_all_started)
                            },
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
                                    login = { (service as EnhancedTracker).loginNoop() },
                                    logout = service::logout,
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

        var username by remember { mutableStateOf(TextFieldValue(tracker.getUsername())) }
        var password by remember { mutableStateOf(TextFieldValue(tracker.getPassword())) }
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

    private suspend fun checkLogin(
        context: Context,
        tracker: Tracker,
        username: String,
        password: String,
    ): Boolean {
        return try {
            tracker.login(username, password)
            withUIContext { context.toast(MR.strings.login_success) }
            true
        } catch (e: Throwable) {
            tracker.logout()
            withUIContext { context.toast(e.message.toString()) }
            false
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(MR.strings.logout_title, tracker.name),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(MR.strings.logout_success)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
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
            title = {
                Text(
                    text = stringResource(MR.strings.tracker_import_title, trackerName),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Text(
                    text = stringResource(MR.strings.tracker_import_confirm_body, trackerName),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                    ) {
                        Text(text = stringResource(MR.strings.action_import))
                    }
                }
            },
        )
    }
}

private data class LoginDialog(
    val tracker: Tracker,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: Tracker,
)

private data class ImportConfirmDialog(
    val trackerName: String,
)
