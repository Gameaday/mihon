package ephyra.domain.extensionrepo.interactor

import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository

class GetExtensionRepoCount(
    private val repository: ExtensionRepoRepository,
) {
    fun subscribe() = repository.getCount()
}
