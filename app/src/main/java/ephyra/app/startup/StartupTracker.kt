package ephyra.app.startup

import ephyra.core.common.util.system.logcat
import logcat.LogPriority
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe singleton that records each startup phase with a wall-clock timestamp.
 *
 * Phases are reported as [logcat] messages and stored so that the on-screen
 * [StartupDiagnosticOverlay] can display them when the app appears stuck.
 */
object StartupTracker {

    /**
     * Well-known phases of the startup sequence, in expected completion order.
     *
     * Each phase must be completed exactly once via [complete].  Any phase that is still
     * absent from [completedPhases] after the diagnostic timeout is highlighted as pending
     * (or blocked) in the overlay.
     *
     * [timeoutMs] is the maximum time (measured from process start) within which this phase
     * should complete.  Phases still pending past their budget are shown as OVERDUE in
     * [StartupDiagnosticOverlay] with an amber warning icon.
     */
    enum class Phase(val displayName: String, val timeoutMs: Long) {
        APP_CREATED("Application created", 2_000L),
        KOIN_INITIALIZED("Koin DI ready", 8_000L),
        MIGRATOR_STARTED("Migrator launched", 10_000L),
        WORKMANAGER_CONFIGURED("WorkManager configured", 12_000L),
        ACTIVITY_CREATED("Main activity created", 15_000L),
        COMPOSE_STARTED("Compose content initialized", 20_000L),
        MIGRATOR_COMPLETE("Migrations complete", 40_000L),
        NAVIGATOR_CREATED("Navigator ready", 45_000L),
        HOME_SCREEN_LOADED("App ready", 60_000L),
    }

    data class PhaseEntry(val phase: Phase, val timestampMs: Long)

    // ConcurrentHashMap makes complete() truly idempotent under concurrency:
    // putIfAbsent is atomic, eliminating the check-then-act race that a
    // CopyOnWriteArrayList had.
    private val _completedPhases = ConcurrentHashMap<Phase, PhaseEntry>()

    /** Immutable snapshot of all completed phases sorted by completion time. */
    val completedPhases: List<PhaseEntry>
        get() = _completedPhases.values.sortedBy { it.timestampMs }

    /** Wall-clock time at which the tracker was first loaded (proxy for process start). */
    val processStartMs: Long = System.currentTimeMillis()

    /** Last error captured during startup (if any). */
    @Volatile
    var lastError: Throwable? = null
        private set

    /**
     * Marks [phase] as completed and logs the event.
     *
     * Idempotent: concurrent or duplicate completions are safely ignored via
     * [ConcurrentHashMap.putIfAbsent].
     *
     * Logged at [LogPriority.INFO] so the message is visible in nightly and
     * preview builds where the minimum log priority is INFO.
     */
    fun complete(phase: Phase) {
        val entry = PhaseEntry(phase, System.currentTimeMillis())
        if (_completedPhases.putIfAbsent(phase, entry) != null) return // already completed
        val elapsed = entry.timestampMs - processStartMs
        logcat(LogPriority.INFO) { "[Startup] ✓ ${phase.displayName} (+${elapsed}ms)" }
    }

    /**
     * Records an error that occurred during a startup phase.
     *
     * Logged at [LogPriority.ERROR] with the full stack trace so it appears in
     * all build types and is easy to spot in logcat.
     */
    fun recordError(phase: Phase, error: Throwable) {
        lastError = error
        logcat(LogPriority.ERROR, error) { "[Startup] ✗ ${phase.displayName}" }
    }

    fun isComplete(phase: Phase): Boolean = _completedPhases.containsKey(phase)

    /** Returns elapsed milliseconds since the tracker was first loaded (proxy for process start). */
    fun elapsedMs(): Long = System.currentTimeMillis() - processStartMs
}
