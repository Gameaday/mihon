package ephyra.domain.chapter.service

import ephyra.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Response
import java.io.File

interface ChapterCache {

    fun getPageListFromCache(chapter: Chapter): List<Page>

    fun putPageListToCache(chapter: Chapter, pages: List<Page>)

    fun isImageInCache(imageUrl: String): Boolean

    suspend fun fetchAndCacheImage(imageUrl: String, fetchImage: suspend () -> Response)

    fun getImageFile(imageUrl: String): File?
}
