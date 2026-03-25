package ephyra.app.data.backup.create.creators

import ephyra.app.data.backup.models.BackupManga
import ephyra.app.data.backup.models.BackupSource
import eu.kanade.tachiyomi.source.Source
import ephyra.domain.source.service.SourceManager
class SourcesBackupCreator(
    private val sourceManager: SourceManager,
) {

    operator fun invoke(mangas: List<BackupManga>): List<BackupSource> {
        return mangas
            .asSequence()
            .map(BackupManga::source)
            .distinct()
            .map(sourceManager::getOrStub)
            .map { it.toBackupSource() }
            .toList()
    }
}

private fun Source.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
