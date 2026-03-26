package ephyra.feature.reader.setting

import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
import ephyra.app.R
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.pager.L2RPagerViewer
import ephyra.feature.reader.viewer.pager.R2LPagerViewer
import ephyra.feature.reader.viewer.pager.VerticalPagerViewer
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import ephyra.i18n.MR
import org.koin.android.ext.android.get

enum class ReadingMode(
    val stringRes: StringResource,
    @DrawableRes val iconRes: Int,
    val flagValue: Int,
    val direction: Direction? = null,
    val type: ViewerType? = null,
) {
    DEFAULT(MR.strings.label_default, R.drawable.ic_reader_default_24dp, 0x00000000),
    LEFT_TO_RIGHT(
        MR.strings.left_to_right_viewer,
        R.drawable.ic_reader_ltr_24dp,
        0x00000001,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    RIGHT_TO_LEFT(
        MR.strings.right_to_left_viewer,
        R.drawable.ic_reader_rtl_24dp,
        0x00000002,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    VERTICAL(
        MR.strings.vertical_viewer,
        R.drawable.ic_reader_vertical_24dp,
        0x00000003,
        Direction.Vertical,
        ViewerType.Pager,
    ),
    WEBTOON(
        MR.strings.webtoon_viewer,
        R.drawable.ic_reader_webtoon_24dp,
        0x00000004,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    CONTINUOUS_VERTICAL(
        MR.strings.vertical_plus_viewer,
        R.drawable.ic_reader_continuous_vertical_24dp,
        0x00000005,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    ;

    companion object {
        const val MASK = 0x00000007

        private val flagMap = entries.associateBy { it.flagValue }

        fun fromPreference(preference: Int?): ReadingMode = flagMap[preference] ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode.type is ViewerType.Pager
        }

        fun toViewer(preference: Int?, activity: ReaderActivity): Viewer {
            return when (fromPreference(preference)) {
                LEFT_TO_RIGHT -> L2RPagerViewer(activity, activity.get(), activity.get())
                RIGHT_TO_LEFT -> R2LPagerViewer(activity, activity.get(), activity.get())
                VERTICAL -> VerticalPagerViewer(activity, activity.get(), activity.get())
                WEBTOON -> WebtoonViewer(activity, activity.get(), activity.get())
                CONTINUOUS_VERTICAL -> WebtoonViewer(activity, activity.get(), activity.get(), isContinuous = false)
                DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
            }
        }
    }

    sealed interface Direction {
        data object Horizontal : Direction
        data object Vertical : Direction
    }

    sealed interface ViewerType {
        data object Pager : ViewerType
        data object Webtoon : ViewerType
    }
}
