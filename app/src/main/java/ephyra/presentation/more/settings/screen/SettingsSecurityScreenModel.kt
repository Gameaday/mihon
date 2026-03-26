package ephyra.presentation.more.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.domain.security.service.PrivacyPreferences
import ephyra.domain.security.service.SecurityPreferences

class SettingsSecurityScreenModel(
    val securityPreferences: SecurityPreferences,
    val privacyPreferences: PrivacyPreferences,
) : ScreenModel
