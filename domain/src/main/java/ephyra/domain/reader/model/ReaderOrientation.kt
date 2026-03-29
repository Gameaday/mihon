package ephyra.domain.reader.model

import android.content.pm.ActivityInfo
import dev.icerock.moko.resources.StringResource
import ephyra.i18n.MR

enum class ReaderOrientation(
    val flag: Int,
    val stringRes: StringResource,
    val flagValue: Int,
) {
    DEFAULT(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        MR.strings.label_default,
        0x00000000,
    ),
    FREE(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        MR.strings.rotation_free,
        0x00000008,
    ),
    PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        MR.strings.rotation_portrait,
        0x00000010,
    ),
    LANDSCAPE(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        MR.strings.rotation_landscape,
        0x00000018,
    ),
    LOCKED_PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        MR.strings.rotation_force_portrait,
        0x00000020,
    ),
    LOCKED_LANDSCAPE(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        MR.strings.rotation_force_landscape,
        0x00000028,
    ),
    REVERSE_PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        MR.strings.rotation_reverse_portrait,
        0x00000030,
    ),
    ;

    companion object {
        const val MASK = 0x00000038

        private val flagMap = entries.associateBy { it.flagValue }

        fun fromPreference(preference: Int?): ReaderOrientation = flagMap[preference] ?: DEFAULT
    }
}
