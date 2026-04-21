package ephyra.app.crash

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Unit tests for [GlobalExceptionHandler].
 *
 * The tests focus on the parts of [GlobalExceptionHandler] that can be exercised on the
 * JVM without an Android runtime:
 *
 * - [GlobalExceptionHandler.ThrowableSerializer] — verifies that exceptions can be
 *   serialised to a JSON string and then reconstructed (round-trip).
 * - [GlobalExceptionHandler.getThrowableFromIntent] — verifies that the helper correctly
 *   parses the serialised exception from an [Intent] extra, and gracefully handles missing
 *   or malformed extras rather than crashing.
 *
 * The actual crash-activity launch path (`uncaughtException → startActivity`) is an
 * integration concern tested by instrumented tests; verifying it here would require a full
 * Android runtime.
 *
 * Tests run concurrently because they are stateless.
 */
@Execution(ExecutionMode.CONCURRENT)
class GlobalExceptionHandlerTest {

    // ── ThrowableSerializer ───────────────────────────────────────────────────

    /**
     * Verifies that [GlobalExceptionHandler.ThrowableSerializer] can serialise a
     * [Throwable] to a JSON string.  The result must be non-empty and must contain
     * the exception message so that it is useful in a bug report.
     */
    @Test
    fun `ThrowableSerializer serializes exception to non-empty JSON string`() {
        val exception = RuntimeException("Something went wrong during startup")
        val json = Json.encodeToString(GlobalExceptionHandler.ThrowableSerializer, exception)

        assertNotNull(json)
        assert(json.isNotBlank()) { "Serialized exception JSON must not be blank" }
        assert("Something went wrong during startup" in json) {
            "Serialized JSON must contain the exception message"
        }
    }

    /**
     * Verifies that [GlobalExceptionHandler.ThrowableSerializer] produces a
     * [Throwable] with the original stack-trace text as its message when deserialised.
     *
     * The deserialised [Throwable] is a plain wrapper whose [Throwable.message] equals
     * the stack-trace string, which is the contract expected by [GlobalExceptionHandler]
     * consumers (e.g. CrashScreen).
     */
    @Test
    fun `ThrowableSerializer round-trips exception message`() {
        val original = IllegalStateException("Koin failed to initialise")
        val json = Json.encodeToString(GlobalExceptionHandler.ThrowableSerializer, original)
        val decoded = Json.decodeFromString(GlobalExceptionHandler.ThrowableSerializer, json)

        assertNotNull(decoded)
        // The decoded message contains the original stack trace as a string, which
        // in turn contains the exception class and message.
        assertNotNull(decoded.message) { "Decoded throwable must have a message" }
        assert("IllegalStateException" in decoded.message!!) {
            "Decoded message must contain the original exception class name"
        }
        assert("Koin failed to initialise" in decoded.message!!) {
            "Decoded message must contain the original exception message text"
        }
    }

    /**
     * Verifies that a Throwable with a null message can be serialised and deserialised
     * without throwing.  Exceptions without a message are common (e.g. bare
     * `throw NullPointerException()`) and the serializer must handle them gracefully.
     */
    @Test
    fun `ThrowableSerializer handles exception with null message`() {
        val exception = NullPointerException() // message is null
        val json = Json.encodeToString(GlobalExceptionHandler.ThrowableSerializer, exception)
        val decoded = Json.decodeFromString(GlobalExceptionHandler.ThrowableSerializer, json)

        assertNotNull(decoded)
        // Decoded message is the stack trace string which may contain "null" as part
        // of the NPE representation; we only assert that decoding does not throw.
    }

    /**
     * Verifies that deeply-nested cause chains are serialised without stack overflow.
     * Some frameworks wrap exceptions multiple times and this must not cause a recursive
     * serialization failure.
     */
    @Test
    fun `ThrowableSerializer handles chained exceptions`() {
        val root = RuntimeException("root cause")
        val wrapped = IllegalStateException("middle layer", root)
        val outermost = Exception("outermost", wrapped)

        val json = Json.encodeToString(GlobalExceptionHandler.ThrowableSerializer, outermost)
        val decoded = Json.decodeFromString(GlobalExceptionHandler.ThrowableSerializer, json)

        assertNotNull(decoded)
        // The stack-trace string representation of the outermost exception includes
        // "Caused by:" lines for each nested exception.
        assert("root cause" in decoded.message!!) {
            "Serialized chain must include the root cause message"
        }
    }

    // ── getThrowableFromIntent ─────────────────────────────────────────────────

    /**
     * Verifies that [GlobalExceptionHandler.getThrowableFromIntent] returns a valid
     * [Throwable] when the intent carries a properly serialised exception.
     */
    @Test
    fun `getThrowableFromIntent returns Throwable when intent has valid extra`() {
        val original = RuntimeException("startup failure")
        val serialized = Json.encodeToString(GlobalExceptionHandler.ThrowableSerializer, original)

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns serialized

        val result = GlobalExceptionHandler.getThrowableFromIntent(intent)

        assertNotNull(result) { "Must return a Throwable when the intent has a valid extra" }
        assert("startup failure" in result!!.message!!) {
            "Decoded Throwable must contain the original exception message"
        }
    }

    /**
     * Verifies that [GlobalExceptionHandler.getThrowableFromIntent] returns `null`
     * (rather than throwing) when the intent does not contain the expected extra.
     *
     * This situation can occur when the activity is re-created from a saved state
     * that did not carry the crash extra (e.g. a back-stack restore).
     */
    @Test
    fun `getThrowableFromIntent returns null when intent extra is absent`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns null

        val result = GlobalExceptionHandler.getThrowableFromIntent(intent)

        assertNull(result) {
            "getThrowableFromIntent must return null for an intent with no crash extra, " +
                "not throw an exception"
        }
    }

    /**
     * Verifies that [GlobalExceptionHandler.getThrowableFromIntent] returns `null`
     * (rather than throwing) when the extra string is not valid JSON.
     *
     * Malformed extras can occur if the serialisation format changes between app versions
     * or if the intent is constructed incorrectly in tests.
     */
    @Test
    fun `getThrowableFromIntent returns null for malformed JSON in extra`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns "not valid json {{{"

        val result = GlobalExceptionHandler.getThrowableFromIntent(intent)

        assertNull(result) {
            "getThrowableFromIntent must return null for malformed JSON, not throw"
        }
    }

    /**
     * Verifies that [GlobalExceptionHandler.getThrowableFromIntent] returns `null`
     * for an empty string extra.  An empty string is not valid JSON and must be
     * handled without throwing.
     */
    @Test
    fun `getThrowableFromIntent returns null for empty string extra`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns ""

        val result = GlobalExceptionHandler.getThrowableFromIntent(intent)

        assertNull(result) {
            "getThrowableFromIntent must return null for an empty extra string"
        }
    }

    // ── ThrowableSerializer descriptor ────────────────────────────────────────

    /**
     * Verifies the serializer's [kotlinx.serialization.descriptors.SerialDescriptor]
     * has the expected serial name.  This is the name stored in polymorphic JSON and
     * must remain stable to avoid breaking crash-report parsing tools.
     */
    @Test
    fun `ThrowableSerializer has expected descriptor serial name`() {
        assertEquals("Throwable", GlobalExceptionHandler.ThrowableSerializer.descriptor.serialName)
    }
}
