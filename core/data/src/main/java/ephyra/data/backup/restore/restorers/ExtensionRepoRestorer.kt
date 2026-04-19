package ephyra.data.backup.restore.restorers

import ephyra.data.backup.models.BackupExtensionRepos
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository

class ExtensionRepoRestorer(
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val getExtensionRepos: GetExtensionRepo,
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        val dbRepos = getExtensionRepos.getAll()
        val existingReposBySHA = dbRepos.associateBy { it.signingKeyFingerprint }
        val existingReposByUrl = dbRepos.associateBy { it.baseUrl }

        val urlExists = existingReposByUrl[backupRepo.baseUrl]
        val shaExists = existingReposBySHA[backupRepo.signingKeyFingerprint]

        if (urlExists != null && urlExists.signingKeyFingerprint != backupRepo.signingKeyFingerprint) {
            error("Already Exists with different signing key fingerprint")
        } else if (shaExists != null) {
            error("${shaExists.name} has the same signing key fingerprint")
        } else {
            extensionRepoRepository.insertRepo(
                backupRepo.baseUrl,
                backupRepo.name,
                backupRepo.shortName,
                backupRepo.website,
                backupRepo.signingKeyFingerprint,
            )
        }
    }
}
