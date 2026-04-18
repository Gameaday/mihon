package ephyra.data.track.kavita

import ephyra.core.data.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

class KavitaInterceptor(private val kavita: Kavita) : Interceptor {

    /**
     * Guards [Kavita.loadOAuth] so that concurrent OkHttp requests on different threads
     * cannot trigger multiple simultaneous token loads.  The double-checked pattern inside
     * [runBlocking] + [withLock] ensures the load runs exactly once even under concurrency.
     *
     * [runBlocking] is unavoidable here because OkHttp's [Interceptor.intercept] is a
     * synchronous API; there is no way to suspend.  The load only ever executes once per
     * app session (or after a token refresh), so the thread-park cost is a one-time hit.
     */
    private val oauthMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (kavita.authentications == null) {
            runBlocking {
                oauthMutex.withLock {
                    if (kavita.authentications == null) {
                        kavita.loadOAuth()
                    }
                }
            }
        }
        val jwtToken = kavita.authentications?.getToken(
            kavita.api.getApiFromUrl(originalRequest.url.toString()),
        )

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $jwtToken")
            .header("User-Agent", "Ephyra v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }
}
