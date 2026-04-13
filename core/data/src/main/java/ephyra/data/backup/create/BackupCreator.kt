package ephyra.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.logcat
import ephyra.core.data.BuildConfig
import ephyra.data.backup.BackupFileValidator
import ephyra.data.backup.create.creators.CategoriesBackupCreator
import ephyra.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.data.backup.create.creators.MangaBackupCreator
import ephyra.data.backup.create.creators.PreferenceBackupCreator
import ephyra.data.backup.create.creators.SourcesBackupCreator
import ephyra.data.backup.models.Backup
import ephyra.data.backup.models.BackupCategory
import ephyra.data.backup.models.BackupExtensionRepos
import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupPreference
import ephyra.data.backup.models.BackupSource
import ephyra.data.backup.models.BackupSourcePreferences
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.storage.service.StorageManager
import ephyra.i18n.MR
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class BackupCreator(
    private val context: Context,
    private val parser: ProtoBuf,
    private val getFavorites: GetFavorites,
    private val backupPreferences: BackupPreferences,
    private val mangaRepository: MangaRepository,
    private val categoriesBackupCreator: CategoriesBackupCreator,
    private val mangaBackupCreator: MangaBackupCreator,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val extensionRepoBackupCreator: ExtensionRepoBackupCreator,
    private val sourcesBackupCreator: SourcesBackupCreator,
    private val storageManager: StorageManager,
) {

    suspend fun createBackup(uri: Uri? = null, options: BackupOptions? = null): Uri {
        val effectiveOptions = options ?: BackupOptions()
        val filename = getFilename()
        val parentDir = if (uri != null) {
            UniFile.fromUri(context, uri)
        } else {
            storageManager.getAutomaticBackupsDirectory()
        } ?: throw Exception("Failed to find or create backup directory")

        val file = parentDir.createFile(filename)
            ?: throw Exception("Failed to create backup file")

        val backupMangas = mangaBackupCreator(getFavorites.await(), effectiveOptions)
        val backup = Backup(
            backupManga = backupMangas,
            backupCategories = categoriesBackupCreator(),
            backupSources = sourcesBackupCreator(backupMangas),
            backupPreferences = preferenceBackupCreator.createApp(true),
            backupExtensionRepo = extensionRepoBackupCreator(),
            backupSourcePreferences = preferenceBackupCreator.createSource(true),
        )

        val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
        file.openOutputStream().sink().gzip().buffer().use {
            it.write(byteArray)
        }
        return file.uri
    }

    companion object {
        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
