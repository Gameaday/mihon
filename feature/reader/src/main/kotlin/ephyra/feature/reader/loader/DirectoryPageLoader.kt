package ephyra.feature.reader.loader

import com.hippo.unifile.UniFile
import ephyra.core.common.util.lang.compareToCaseInsensitiveNaturalOrder
import ephyra.core.common.util.system.ImageUtil
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page

/**
 * Loader used to load a chapter from a directory given on [file].
 */
internal class DirectoryPageLoader(val file: UniFile) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        return file.listFiles()
            ?.filter { !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() } }
            ?.sortedWith { f1, f2 -> f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty()) }
            ?.mapIndexed { i, file ->
                val streamFn = { file.openInputStream() }
                ReaderPage(i).apply {
                    stream = streamFn
                    status = Page.State.Ready
                }
            }
            .orEmpty()
    }

    // All pages are marked Ready immediately in getPages(), so there is no loading work to do
    // here. The guard ensures the loader has not been recycled before any stream lambda is used.
    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }
}
