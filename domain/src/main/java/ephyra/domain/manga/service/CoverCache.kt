package ephyra.domain.manga.service

import ephyra.domain.manga.model.Manga
import java.io.File

interface CoverCache {

    fun getCoverFile(manga: Manga): File?

    fun getCustomCoverFile(manga: Manga): File?

    fun setCustomCoverToCache(manga: Manga, inputStream: java.io.InputStream)

    fun deleteFromCache(manga: Manga, deleteCustom: Boolean = false): Int

    fun deleteCustomCover(mangaId: Long)

    fun deleteAll()
}
