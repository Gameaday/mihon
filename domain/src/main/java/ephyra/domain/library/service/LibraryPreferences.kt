package ephyra.domain.library.service

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getEnum
import ephyra.core.common.util.system.ImageFormat
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.manga.model.Manga

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun displayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun sortingMode() = preferenceStore.getObjectFromString(
        "library_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    fun randomSortSeed() = preferenceStore.getInt("library_random_sort_seed", 0)

    fun portraitColumns() = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    fun lastUpdatedTimestamp() = preferenceStore.getLong(Preference.appStateKey("library_update_last_timestamp"), 0L)
    fun autoUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 0)

    fun autoUpdateDeviceRestrictions() = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )

    fun autoUpdateMangaRestrictions() = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            MANGA_HAS_UNREAD,
            MANGA_NON_COMPLETED,
            MANGA_NON_READ,
            MANGA_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun showContinueReadingButton() = preferenceStore.getBoolean(
        "display_continue_reading_button",
        false,
    )

    fun markDuplicateReadChapterAsRead() = preferenceStore.getStringSet("mark_duplicate_read_chapter_read", emptySet())

    // region Filter

    fun filterDownloaded() = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    fun filterUnread() = preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    fun filterStarted() = preferenceStore.getEnum(
        "pref_filter_library_started_v2",
        TriState.DISABLED,
    )

    fun filterBookmarked() = preferenceStore.getEnum(
        "pref_filter_library_bookmarked_v2",
        TriState.DISABLED,
    )

    fun filterCompleted() = preferenceStore.getEnum(
        "pref_filter_library_completed_v2",
        TriState.DISABLED,
    )

    fun filterIntervalCustom() = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    fun filterSourceHealthDead() = preferenceStore.getEnum(
        "pref_filter_library_source_health_dead",
        TriState.DISABLED,
    )

    fun filterContentTypeManga() = preferenceStore.getEnum(
        "pref_filter_library_content_type_manga",
        TriState.DISABLED,
    )

    fun filterTracking(id: Int) = preferenceStore.getEnum(
        "pref_filter_library_tracked_${id}_v2",
        TriState.DISABLED,
    )

    // endregion

    // region Badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun unreadBadge() = preferenceStore.getBoolean("display_unread_badge", true)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", false)

    fun newShowUpdatesCount() = preferenceStore.getBoolean("library_show_updates_count", true)
    fun newUpdatesCount() = preferenceStore.getInt(Preference.appStateKey("library_unseen_updates_count"), 0)

    // endregion

    // region Category

    fun defaultCategory() = preferenceStore.getInt(DEFAULT_CATEGORY_PREF_KEY, -1)

    fun lastUsedCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    fun updateCategories() = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_PREF_KEY, emptySet())

    fun updateCategoriesExclude() = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    // endregion

    // region Chapter

    fun filterChapterByRead() = preferenceStore.getLong(
        "default_chapter_filter_by_read",
        Manga.SHOW_ALL,
    )

    fun filterChapterByDownloaded() = preferenceStore.getLong(
        "default_chapter_filter_by_downloaded",
        Manga.SHOW_ALL,
    )

    fun filterChapterByBookmarked() = preferenceStore.getLong(
        "default_chapter_filter_by_bookmarked",
        Manga.SHOW_ALL,
    )

    // and upload date
    fun sortChapterBySourceOrNumber() = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    fun displayChapterByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_NAME,
    )

    fun sortChapterByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.unreadFilterRaw)
        filterChapterByDownloaded().set(manga.downloadedFilterRaw)
        filterChapterByBookmarked().set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    fun hideMissingChapters() = preferenceStore.getBoolean("pref_hide_missing_chapter_indicators", false)
    // endregion

    // region Swipe Actions

    fun swipeToStartAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_end_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    fun swipeToEndAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleRead,
    )

    fun updateMangaTitles() = preferenceStore.getBoolean("pref_update_library_manga_titles", false)

    fun disallowNonAsciiFilenames() = preferenceStore.getBoolean("disallow_non_ascii_filenames", false)

    /**
     * When enabled, downloads use Jellyfin-compatible naming conventions:
     * `Series Name/Series Name Ch. 001.cbz` instead of the default
     * `Source/Series Name/chapter_hash/` structure.
     *
     * This allows downloaded content to be directly served by a Jellyfin
     * media server with the Bookshelf plugin without manual renaming.
     */
    fun jellyfinCompatibleNaming() = preferenceStore.getBoolean("pref_jellyfin_compatible_naming", false)

    /**
     * When enabled, downloaded chapters are also exported/saved to a Jellyfin
     * library path, and read progress is synced back to the Jellyfin server.
     *
     * Requires the Jellyfin tracker to be configured with a valid server URL
     * and API key. When a series is matched to a Jellyfin library item, marking
     * chapters as read in the app will mark them as played on the server.
     */
    fun jellyfinSyncEnabled() = preferenceStore.getBoolean("pref_jellyfin_sync_enabled", false)

    /**
     * Preferred Jellyfin library ID for filtering search results.
     * When set, series search is scoped to this library. When empty, all libraries are searched.
     * This improves matching accuracy when the server has multiple libraries.
     */
    fun jellyfinLibraryId() = preferenceStore.getString("pref_jellyfin_library_id", "")

    // endregion

    // region Image Format

    /**
     * User preference for the lossless image format used when the app creates derived images
     * (cover saves/shares, tall-image splits, page merges, rotations, etc.).
     *
     * This does **not** affect untouched downloads: pages that arrive from the source in their
     * original format are always stored as-is.  The preference only applies when the app must
     * produce a new bitmap (e.g. splitting a tall image into parts).
     *
     * Having a single, app-wide setting ensures **format consistency** within a chapter —
     * every derived image uses the same container.
     */
    fun imageFormat() = preferenceStore.getEnum("pref_image_format", ImageFormat.WEBP)

    // endregion

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val MANGA_NON_COMPLETED = "manga_ongoing"
        const val MANGA_HAS_UNREAD = "manga_fully_read"
        const val MANGA_NON_READ = "manga_started"
        const val MANGA_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"

        const val MARK_DUPLICATE_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_CHAPTER_READ_EXISTING = "existing"

        const val DEFAULT_CATEGORY_PREF_KEY = "default_category"
        private const val LIBRARY_UPDATE_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"
        val categoryPreferenceKeys = setOf(
            DEFAULT_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
