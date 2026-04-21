package ephyra.app.startup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StartupTracker].
 *
 * Each test resets the singleton via [StartupTracker.resetForTest] so that
 * completed-phase state from one test cannot bleed into the next.
 *
 * Tests run sequentially (no CONCURRENT annotation) because they share the
 * [StartupTracker] global singleton.
 */
class StartupTrackerTest {

    @AfterEach
    fun resetTracker() {
        StartupTracker.resetForTest()
    }

    // ── complete() / isComplete() ──────────────────────────────────────────────

    @Test
    fun `complete marks phase as completed`() {
        assertFalse(StartupTracker.isComplete(StartupTracker.Phase.APP_CREATED))

        StartupTracker.complete(StartupTracker.Phase.APP_CREATED)

        assertTrue(StartupTracker.isComplete(StartupTracker.Phase.APP_CREATED))
    }

    @Test
    fun `complete is idempotent — duplicate calls do not duplicate entries`() {
        StartupTracker.complete(StartupTracker.Phase.KOIN_INITIALIZED)
        val firstSnapshot = StartupTracker.completedPhases.toList()

        // Call complete() a second time for the same phase
        StartupTracker.complete(StartupTracker.Phase.KOIN_INITIALIZED)
        val secondSnapshot = StartupTracker.completedPhases.toList()

        assertEquals(1, firstSnapshot.size)
        assertEquals(firstSnapshot.size, secondSnapshot.size) {
            "Duplicate complete() calls must not add extra entries"
        }
        // The timestamp of the recorded entry must not change
        assertEquals(firstSnapshot[0].timestampMs, secondSnapshot[0].timestampMs) {
            "Second complete() must not overwrite the first-recorded timestamp"
        }
    }

    @Test
    fun `isComplete returns false for phases that have not been completed`() {
        StartupTracker.Phase.entries.forEach { phase ->
            assertFalse(StartupTracker.isComplete(phase)) {
                "Phase ${phase.displayName} should not be complete before any complete() calls"
            }
        }
    }

    @Test
    fun `completedPhases is empty before any phase completes`() {
        assertTrue(StartupTracker.completedPhases.isEmpty())
    }

    @Test
    fun `completedPhases grows as phases are completed`() {
        assertTrue(StartupTracker.completedPhases.isEmpty())

        StartupTracker.complete(StartupTracker.Phase.APP_CREATED)
        assertEquals(1, StartupTracker.completedPhases.size)

        StartupTracker.complete(StartupTracker.Phase.KOIN_INITIALIZED)
        assertEquals(2, StartupTracker.completedPhases.size)
    }

    @Test
    fun `completedPhases returns entries sorted by timestamp`() {
        // Complete phases out of their declared enum order
        StartupTracker.complete(StartupTracker.Phase.KOIN_INITIALIZED)
        Thread.sleep(2) // guarantee different wall-clock timestamps
        StartupTracker.complete(StartupTracker.Phase.APP_CREATED)

        val phases = StartupTracker.completedPhases
        assertTrue(phases[0].timestampMs <= phases[1].timestampMs) {
            "completedPhases must be sorted ascending by completion time"
        }
    }

    @Test
    fun `PhaseEntry stores the completing phase`() {
        StartupTracker.complete(StartupTracker.Phase.ACTIVITY_CREATED)
        val entry = StartupTracker.completedPhases.first()

        assertEquals(StartupTracker.Phase.ACTIVITY_CREATED, entry.phase)
    }

    @Test
    fun `PhaseEntry timestamp is at or after processStartMs`() {
        StartupTracker.complete(StartupTracker.Phase.COMPOSE_STARTED)
        val entry = StartupTracker.completedPhases.first()

        assertTrue(entry.timestampMs >= StartupTracker.processStartMs) {
            "Phase completion timestamp must not precede the process start time"
        }
    }

    // ── recordError() / lastError ─────────────────────────────────────────────

    @Test
    fun `lastError is null before any error is recorded`() {
        assertNull(StartupTracker.lastError)
    }

    @Test
    fun `recordError sets lastError`() {
        val error = RuntimeException("test error")
        StartupTracker.recordError(StartupTracker.Phase.MIGRATOR_STARTED, error)

        assertNotNull(StartupTracker.lastError)
        assertEquals("test error", StartupTracker.lastError!!.message)
    }

    @Test
    fun `recordError replaces a previously recorded error`() {
        val first = RuntimeException("first")
        val second = IllegalStateException("second")

        StartupTracker.recordError(StartupTracker.Phase.APP_CREATED, first)
        StartupTracker.recordError(StartupTracker.Phase.KOIN_INITIALIZED, second)

        assertEquals("second", StartupTracker.lastError!!.message) {
            "recordError must overwrite the previously stored error"
        }
    }

    @Test
    fun `recordError does not mark the phase as complete`() {
        StartupTracker.recordError(StartupTracker.Phase.KOIN_INITIALIZED, RuntimeException())

        assertFalse(StartupTracker.isComplete(StartupTracker.Phase.KOIN_INITIALIZED)) {
            "recordError must not implicitly complete the phase"
        }
    }

    // ── elapsedMs() ───────────────────────────────────────────────────────────

    @Test
    fun `elapsedMs returns a non-negative value`() {
        assertTrue(StartupTracker.elapsedMs() >= 0)
    }

    @Test
    fun `elapsedMs grows over time`() {
        val before = StartupTracker.elapsedMs()
        Thread.sleep(5)
        val after = StartupTracker.elapsedMs()

        assertTrue(after >= before) {
            "elapsedMs must be monotonically non-decreasing"
        }
    }

    // ── Phase enum invariants ─────────────────────────────────────────────────

    @Test
    fun `all phases have a non-blank displayName`() {
        StartupTracker.Phase.entries.forEach { phase ->
            assertFalse(phase.displayName.isBlank()) {
                "Phase $phase must have a non-blank displayName"
            }
        }
    }

    @Test
    fun `all phase timeoutMs values are positive`() {
        StartupTracker.Phase.entries.forEach { phase ->
            assertTrue(phase.timeoutMs > 0) {
                "Phase ${phase.displayName} has a non-positive timeoutMs (${phase.timeoutMs}). " +
                    "A timeout of zero or less would mark every phase as immediately overdue."
            }
        }
    }

    @Test
    fun `phase timeoutMs values are in strictly ascending order`() {
        // The phases are declared in expected completion order; their timeout budgets
        // must also be increasing so that a later phase cannot expire before an earlier one.
        val timeouts = StartupTracker.Phase.entries.map { it.timeoutMs }
        for (i in 1 until timeouts.size) {
            assertTrue(timeouts[i] > timeouts[i - 1]) {
                "Phase timeouts must be strictly ascending. " +
                    "${StartupTracker.Phase.entries[i].displayName} (${timeouts[i]}ms) is not " +
                    "greater than ${StartupTracker.Phase.entries[i - 1].displayName} (${timeouts[i - 1]}ms)."
            }
        }
    }

    @Test
    fun `HOME_SCREEN_LOADED is the last phase`() {
        assertEquals(
            StartupTracker.Phase.HOME_SCREEN_LOADED,
            StartupTracker.Phase.entries.last(),
        ) {
            "HOME_SCREEN_LOADED must remain the final startup phase so the diagnostic overlay " +
                "knows when the app is fully ready."
        }
    }

    @Test
    fun `APP_CREATED is the first phase`() {
        assertEquals(
            StartupTracker.Phase.APP_CREATED,
            StartupTracker.Phase.entries.first(),
        ) {
            "APP_CREATED must remain the first startup phase."
        }
    }

    // ── resetForTest() ────────────────────────────────────────────────────────

    @Test
    fun `resetForTest clears completed phases`() {
        StartupTracker.complete(StartupTracker.Phase.APP_CREATED)
        assertFalse(StartupTracker.completedPhases.isEmpty())

        StartupTracker.resetForTest()

        assertTrue(StartupTracker.completedPhases.isEmpty()) {
            "resetForTest must clear all completed phases"
        }
    }

    @Test
    fun `resetForTest clears lastError`() {
        StartupTracker.recordError(StartupTracker.Phase.APP_CREATED, RuntimeException())
        assertNotNull(StartupTracker.lastError)

        StartupTracker.resetForTest()

        assertNull(StartupTracker.lastError) {
            "resetForTest must clear lastError"
        }
    }
}
