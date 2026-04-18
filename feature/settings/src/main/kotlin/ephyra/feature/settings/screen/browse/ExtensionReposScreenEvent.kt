package ephyra.feature.settings.screen.browse

import ephyra.domain.extensionrepo.model.ExtensionRepo

sealed interface ExtensionReposScreenEvent {
    data class CreateRepo(val baseUrl: String) : ExtensionReposScreenEvent
    data class ReplaceRepo(val newRepo: ExtensionRepo) : ExtensionReposScreenEvent
    data object RefreshRepos : ExtensionReposScreenEvent
    data class DeleteRepo(val baseUrl: String) : ExtensionReposScreenEvent
    data class ShowDialog(val dialog: RepoDialog) : ExtensionReposScreenEvent
    data object DismissDialog : ExtensionReposScreenEvent
}
