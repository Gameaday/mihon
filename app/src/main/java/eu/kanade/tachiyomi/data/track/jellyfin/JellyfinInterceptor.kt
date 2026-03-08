package eu.kanade.tachiyomi.data.track.jellyfin

import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds Jellyfin authentication headers.
 *
 * Adds:
 * - `X-Emby-Token` header with the API key or access token
 * - `User-Agent` identifying Mihon
 */
class JellyfinInterceptor(private val jellyfin: Jellyfin) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = jellyfin.getPassword() // stores API key / access token

        val authRequest = originalRequest.newBuilder()
            .header("X-Emby-Token", token)
            .header(
                "User-Agent",
                "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
            )
            .build()

        return chain.proceed(authRequest)
    }
}
