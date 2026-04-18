package ephyra.feature.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.settings.screen.browse.components.ExtensionRepoConfirmDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoConflictDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoCreateDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoDeleteDialog
import ephyra.feature.settings.screen.browse.components.ExtensionReposScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest

class ExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<ExtensionReposScreenModel>()
        val state by screenModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(url) {
            url?.let { screenModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Confirm(it))) }
        }

        if (state is RepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as RepoScreenState.Success

        ExtensionReposScreen(
            state = successState,
            onClickCreate = { screenModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Create)) },
            onOpenWebsite = { context.openInBrowser(it.website) },
            onClickDelete = { screenModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Delete(it))) },
            onClickRefresh = { screenModel.onEvent(ExtensionReposScreenEvent.RefreshRepos) },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is RepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onCreate = { screenModel.onEvent(ExtensionReposScreenEvent.CreateRepo(it)) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }

            is RepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onDelete = { screenModel.onEvent(ExtensionReposScreenEvent.DeleteRepo(dialog.repo)) },
                    repo = dialog.repo,
                )
            }

            is RepoDialog.Conflict -> {
                ExtensionRepoConflictDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onMigrate = { screenModel.onEvent(ExtensionReposScreenEvent.ReplaceRepo(dialog.newRepo)) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }

            is RepoDialog.Confirm -> {
                ExtensionRepoConfirmDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onCreate = { screenModel.onEvent(ExtensionReposScreenEvent.CreateRepo(dialog.url)) },
                    repo = dialog.url,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
