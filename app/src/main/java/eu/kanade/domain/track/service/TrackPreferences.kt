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

    companion object {
        /** Sentinel value: let the system pick the best available tracker automatically. */
        const val AUTHORITY_TRACKER_AUTO = 0L

        /** Default authority order: MangaUpdates (7, public) → AniList (2) → MAL (1) → Jellyfin (10). */
        val DEFAULT_AUTHORITY_ORDER = listOf(7L, 2L, 1L, 10L)
    }
}
