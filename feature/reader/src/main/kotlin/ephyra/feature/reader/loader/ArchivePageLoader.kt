package ephyra.feature.reader.loader

import ephyra.core.archive.ArchiveReader
import ephyra.core.common.util.lang.compareToCaseInsensitiveNaturalOrder
import ephyra.core.common.util.system.ImageUtil
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page

/**
 * Loader used to load a chapter from an archive file.
 */
internal class ArchivePageLoader(private val reader: ArchiveReader) : PageLoader() {
    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        return reader.useEntries { entries ->
            entries
                .filter {
                    it.isFile &&
                        ImageUtil.isImage(it.name) {
                            requireNotNull(reader.getInputStream(it.name)) {
                                "Entry '${it.name}' not found in archive during image check"
                            }
                        }
                }
                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                .mapIndexed { i, entry ->
                    ReaderPage(i).apply {
                        stream =
                            {
                                requireNotNull(reader.getInputStream(entry.name)) {
                                    "Entry '${entry.name}' not found in archive"
                                }
                            }
                        status = Page.State.Ready
                    }
                }
                .toList()
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
