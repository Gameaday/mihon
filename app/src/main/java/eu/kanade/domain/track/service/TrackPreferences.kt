package eu.kanade.domain.track.service

import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_username_${tracker.id}"),
        "",
    )

    fun trackPassword(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_password_${tracker.id}"),
        "",
    )

    fun trackAuthExpired(tracker: Tracker) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${tracker.id}"),
        false,
    )

    fun setCredentials(tracker: Tracker, username: String, password: String) {
        trackUsername(tracker).set(username)
        trackPassword(tracker).set(password)
        trackAuthExpired(tracker).set(false)
    }

    fun trackToken(tracker: Tracker) = preferenceStore.getString(Preference.privateKey("track_token_${tracker.id}"), "")

    /**
     * Ordered list of preferred authority trackers for matching.
     *
     * The matching engine walks this list in order, trying each tracker.
     * A tracker is skipped if it is not available (not logged in and does not support
     * public search) or does not support the requested content type.
     *
     * Default order: MangaUpdates (public, no login), AniList, MyAnimeList.
     */
    fun authorityTrackerOrder() = preferenceStore.getLongArray(
        "pref_authority_tracker_order",
        DEFAULT_AUTHORITY_ORDER,
    )

    /**
     * Legacy single-preference accessor kept for backward compatibility.
     * @see authorityTrackerOrder
     */
    fun preferredAuthorityTracker() = preferenceStore.getLong(
        "pref_preferred_authority_tracker",
        AUTHORITY_TRACKER_AUTO,
    )

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun autoUpdateTrackOnMarkRead() = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )

    /**
     * Jellyfin user ID, used for API calls that require a user context.
     * Set during Jellyfin tracker setup when connecting to a server.
     */
    fun jellyfinUserId() = preferenceStore.getString(
        Preference.privateKey("jellyfin_user_id"),
        "",
    )

    /**
     * Jellyfin server URL. Stored separately from tracker credentials so it can
     * be updated independently when the server moves to a new address (dynamic IP,
     * domain change). All API calls resolve the server URL from this preference
     * rather than extracting it from tracking URLs.
     */
    fun jellyfinServerUrl() = preferenceStore.getString(
        Preference.privateKey("jellyfin_server_url"),
        "",
    )

    /**
     * Jellyfin server ID (stable across address changes). Used to verify that
     * a new server URL points to the same Jellyfin instance when migrating.
     */
    fun jellyfinServerId() = preferenceStore.getString(
        Preference.privateKey("jellyfin_server_id"),
        "",
    )

    /**
     * Jellyfin display username — the human-readable name of the authenticated user.
     * Shown in the settings UI and used for diagnostics.
     */
    fun jellyfinUsername() = preferenceStore.getString(
        Preference.privateKey("jellyfin_username"),
        "",
    )

    /**
     * Jellyfin server name — the display name of the connected Jellyfin server.
     * Stored during login from the SystemInfo response. Shown in the settings UI
     * alongside the server URL for user clarity.
     */
    fun jellyfinServerName() = preferenceStore.getString(
        Preference.privateKey("jellyfin_server_name"),
        "",
    )

    /**
     * Whether the authenticated Jellyfin user has administrator privileges.
     * Cached at login time from the user's Policy.IsAdministrator field.
     *
     * Admin users can trigger library scans (POST /Library/Refresh) — non-admin
     * users cannot. This flag gates the library scan step in chapter sync and
     * allows the UI to show appropriate messages.
     */
    fun jellyfinIsAdmin() = preferenceStore.getBoolean(
        Preference.privateKey("jellyfin_is_admin"),
        false,
    )

    companion object {
        /** Sentinel value: let the system pick the best available tracker automatically. */
        const val AUTHORITY_TRACKER_AUTO = 0L

        /** Default authority order: MangaUpdates (7, public) → AniList (2) → MAL (1) → Jellyfin (10). */
        val DEFAULT_AUTHORITY_ORDER = listOf(7L, 2L, 1L, 10L)
    }
}
