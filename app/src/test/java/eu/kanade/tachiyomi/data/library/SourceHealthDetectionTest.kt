package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.SourceStatus

class SourceHealthDetectionTest {

    // --- DEAD detection ---

    @Test
    fun `zero chapters when previously had chapters marks DEAD`() {
        assertEquals(
            SourceStatus.DEAD,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 0, previousCount = 100),
        )
    }

    @Test
    fun `zero chapters when previously had 1 chapter marks DEAD`() {
        assertEquals(
            SourceStatus.DEAD,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 0, previousCount = 1),
        )
    }

    @Test
    fun `zero chapters when previously had zero is HEALTHY (new manga)`() {
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 0, previousCount = 0),
        )
    }

    // --- DEGRADED detection (< 70% threshold) ---

    @Test
    fun `69 of 100 chapters marks DEGRADED`() {
        assertEquals(
            SourceStatus.DEGRADED,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 69, previousCount = 100),
        )
    }

    @Test
    fun `50 of 100 chapters marks DEGRADED`() {
        assertEquals(
            SourceStatus.DEGRADED,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 50, previousCount = 100),
        )
    }

    @Test
    fun `1 of 100 chapters marks DEGRADED`() {
        assertEquals(
            SourceStatus.DEGRADED,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 1, previousCount = 100),
        )
    }

    // --- HEALTHY detection ---

    @Test
    fun `70 of 100 chapters is HEALTHY (exactly at threshold)`() {
        // 70 * 10 = 700, 100 * 7 = 700, NOT strictly less than, so HEALTHY
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 70, previousCount = 100),
        )
    }

    @Test
    fun `71 of 100 chapters is HEALTHY`() {
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 71, previousCount = 100),
        )
    }

    @Test
    fun `100 of 100 chapters is HEALTHY`() {
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 100, previousCount = 100),
        )
    }

    @Test
    fun `more chapters than before is HEALTHY (chapter growth)`() {
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 150, previousCount = 100),
        )
    }

    // --- Recovery detection ---

    @Test
    fun `recovery from DEAD to full count is HEALTHY`() {
        // Simulates: source was DEAD (0 chapters last time), now returns full count
        // previousCount is 0 (was DEAD), fetchedCount is 100
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 100, previousCount = 0),
        )
    }

    @Test
    fun `first-time fetch with no previous chapters is HEALTHY`() {
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 50, previousCount = 0),
        )
    }

    // --- Edge cases ---

    @Test
    fun `small manga with 3 of 5 chapters is DEGRADED (60 pct)`() {
        // 3 * 10 = 30 < 5 * 7 = 35, so DEGRADED
        assertEquals(
            SourceStatus.DEGRADED,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 3, previousCount = 5),
        )
    }

    @Test
    fun `small manga with 4 of 5 chapters is HEALTHY (80 pct)`() {
        // 4 * 10 = 40 >= 5 * 7 = 35, so HEALTHY
        assertEquals(
            SourceStatus.HEALTHY,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 4, previousCount = 5),
        )
    }

    @Test
    fun `single chapter manga losing its only chapter is DEAD`() {
        assertEquals(
            SourceStatus.DEAD,
            LibraryUpdateJob.detectSourceHealth(fetchedCount = 0, previousCount = 1),
        )
    }

    // --- dead_since tracking ---

    @Test
    fun `DEAD_SINCE_CLEARED sentinel value is zero`() {
        assertEquals(0L, LibraryUpdateJob.DEAD_SINCE_CLEARED)
    }

    @Test
    fun `DEAD_MIGRATION_THRESHOLD_MS is 3 days`() {
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000
        assertEquals(threeDaysMs, LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }

    @Test
    fun `chapter drop threshold constants produce 70 percent`() {
        // 7/10 = 0.7, meaning < 70% is DEGRADED
        val numerator = LibraryUpdateJob.CHAPTER_DROP_THRESHOLD_NUMERATOR
        val denominator = LibraryUpdateJob.CHAPTER_DROP_THRESHOLD_DENOMINATOR
        assertEquals(7, numerator)
        assertEquals(10, denominator)
    }
}
