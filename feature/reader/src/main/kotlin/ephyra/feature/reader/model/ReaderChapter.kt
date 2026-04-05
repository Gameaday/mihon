package ephyra.feature.reader.model

import ephyra.core.common.util.system.logcat
import ephyra.data.database.models.Chapter
import ephyra.domain.chapter.model.toDbChapter
import ephyra.feature.reader.loader.PageLoader
import kotlinx.coroutines.flow.MutableStateFlow

data class ReaderChapter(val chapter: Chapter) {

    val stateFlow = MutableStateFlow<State>(State.Wait)
    var state: State
        get() = stateFlow.value
        set(value) {
            stateFlow.value = value
        }

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    var pageLoader: PageLoader? = null

    var requestedPage: Int = 0

    private var references = 0

    constructor(chapter: ephyra.domain.chapter.model.Chapter) : this(chapter.toDbChapter())

    fun ref() {
        references++
    }

    fun unref() {
        references--
        if (references == 0) {
            if (pageLoader != null) {
                logcat { "Recycling chapter ${chapter.name}" }
            }
            pageLoader?.recycle()
            pageLoader = null
            // Recycle any merged bitmaps held by pages before dropping the page list,
            // so large native allocations are freed immediately.
            (state as? State.Loaded)?.pages?.forEach { page ->
                page.recycleMergedBitmap()
            }
            state = State.Wait
        }
    }

    sealed interface State {
        data object Wait : State
        data object Loading : State
        data class Error(val error: Throwable) : State
        data class Loaded(val pages: List<ReaderPage>) : State
    }
}
