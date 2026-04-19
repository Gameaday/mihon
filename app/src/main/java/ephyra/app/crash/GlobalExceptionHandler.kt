package ephyra.app.crash

import android.content.Context
import android.content.Intent
import ephyra.core.common.util.system.logcat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import logcat.LogPriority
import org.koin.core.context.GlobalContext

class GlobalExceptionHandler private constructor(
    private val applicationContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val activityToBeLaunched: Class<*>,
) : Thread.UncaughtExceptionHandler {

    object ThrowableSerializer : KSerializer<Throwable> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("Throwable", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Throwable =
            Throwable(message = decoder.decodeString())

        override fun serialize(encoder: Encoder, value: Throwable) =
            encoder.encodeString(value.stackTraceToString())
    }

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        logcat(priority = LogPriority.ERROR, throwable = exception)
        // Only launch CrashActivity when Koin is ready.  CrashActivity extends BaseActivity
        // which eagerly resolves Koin delegates (SecureActivityDelegate, ThemingDelegate) via
        // KoinJavaComponent.get().  Launching it before startKoin() completes would trigger a
        // second crash that swallows the original exception and shows no UI.  When Koin is not
        // yet available we fall through to the default handler so the process terminates cleanly.
        if (GlobalContext.getOrNull() != null) {
            launchActivity(applicationContext, activityToBeLaunched, exception)
        }
        defaultHandler?.uncaughtException(thread, exception)
    }

    private fun launchActivity(
        applicationContext: Context,
        activity: Class<*>,
        exception: Throwable,
    ) {
        val intent = Intent(applicationContext, activity).apply {
            putExtra(INTENT_EXTRA, Json.encodeToString(ThrowableSerializer, exception))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        applicationContext.startActivity(intent)
    }

    companion object {
        private const val INTENT_EXTRA = "Throwable"

        fun initialize(
            applicationContext: Context,
            activityToBeLaunched: Class<*>,
        ) {
            val handler = GlobalExceptionHandler(
                applicationContext,
                Thread.getDefaultUncaughtExceptionHandler(),
                activityToBeLaunched,
            )
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun getThrowableFromIntent(intent: Intent): Throwable? {
            return try {
                Json.decodeFromString(ThrowableSerializer, intent.getStringExtra(INTENT_EXTRA)!!)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Wasn't able to retrieve throwable from intent" }
                null
            }
        }
    }
}
