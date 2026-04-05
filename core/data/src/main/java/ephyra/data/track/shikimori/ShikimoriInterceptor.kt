package ephyra.data.track.shikimori

import ephyra.core.data.BuildConfig
import ephyra.data.track.shikimori.dto.SMOAuth
import ephyra.data.track.shikimori.dto.isExpired
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ShikimoriInterceptor(
    private val shikimori: Shikimori,
    private val json: Json,
) : Interceptor {

    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: SMOAuth? = shikimori.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val currAuth = oauth ?: throw Exception("Not authenticated with Shikimori")

        // Refresh access token if expired.
        if (currAuth.isExpired()) {
            val refreshToken = currAuth.refreshToken
                ?: throw IOException("No refresh token available")
            val response = chain.proceed(ShikimoriApi.refreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                newAuth(json.decodeFromString<SMOAuth>(response.body.string()))
            } else {
                response.close()
            }
        }
        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${(oauth ?: currAuth).accessToken}")
            .header("User-Agent", "Ephyra v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: SMOAuth?) {
        this.oauth = oauth
        shikimori.saveToken(oauth)
    }
}
