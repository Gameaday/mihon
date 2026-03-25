package ephyra.app.ui.reader.loader

import eu.kanade.ephyra.source.model.Page
import ephyra.app.ui.reader.model.ReaderPage
import ephyra.core.archive.EpubReader

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        return reader.getImagesFromPages().mapIndexed { i, path ->
            ReaderPage(i).apply {
                stream = { requireNotNull(reader.getInputStream(path)) { "Entry '$path' not found in EPUB" } }
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
