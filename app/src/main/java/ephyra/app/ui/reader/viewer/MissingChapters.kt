package ephyra.app.ui.reader.viewer

import ephyra.app.data.database.models.toDomainChapter
import ephyra.app.ui.reader.model.ReaderChapter
import ephyra.domain.chapter.service.calculateChapterGap as domainCalculateChapterGap

fun calculateChapterGap(higherReaderChapter: ReaderChapter?, lowerReaderChapter: ReaderChapter?): Int {
    return domainCalculateChapterGap(
        higherReaderChapter?.chapter?.toDomainChapter(),
        lowerReaderChapter?.chapter?.toDomainChapter(),
    )
}
