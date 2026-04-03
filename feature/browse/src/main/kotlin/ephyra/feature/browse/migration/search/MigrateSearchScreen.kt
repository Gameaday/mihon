package ephyra.feature.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.presentation.MigrateSearchScreen
import ephyra.presentation.core.util.Screen
import org.koin.core.parameter.parametersOf
import ephyra.feature.browse.source.globalsearch.SearchScreenModel
import ephyra.feature.manga.MangaScreen
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.feature.migration.list.MigrationListScreen

class MigrateSearchScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<MigrateSearchScreenModel> { parametersOf(mangaId) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        MigrateSearchScreen(
            state = state,
            fromSourceId = state.from?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = { navigator.push(MigrateSourceSearchScreen(state.from!!, it.id, state.searchQuery)) },
            onClickItem = {
                val migrateListScreen = navigator.items
                    .filterIsInstance<MigrationListScreen>()
                    .lastOrNull()

                if (migrateListScreen == null) {
                    screenModel.setMigrateDialog(mangaId, it)
                } else {
                    migrateListScreen.addMatchOverride(current = mangaId, target = it.id)
                    navigator.popUntil { screen -> screen is MigrationListScreen }
                }
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )

        when (val dialog = state.dialog) {
            is SearchScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.current] so we show [dialog.target].
                    onClickTitle = { navigator.push(MangaScreen(dialog.target.id, true)) },
                    onDismissRequest = { screenModel.clearDialog() },
                    onComplete = {
                        if (navigator.lastItem is MangaScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(MangaScreen(dialog.target.id))
                        } else {
                            navigator.replace(MangaScreen(dialog.target.id))
                        }
                    },
                )
            }

            else -> {}
        }
    }
}
