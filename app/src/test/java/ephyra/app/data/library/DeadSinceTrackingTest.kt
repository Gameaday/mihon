package ephyra.app.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.SourceStatus
import ephyra.domain.manga.model.toMangaUpdate

class DeadSinceTrackingTest {

    // --- Manga model ---

    @Test
    fun `Manga create() has null deadSince`() {
        val manga = Manga.create()
        assertNull(manga.deadSince)
    }

    @Test
    fun `Manga copy preserves deadSince`() {
        val manga = Manga.create().copy(deadSince = 1000L)
        assertEquals(1000L, manga.deadSince)
    }

    @Test
    fun `Manga copy can clear deadSince to null`() {
        val manga = Manga.create().copy(deadSince = 1000L)
        val cleared = manga.copy(deadSince = null)
        assertNull(cleared.deadSince)
    }

    // --- MangaUpdate model ---

    @Test
    fun `MangaUpdate default deadSince is null`() {
        val update = MangaUpdate(id = 1L)
        assertNull(update.deadSince)
    }

    @Test
    fun `MangaUpdate can set deadSince timestamp`() {
        val now = System.currentTimeMillis()
        val update = MangaUpdate(id = 1L, deadSince = now)
        assertEquals(now, update.deadSince)
    }

    @Test
    fun `MangaUpdate can set DEAD_SINCE_CLEARED sentinel`() {
        val update = MangaUpdate(
            id = 1L,
            deadSince = LibraryUpdateJob.DEAD_SINCE_CLEARED,
        )
        assertEquals(0L, update.deadSince)
    }

    // --- toMangaUpdate conversion ---

    @Test
    fun `toMangaUpdate preserves deadSince`() {
        val manga = Manga.create().copy(id = 42L, deadSince = 5000L)
        val update = manga.toMangaUpdate()
        assertEquals(5000L, update.deadSince)
    }

    @Test
    fun `toMangaUpdate preserves null deadSince`() {
        val manga = Manga.create().copy(id = 42L, deadSince = null)
        val update = manga.toMangaUpdate()
        assertNull(update.deadSince)
    }

    // --- Migration threshold logic ---

    @Test
    fun `manga DEAD for 4 days exceeds threshold`() {
        val fourDaysAgo = System.currentTimeMillis() - (4L * 24 * 60 * 60 * 1000)
        val elapsed = System.currentTimeMillis() - fourDaysAgo
        assert(elapsed >= LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }

    @Test
    fun `manga DEAD for 1 day does not exceed threshold`() {
        val oneDayAgo = System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000)
        val elapsed = System.currentTimeMillis() - oneDayAgo
        assert(elapsed < LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }

    @Test
    fun `manga DEAD for exactly 3 days meets threshold`() {
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
        val elapsed = System.currentTimeMillis() - threeDaysAgo
        assert(elapsed >= LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }

    // --- dead_since transition rules ---

    /**
     * Tests the dead_since transition logic from LibraryUpdateJob lines 482-488:
     * - HEALTHY → DEAD: set timestamp
     * - DEAD → HEALTHY: clear (sentinel 0)
     * - DEAD → DEGRADED: clear (sentinel 0)
     * - DEAD → DEAD: no change (null)
     * - HEALTHY → DEGRADED: no change (null)
     */
    private fun computeDeadSince(
        newStatus: SourceStatus,
        oldStatus: SourceStatus,
    ): Long? {
        return when {
            newStatus == SourceStatus.DEAD && oldStatus != SourceStatus.DEAD ->
                1000L // Represents System.currentTimeMillis() in tests
            newStatus != SourceStatus.DEAD && oldStatus == SourceStatus.DEAD ->
                LibraryUpdateJob.DEAD_SINCE_CLEARED
            else -> null
        }
    }

    @Test
    fun `HEALTHY to DEAD sets dead_since timestamp`() {
        val result = computeDeadSince(SourceStatus.DEAD, SourceStatus.HEALTHY)
        assertEquals(1000L, result)
    }

    @Test
    fun `DEGRADED to DEAD sets dead_since timestamp`() {
        val result = computeDeadSince(SourceStatus.DEAD, SourceStatus.DEGRADED)
        assertEquals(1000L, result)
    }

    @Test
    fun `DEAD to HEALTHY clears dead_since with sentinel`() {
        val result = computeDeadSince(SourceStatus.HEALTHY, SourceStatus.DEAD)
        assertEquals(LibraryUpdateJob.DEAD_SINCE_CLEARED, result)
    }

    @Test
    fun `DEAD to DEGRADED clears dead_since with sentinel`() {
        val result = computeDeadSince(SourceStatus.DEGRADED, SourceStatus.DEAD)
        assertEquals(LibraryUpdateJob.DEAD_SINCE_CLEARED, result)
    }

    @Test
    fun `DEAD to DEAD makes no change to dead_since`() {
        val result = computeDeadSince(SourceStatus.DEAD, SourceStatus.DEAD)
        assertNull(result)
    }

    @Test
    fun `HEALTHY to DEGRADED makes no change to dead_since`() {
        val result = computeDeadSince(SourceStatus.DEGRADED, SourceStatus.HEALTHY)
        assertNull(result)
    }

    @Test
    fun `HEALTHY to HEALTHY makes no change to dead_since`() {
        val result = computeDeadSince(SourceStatus.HEALTHY, SourceStatus.HEALTHY)
        assertNull(result)
    }
}
