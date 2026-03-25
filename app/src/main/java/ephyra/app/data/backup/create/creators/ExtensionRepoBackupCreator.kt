package ephyra.app.data.backup.create.creators

import ephyra.app.data.backup.models.BackupExtensionRepos
import ephyra.app.data.backup.models.backupExtensionReposMapper
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionRepoBackupCreator(
    private val getExtensionRepos: GetExtensionRepo = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionRepos> {
        return getExtensionRepos.getAll()
            .map(backupExtensionReposMapper)
    }
}
