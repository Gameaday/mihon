package ephyra.app.data.track.jellyfin

import ephyra.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds Jellyfin authentication headers.
 *
 * Adds:
 * - `X-Emby-Token` header with the access token (from AuthenticateByName)
 * - `User-Agent` identifying Ephyra
 */
class JellyfinInterceptor(private val jellyfin: Jellyfin) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = jellyfin.getPassword() // stores access token from AuthenticateByName

        val authRequest = originalRequest.newBuilder()
            .header("X-Emby-Token", token)
            .header(
                "User-Agent",
                "Ephyra v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
            )
            .build()

        return chain.proceed(authRequest)
    }
}
