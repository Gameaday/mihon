package ephyra.domain.storage.service

import com.hippo.unifile.UniFile
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the app's top-level storage locations and a [changes] flow that emits
 * whenever the base storage directory is reconfigured.
 *
 * Callers in `:core/download`, `:core/data`, and `:source-local` depend only on this interface.
 * The Android implementation ([ephyra.app.data.storage.StorageManagerImpl]) lives in `:app` where
 * it may freely use [android.content.Context] and [androidx.core.net.toUri].
 */
interface StorageManager {

    companion object {
        const val AUTOMATIC_BACKUPS_PATH = "autobackup"
        const val DOWNLOADS_PATH = "downloads"
        const val LOCAL_SOURCE_PATH = "local"
    }

    /** Emits [Unit] whenever the base storage directory changes. */
    val changes: Flow<Unit>

    fun getAutomaticBackupsDirectory(): UniFile?

    fun getDownloadsDirectory(): UniFile?

    fun getLocalSourceDirectory(): UniFile?
}

