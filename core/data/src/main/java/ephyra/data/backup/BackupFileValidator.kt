package ephyra.data.backup

import android.content.Context
import android.net.Uri
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.service.TrackerManager

class BackupFileValidator(
    private val context: Context,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
) {

    /**
     * Checks if the given backup file is valid.
     *
     * @param uri the uri of the backup file.
     * @return the validation result.
     */
    fun validate(uri: Uri): ValidationResult {
        val backup = BackupDecoder(context, kotlinx.serialization.protobuf.ProtoBuf).decode(uri)

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val missingSources = sources.filterKeys { sourceManager.get(it) == null }
            .values.toSet()

        val trackers = backup.backupManga.flatMap { it.tracking }.map { it.syncId }.toSet()
        val missingTrackers = trackers
            .filter { trackerManager.get(it.toLong()) == null }
            .map { it.toString() }
            .toSet()

        return ValidationResult(
            missingSources = missingSources,
            missingTrackers = missingTrackers,
        )
    }

    data class ValidationResult(
        val missingSources: Set<String>,
        val missingTrackers: Set<String>,
    )
}
