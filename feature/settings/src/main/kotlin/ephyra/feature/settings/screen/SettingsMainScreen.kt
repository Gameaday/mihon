package ephyra.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import ephyra.feature.settings.screen.about.AboutScreen
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.LocalBackPress
import ephyra.presentation.core.util.Screen
import kotlinx.collections.immutable.persistentListOf
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

object SettingsMainScreen : Screen() {

    @Composable
    override fun Content() {
        Content(twoPane = false)
    }

    @Composable
    private fun getPalerSurface(): Color {
        return MaterialTheme.colorScheme.surfaceContainerLow
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
        val topBarState = rememberTopAppBarState()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = backPress::invoke,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = containerColor,
            content = { contentPadding ->
                val state = rememberLazyListState()
                val indexSelected = if (twoPane) {
                    items.indexOfFirst { it.screen::class == navigator.items.first()::class }
                        .also {
                            LaunchedEffect(Unit) {
                                state.animateScrollToItem(it)
                                if (it > 0) {
                                    // Lift scroll
                                    topBarState.contentOffset = topBarState.heightOffsetLimit
                                }
                            }
                        }
                } else {
                    null
                }

                LazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.titleRes.resourceId },
                    ) { index, item ->
                        val selected = indexSelected == index
                        var modifier: Modifier = Modifier
                        var contentColor = LocalContentColor.current
                        if (twoPane) {
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .then(
                                    if (selected) {
                                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                    } else {
                                        Modifier
                                    },
                                )
                            if (selected) {
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            TextPreferenceWidget(
                                modifier = modifier,
                                title = stringResource(item.titleRes),
                                subtitle = item.formatSubtitle(),
                                icon = item.icon,
                                onPreferenceClick = { navigator.navigate(item.screen, twoPane) },
                            )
                        }
                    }
                }
            },
        )
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource? = null,
        val formatSubtitle: @Composable () -> String? = { subtitleRes?.let { stringResource(it) } },
        val icon: ImageVector,
        val screen: VoyagerScreen,
    )

    private val items = listOf(
        Item(
            titleRes = MR.strings.pref_category_appearance,
            subtitleRes = MR.strings.pref_appearance_summary,
            icon = Icons.Outlined.Palette,
            screen = SettingsAppearanceScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_library,
            subtitleRes = MR.strings.pref_library_summary,
            icon = Icons.Outlined.CollectionsBookmark,
            screen = SettingsLibraryScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_reader,
            subtitleRes = MR.strings.pref_reader_summary,
            icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
            screen = SettingsReaderScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_downloads,
            subtitleRes = MR.strings.pref_downloads_summary,
            icon = Icons.Outlined.GetApp,
            screen = SettingsDownloadScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_tracking,
            subtitleRes = MR.strings.pref_tracking_summary,
            icon = Icons.Outlined.Sync,
            screen = SettingsTrackingScreen,
        ),
        Item(
            titleRes = MR.strings.label_discover,
            subtitleRes = MR.strings.pref_browse_summary,
            icon = Icons.Outlined.Explore,
            screen = SettingsBrowseScreen,
        ),
        Item(
            titleRes = MR.strings.label_data_storage,
            subtitleRes = MR.strings.pref_backup_summary,
            icon = Icons.Outlined.Storage,
            screen = SettingsDataScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_security,
            subtitleRes = MR.strings.pref_security_summary,
            icon = Icons.Outlined.Security,
            screen = SettingsSecurityScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_advanced,
            subtitleRes = MR.strings.pref_advanced_summary,
            icon = Icons.Outlined.Code,
            screen = SettingsAdvancedScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_about,
            formatSubtitle = {
                "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
            },
            icon = Icons.Outlined.Info,
            screen = AboutScreen,
        ),
    )
}
