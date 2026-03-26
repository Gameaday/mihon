package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.domain.reader.service.ReaderPreferences

class SettingsReaderScreenModel(
    val readerPreferences: ReaderPreferences,
) : ScreenModel
