package ephyra.domain.extensionrepo.interactor

import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository

class ReplaceExtensionRepo(
    private val repository: ExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        repository.replaceRepo(repo)
    }
}
