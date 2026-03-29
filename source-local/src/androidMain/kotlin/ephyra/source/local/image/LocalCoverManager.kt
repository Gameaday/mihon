package ephyra.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.storage.nameWithoutExtension
import ephyra.core.common.util.system.ImageUtil
import ephyra.source.local.io.LocalSourceFileSystem
import java.io.InputStream

actual class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {

    actual fun find(mangaUrl: String): UniFile? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all files whose names match known cover naming conventions.
            // Supports: cover.*, poster.*, folder.* (Jellyfin convention)
            .filter {
                it.isFile && it.nameWithoutExtension.let { name ->
                    name.equals("cover", ignoreCase = true) ||
                        name.equals("poster", ignoreCase = true) ||
                        name.equals("folder", ignoreCase = true)
                }
            }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    actual fun update(
        manga: SManga,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(manga.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        manga.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }

    companion object {
        private const val DEFAULT_COVER_NAME = "cover.jpg"
    }
}
