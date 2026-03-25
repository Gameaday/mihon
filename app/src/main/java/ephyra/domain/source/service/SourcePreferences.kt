package ephyra.domain.source.service

import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.app.util.system.LocaleHelper
import ephyra.domain.migration.models.MigrationFlag
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.preference.getEnum
import ephyra.core.common.preference.getLongArray
import ephyra.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun sourceDisplayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun incognitoExtensions() = preferenceStore.getStringSet("incognito_extensions", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet("pinned_catalogues", emptySet())

    fun lastUsedSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    fun extensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    fun migrationSources() = preferenceStore.getLongArray("migration_sources", emptyList())

    fun migrationFlags() = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    fun migrationDeepSearchMode() = preferenceStore.getBoolean("migration_deep_search", false)

    fun migrationPrioritizeByChapters() = preferenceStore.getBoolean("migration_prioritize_by_chapters", false)

    fun migrationHideUnmatched() = preferenceStore.getBoolean("migration_hide_unmatched", false)

    fun migrationHideWithoutUpdates() = preferenceStore.getBoolean("migration_hide_without_updates", false)
}
