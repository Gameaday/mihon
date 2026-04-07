package ephyra.feature.settings.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val data = when {
            !uri.encodedQuery.isNullOrBlank() -> uri.encodedQuery
            !uri.encodedFragment.isNullOrBlank() -> uri.encodedFragment
            else -> null
        }
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.associate {
                val parts = it.split("=", limit = 2).map<String, String>(Uri::decode)
                parts[0] to parts.getOrNull(1)
            }
            .orEmpty()

        lifecycleScope.launch {
            when (uri.host) {
                "anilist-auth" -> handleAniList(data["access_token"])
                "bangumi-auth" -> handleBangumi(data["code"])
                "myanimelist-auth" -> handleMyAnimeList(data["code"])
                "shikimori-auth" -> handleShikimori(data["code"])
            }
            returnToSettings()
        }
    }

    private suspend fun handleAniList(accessToken: String?) {
        val tracker = trackerManager.get(2L) ?: return
        if (accessToken != null) {
            tracker.login(accessToken, "")
        } else {
            tracker.logout()
        }
    }

    private suspend fun handleBangumi(code: String?) {
        val tracker = trackerManager.get(5L) ?: return
        if (code != null) {
            tracker.login(code, "")
        } else {
            tracker.logout()
        }
    }

    private suspend fun handleMyAnimeList(code: String?) {
        val tracker = trackerManager.get(1L) ?: return
        if (code != null) {
            tracker.login(code, "")
        } else {
            tracker.logout()
        }
    }

    private suspend fun handleShikimori(code: String?) {
        val tracker = trackerManager.get(4L) ?: return
        if (code != null) {
            tracker.login(code, "")
        } else {
            tracker.logout()
        }
    }
}
