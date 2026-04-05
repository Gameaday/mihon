package ephyra.feature.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.migration.manga.MigrateMangaScreen
import ephyra.feature.browse.presentation.MigrateSourceScreen
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.i18n.stringResource
import kotlinx.collections.immutable.persistentListOf

@Composable
fun Screen.migrateSourceTab(): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<MigrateSourceScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = MR.strings.label_migration,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.migration_help_guide),
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = {
                    uriHandler.openUri("https://ephyra.app/docs/guides/source-migration")
                },
            ),
        ),
        content = { contentPadding, _ ->
            MigrateSourceScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrateMangaScreen(source.id))
                },
                onToggleSortingDirection = screenModel::toggleSortingDirection,
                onToggleSortingMode = screenModel::toggleSortingMode,
            )
        },
    )
}
