package ephyra.feature.reader.setting

import ephyra.presentation.core.R
import ephyra.domain.reader.model.ReadingMode
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.pager.L2RPagerViewer
import ephyra.feature.reader.viewer.pager.R2LPagerViewer
import ephyra.feature.reader.viewer.pager.VerticalPagerViewer
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import org.koin.android.ext.android.get

val ReadingMode.iconRes: Int
    get() = when (this) {
        ReadingMode.DEFAULT -> R.drawable.ic_reader_default_24dp
        ReadingMode.LEFT_TO_RIGHT -> R.drawable.ic_reader_ltr_24dp
        ReadingMode.RIGHT_TO_LEFT -> R.drawable.ic_reader_rtl_24dp
        ReadingMode.VERTICAL -> R.drawable.ic_reader_vertical_24dp
        ReadingMode.WEBTOON -> R.drawable.ic_reader_webtoon_24dp
        ReadingMode.CONTINUOUS_VERTICAL -> R.drawable.ic_reader_continuous_vertical_24dp
    }

fun ReadingMode.toViewer(activity: ReaderActivity): Viewer {
    return when (this) {
        ReadingMode.LEFT_TO_RIGHT -> L2RPagerViewer(activity, activity.get(), activity.get())
        ReadingMode.RIGHT_TO_LEFT -> R2LPagerViewer(activity, activity.get(), activity.get())
        ReadingMode.VERTICAL -> VerticalPagerViewer(activity, activity.get(), activity.get())
        ReadingMode.WEBTOON -> WebtoonViewer(activity, activity.get(), activity.get())
        ReadingMode.CONTINUOUS_VERTICAL -> WebtoonViewer(activity, activity.get(), activity.get(), isContinuous = false)
        ReadingMode.DEFAULT -> throw IllegalStateException("Preference value must be resolved")
    }
}
