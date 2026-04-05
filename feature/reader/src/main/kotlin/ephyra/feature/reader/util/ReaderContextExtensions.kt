package ephyra.feature.reader.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.domain.ui.model.ThemeMode
import ephyra.presentation.core.R
import ephyra.presentation.core.util.system.isNightMode
import kotlinx.coroutines.runBlocking

/**
 * Creates night mode Context depending on reader theme/background
 */
fun Context.createReaderThemeContext(
    preferences: UiPreferences,
    readerPreferences: ReaderPreferences,
): Context {
    val themeMode = runBlocking { preferences.themeMode().get() }
    val isDarkBackground = when (runBlocking { readerPreferences.readerTheme().get() }) {
        1, 2 -> true // Black, Gray
        3 -> when (themeMode) { // Automatic bg uses activity background by default
            ThemeMode.SYSTEM -> applicationContext.isNightMode()
            else -> themeMode == ThemeMode.DARK
        }

        else -> false // White
    }
    val expected = if (isDarkBackground) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != expected) {
        val overrideConf = Configuration()
        overrideConf.setTo(resources.configuration)
        overrideConf.uiMode = (overrideConf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or expected

        val wrappedContext = ContextThemeWrapper(this, R.style.Theme_Tachiyomi)
        wrappedContext.applyOverrideConfiguration(overrideConf)
        return wrappedContext
    }
    return this
}
