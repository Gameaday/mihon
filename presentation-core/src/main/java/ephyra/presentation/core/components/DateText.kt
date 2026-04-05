package ephyra.presentation.core.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ephyra.core.common.util.lang.toRelativeString
import ephyra.domain.ui.UiPreferences
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun relativeDateText(
    dateEpochMillis: Long,
): String {
    return relativeDateText(
        localDate = LocalDate.ofInstant(
            Instant.ofEpochMilli(dateEpochMillis),
            ZoneId.systemDefault(),
        )
            .takeIf { dateEpochMillis != 0L },
    )
}

@Composable
fun relativeDateText(
    localDate: LocalDate?,
): String {
    val context = LocalContext.current

    val preferences = ephyra.presentation.core.util.LocalUiPreferences.current

    val relativeTime = remember { runBlocking { preferences.relativeTime().get() } }

    val dateFormat = remember { UiPreferences.dateFormat(runBlocking { preferences.dateFormat().get() }) }

    return localDate?.toRelativeString(
        context = context,
        relative = relativeTime,
        dateFormat = dateFormat,
    )
        ?: stringResource(MR.strings.not_applicable)
}
