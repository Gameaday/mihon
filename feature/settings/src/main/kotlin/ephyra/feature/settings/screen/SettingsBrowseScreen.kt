package ephyra.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.i18n.stringResource
import ephyra.domain.source.service.SourcePreferences
import ephyra.feature.settings.Preference
import ephyra.feature.settings.screen.browse.ExtensionReposScreen
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.AuthenticatorUtil.authenticate
import kotlinx.collections.immutable.persistentListOf

object SettingsBrowseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_discover

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<SettingsBrowseScreenModel>()
        val reposCount by screenModel.getExtensionRepoCount().collectAsState(0)

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = screenModel.sourcePreferences.hideInLibraryItems(),
                        title = stringResource(MR.strings.pref_hide_in_library_items),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.label_extension_repos),
                        subtitle = pluralStringResource(MR.plurals.num_repos, reposCount, reposCount),
                        onClick = {
                            navigator.push(ExtensionReposScreen())
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_nsfw_content),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = screenModel.sourcePreferences.showNsfwSource(),
                        title = stringResource(MR.strings.pref_show_nsfw_source),
                        subtitle = stringResource(MR.strings.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.stringResource(MR.strings.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.parental_controls_info)),
                ),
            ),
        )
    }
}
