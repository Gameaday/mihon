package ephyra.data.backup.restore

import android.content.Context
import android.net.Uri
import ephyra.data.backup.BackupDecoder
import ephyra.data.backup.restore.restorers.CategoriesRestorer
import ephyra.data.backup.restore.restorers.ExtensionRepoRestorer
import ephyra.data.backup.restore.restorers.MangaRestorer
import ephyra.data.backup.restore.restorers.PreferenceRestorer
import ephyra.domain.backup.service.BackupNotifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalSerializationApi::class)
class BackupRestorer(
    private val context: Context,
    private val categoriesRestorer: CategoriesRestorer,
    private val preferenceRestorer: PreferenceRestorer,
    private val extensionRepoRestorer: ExtensionRepoRestorer,
    private val mangaRestorer: MangaRestorer,
    private val notifier: BackupNotifier,
) {

    private var restoreAmount = 0
    private val restoreProgress = AtomicInteger(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()

    suspend fun restore(uri: Uri, options: RestoreOptions? = null, onProgress: (Int, Int, String) -> Unit) {
        val startTime = System.currentTimeMillis()
        restoreProgress.set(0)
        errors.clear()

        val backup = try {
            BackupDecoder(context, ProtoBuf).decode(uri)
        } catch (e: Exception) {
            val time = System.currentTimeMillis() - startTime
            errors.add(Pair(Date(), e.message ?: "Failed to decode backup"))
            notifier.showRestoreComplete(time, errors.size, uri.path)
            return
        }

        val effectiveOptions = options ?: RestoreOptions()

        // Restore categories first so that manga can be assigned to them
        if (effectiveOptions.categories) {
            try {
                categoriesRestorer(backup.backupCategories)
            } catch (e: Exception) {
                errors.add(Pair(Date(), "Categories: ${e.message}"))
            }
        }

        // Restore app-level preferences
        if (effectiveOptions.appSettings) {
            try {
                preferenceRestorer.restoreApp(backup.backupPreferences, backup.backupCategories)
            } catch (e: Exception) {
                errors.add(Pair(Date(), "App preferences: ${e.message}"))
            }
        }

        // Restore extension repository entries
        if (effectiveOptions.extensionRepoSettings) {
            backup.backupExtensionRepo.forEach { repo ->
                try {
                    extensionRepoRestorer(repo)
                } catch (e: Exception) {
                    errors.add(Pair(Date(), "Extension repo ${repo.name}: ${e.message}"))
                }
            }
        }

        // Restore per-source preferences
        if (effectiveOptions.sourceSettings) {
            try {
                preferenceRestorer.restoreSource(backup.backupSourcePreferences)
            } catch (e: Exception) {
                errors.add(Pair(Date(), "Source preferences: ${e.message}"))
            }
        }

        // Restore library entries (manga, chapters, history, tracking)
        if (effectiveOptions.libraryEntries) {
            val sortedMangas = mangaRestorer.sortByNew(backup.backupManga)
            restoreAmount = sortedMangas.size

            coroutineScope {
                sortedMangas.forEach { backupManga ->
                    launch {
                        ensureActive()
                        try {
                            mangaRestorer.restore(backupManga, backup.backupCategories)
                        } catch (e: Exception) {
                            errors.add(Pair(Date(), "${backupManga.title}: ${e.message}"))
                        }
                        val progress = restoreProgress.incrementAndGet()
                        onProgress(progress, restoreAmount, backupManga.title)
                    }
                }
            }
        }

        val time = System.currentTimeMillis() - startTime
        notifier.showRestoreComplete(time, errors.size, uri.path)
    }
}
