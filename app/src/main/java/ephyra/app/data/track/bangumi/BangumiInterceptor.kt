package ephyra.app.data.track.bangumi

import ephyra.app.BuildConfig
import ephyra.app.data.track.bangumi.dto.BGMOAuth
import ephyra.app.data.track.bangumi.dto.isExpired
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class BangumiInterceptor(
    private val bangumi: Bangumi,
    private val json: Json,
) : Interceptor {


    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: BGMOAuth? = bangumi.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currAuth: BGMOAuth = oauth ?: throw Exception("Not authenticated with Bangumi")

        if (currAuth.isExpired()) {
            val refreshToken = currAuth.refreshToken
                ?: throw IOException("No refresh token available")
            val response = chain.proceed(BangumiApi.refreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                currAuth = json.decodeFromString<BGMOAuth>(response.body.string())
                newAuth(currAuth)
            } else {
                response.close()
            }
        }

        return originalRequest.newBuilder()
            .header(
                "User-Agent",
                "Gameaday/Ephyra/v${BuildConfig.VERSION_NAME} (Android) (http://github.com/Gameaday/Ephyra)",
            )
            .apply {
                addHeader("Authorization", "Bearer ${currAuth.accessToken}")
            }
            .build()
            .let(chain::proceed)
    }

    fun newAuth(oauth: BGMOAuth?) {
        this.oauth = if (oauth == null) {
            null
        } else {
            BGMOAuth(
                oauth.accessToken,
                oauth.tokenType,
                System.currentTimeMillis() / 1000,
                oauth.expiresIn,
                oauth.refreshToken,
                this.oauth?.userId,
            )
        }

        bangumi.saveToken(oauth)
    }
}
