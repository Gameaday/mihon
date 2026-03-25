package ephyra.domain.download.service

import ephyra.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(REMOVE_EXCLUDE_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapterCategoriesExclude() =
        preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    fun parallelSourceLimit() = preferenceStore.getInt("download_parallel_source_limit", 5)

    fun parallelPageLimit() = preferenceStore.getInt("download_parallel_page_limit", 5)

    fun autoSyncToJellyfin() = preferenceStore.getBoolean("auto_sync_to_jellyfin", false)

    fun jellyfinScanAfterSync() = preferenceStore.getBoolean("jellyfin_scan_after_sync", false)

    /**
     * Which chapters to include when syncing to Jellyfin.
     * 0 = All chapters (skip existing), 1 = Read chapters only, 2 = Downloaded chapters only.
     */
    fun jellyfinUploadScope() = preferenceStore.getInt("jellyfin_upload_scope", 0)

    /**
     * URI of the Jellyfin library folder (e.g. a network share or NAS mount).
     * When set, completed Jellyfin-named downloads are copied to this folder
     * so the Jellyfin server can discover them via library scan.
     * Empty string = not set (downloads stay in the default location only).
     */
    fun jellyfinLibraryFolder() = preferenceStore.getString("jellyfin_library_folder", "")

    /**
     * Perceptual hashes (dHash, 16-char hex) of known scanlation group intro/outro/credits pages.
     * After all pages in a chapter are downloaded, any page whose dHash is within
     * [BLOCKED_PAGE_DHASH_THRESHOLD] Hamming distance of an entry in this set is deleted
     * before archiving, so it never appears in the final chapter on disk or in the reader.
     *
     * dHash is robust to JPEG recompression, minor rescaling, and color shifts —
     * a single entry silences all visual variants of the same credit page.
     */
    fun blockedPageHashes() = preferenceStore.getStringSet("blocked_page_hashes", emptySet())

    companion object {
        /**
         * Maximum Hamming distance (inclusive) between two dHash values for a match.
         * 0 = exact perceptual match only; 10 = tolerate minor visual differences.
         */
        const val BLOCKED_PAGE_DHASH_THRESHOLD = 10

        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
