package ephyra.presentation.core.util

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource
import java.time.Instant

/**
 * Returns a relative date string (e.g., "2 hours ago").
 */
@Composable
@ReadOnlyComposable
fun relativeDateText(date: Long): String {
    if (date <= 0L) {
        return stringResource(MR.strings.not_applicable)
    }

    val context = LocalContext.current
    return DateUtils.getRelativeTimeSpanString(
        date,
        Instant.now().toEpochMilli(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}
