package ephyra.feature.reader.viewer.pager

import ephyra.feature.reader.ReaderActivity
import ephyra.app.data.download.DownloadManager
import ephyra.feature.reader.setting.ReaderPreferences

/**
 * Implementation of a left to right PagerViewer.
 */
class L2RPagerViewer(
    activity: ReaderActivity,
    downloadManager: DownloadManager,
    readerPreferences: ReaderPreferences,
) : PagerViewer(activity, downloadManager, readerPreferences) {
    /**
     * Creates a new left to right pager.
     */
    override fun createPager(): Pager {
        return Pager(activity)
    }
}

/**
 * Implementation of a right to left PagerViewer.
 */
class R2LPagerViewer(
    activity: ReaderActivity,
    downloadManager: DownloadManager,
    readerPreferences: ReaderPreferences,
) : PagerViewer(activity, downloadManager, readerPreferences) {
    /**
     * Creates a new right to left pager.
     */
    override fun createPager(): Pager {
        return Pager(activity)
    }

    /**
     * Moves to the next page. On a R2L pager the next page is the one at the left.
     */
    override fun moveToNext() {
        moveLeft()
    }

    /**
     * Moves to the previous page. On a R2L pager the previous page is the one at the right.
     */
    override fun moveToPrevious() {
        moveRight()
    }
}

/**
 * Implementation of a vertical (top to bottom) PagerViewer.
 */
class VerticalPagerViewer(
    activity: ReaderActivity,
    downloadManager: DownloadManager,
    readerPreferences: ReaderPreferences,
) : PagerViewer(activity, downloadManager, readerPreferences) {
    /**
     * Creates a new vertical pager.
     */
    override fun createPager(): Pager {
        return Pager(activity, isHorizontal = false)
    }
}
