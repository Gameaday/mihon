package ephyra.feature.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import ephyra.core.common.util.system.logcat
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.InsertPage
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.util.createReaderThemeContext
import ephyra.feature.reader.viewer.calculateChapterGap
import ephyra.feature.reader.widget.ViewPagerAdapter
import org.koin.android.ext.android.getKoin

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    var items: MutableList<Any> = mutableListOf()
        private set

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext = viewer.activity.createReaderThemeContext(
        viewer.activity.getKoin().get<UiPreferences>(),
        viewer.activity.getKoin().get<ReaderPreferences>(),
    )

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Forces chapter transition if there is missing chapters
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Add previous chapter pages and transition, excluding any hidden pages
        // (absorbed stubs or blocked pages) so they are not re-inserted on refresh.
        chapters.prevChapter?.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        var insertPageLastPage: InsertPage? = null

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            val pages = currPages.toMutableList()

            val lastPage = pages.last()

            // Insert preprocessed pages into current page list
            preprocessed.keys.sortedDescending()
                .forEach { key ->
                    if (lastPage.index == key) {
                        insertPageLastPage = preprocessed[key]
                    }
                    preprocessed[key]?.let { pages.add(key + 1, it) }
                }

            // Filter hidden pages (absorbed stubs + blocked pages) AFTER inserting
            // preprocessed InsertPages so that the position calculations (which use
            // original page indices as keys) stay correct. InsertPages are never hidden.
            newItems.addAll(pages.filter { !it.isHidden })
        }

        currentChapter = chapters.currChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (
                    nextHasMissingChapters ||
                    forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        // Exclude hidden pages from the next chapter as well.
        chapters.nextChapter?.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        // Resets double-page splits, else insert pages get misplaced
        items.filterIsInstance<InsertPage>().also { items.removeAll(it) }

        if (viewer is R2LPagerViewer) {
            newItems.reverse()
        }

        preprocessed = mutableMapOf()
        items = newItems
        notifyDataSetChanged()

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage)
        }
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return items.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        return when (val item = items[position]) {
            is ReaderPage -> PagerPageHolder(readerThemedContext, viewer, item)
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = items.indexOf(view.item)
            if (position != -1) {
                return position
            } else {
                logcat { "Position for ${view.item} not found" }
            }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        if (currentPage !is ReaderPage) return

        val currentIndex = items.indexOf(currentPage)

        // Put aside preprocessed pages for next chapter so they don't get removed when changing chapter
        if (currentPage.chapter.chapter.id != currentChapter?.chapter?.id) {
            preprocessed[newPage.index] = newPage
            return
        }

        val placeAtIndex = when (viewer) {
            is L2RPagerViewer,
            is VerticalPagerViewer,
            -> currentIndex + 1

            else -> currentIndex
        }

        // It will enter a endless cycle of insert pages
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && items[placeAtIndex - 1] is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (items[placeAtIndex] is InsertPage) {
            return
        }

        items.add(placeAtIndex, newPage)

        notifyDataSetChanged()
    }

    fun onPageAbsorb(page: ReaderPage) {
        page.isAbsorbed = true
        val index = items.indexOf(page)
        if (index != -1) {
            items.removeAt(index)
            notifyDataSetChanged()
        }
    }

    fun cleanupPageSplit() {
        val insertPages = items.filterIsInstance<InsertPage>()
        items.removeAll(insertPages)
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext(
            viewer.activity.getKoin().get<UiPreferences>(),
            viewer.activity.getKoin().get<ReaderPreferences>(),
        )
    }
}
