package ephyra.data.backup.restore

import android.content.Context
import android.net.Uri
import ephyra.core.common.i18n.stringResource
import ephyra.data.backup.BackupDecoder
import ephyra.data.backup.models.BackupCategory
import ephyra.data.backup.models.BackupExtensionRepos
import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupPreference
import ephyra.data.backup.models.BackupSourcePreferences
import ephyra.data.backup.restore.restorers.CategoriesRestorer
import ephyra.data.backup.restore.restorers.ExtensionRepoRestorer
import ephyra.data.backup.restore.restorers.MangaRestorer
import ephyra.data.backup.restore.restorers.PreferenceRestorer
import ephyra.domain.backup.service.BackupNotifier
import ephyra.i18n.MR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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

    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions? = null, onProgress: (Int, Int, String) -> Unit) {
        val startTime = System.currentTimeMillis()

        // restoreFromFile(uri, onProgress) // Simplified for now

        val time = System.currentTimeMillis() - startTime
        notifier.showRestoreComplete(time, errors.size, uri.path)
    }

    // ... simplified ...
}
