package ephyra.presentation.manga.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SourceHealthBannerTest {

    @Test
    fun `formatDeadDuration returns null for zero`() {
        assertNull(formatDeadDuration(0))
    }

    @Test
    fun `formatDeadDuration returns null for negative`() {
        assertNull(formatDeadDuration(-1000))
    }

    @Test
    fun `formatDeadDuration returns less than 1d for recent timestamp`() {
        val recentTimestamp = System.currentTimeMillis() - (12 * 60 * 60 * 1000) // 12 hours ago
        assertEquals("<1d", formatDeadDuration(recentTimestamp))
    }

    @Test
    fun `formatDeadDuration returns 1d for 1 day ago`() {
        val oneDayAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000) // 25 hours ago
        assertEquals("1d", formatDeadDuration(oneDayAgo))
    }

    @Test
    fun `formatDeadDuration returns 3d for 3 days ago`() {
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000 + 1000)
        assertEquals("3d", formatDeadDuration(threeDaysAgo))
    }

    @Test
    fun `formatDeadDuration returns 7d for 1 week ago`() {
        val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        assertEquals("7d", formatDeadDuration(oneWeekAgo))
    }

    @Test
    fun `formatDeadDuration returns null for future timestamp`() {
        val futureTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        assertNull(formatDeadDuration(futureTimestamp))
    }
}
