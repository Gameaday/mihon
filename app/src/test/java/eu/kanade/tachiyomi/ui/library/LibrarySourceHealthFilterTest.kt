package eu.kanade.tachiyomi.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.SourceStatus

/**
 * Tests for the library source health filter logic.
 * The filter checks SourceStatus.fromValue() and applies TriState filtering:
 * - ENABLED_IS: show only DEAD or DEGRADED manga
 * - ENABLED_NOT: hide DEAD or DEGRADED manga
 * - DISABLED: show all (no filter)
 */
class LibrarySourceHealthFilterTest {

    /**
     * Replicates the filter logic from LibraryScreenModel.applyFilters():
     * ```
     * val status = SourceStatus.fromValue(it.libraryManga.manga.sourceStatus)
     * status == SourceStatus.DEAD || status == SourceStatus.DEGRADED
     * ```
     * Combined with applyFilter() TriState logic.
     */
    private fun isUnhealthy(sourceStatus: Int): Boolean {
        val status = SourceStatus.fromValue(sourceStatus)
        return status == SourceStatus.DEAD || status == SourceStatus.DEGRADED
    }

    /**
     * Simulates applyFilter() behavior from LibraryScreenModel.
     */
    private fun applyFilter(filterState: TriState, predicate: () -> Boolean): Boolean {
        return when (filterState) {
            TriState.DISABLED -> true
            TriState.ENABLED_IS -> predicate()
            TriState.ENABLED_NOT -> !predicate()
        }
    }

    private fun filterSourceHealth(filterState: TriState, sourceStatus: Int): Boolean {
        return applyFilter(filterState) { isUnhealthy(sourceStatus) }
    }

    // --- SourceStatus.fromValue() ---

    @Test
    fun `fromValue maps 0 to HEALTHY`() {
        assertEquals(SourceStatus.HEALTHY, SourceStatus.fromValue(0))
    }

    @Test
    fun `fromValue maps 1 to DEGRADED`() {
        assertEquals(SourceStatus.DEGRADED, SourceStatus.fromValue(1))
    }

    @Test
    fun `fromValue maps 2 to DEAD`() {
        assertEquals(SourceStatus.DEAD, SourceStatus.fromValue(2))
    }

    @Test
    fun `fromValue maps 3 to REPLACED`() {
        assertEquals(SourceStatus.REPLACED, SourceStatus.fromValue(3))
    }

    @Test
    fun `fromValue defaults unknown values to HEALTHY`() {
        assertEquals(SourceStatus.HEALTHY, SourceStatus.fromValue(999))
        assertEquals(SourceStatus.HEALTHY, SourceStatus.fromValue(-1))
    }

    // --- isUnhealthy check ---

    @Test
    fun `DEAD is unhealthy`() {
        assertTrue(isUnhealthy(SourceStatus.DEAD.value))
    }

    @Test
    fun `DEGRADED is unhealthy`() {
        assertTrue(isUnhealthy(SourceStatus.DEGRADED.value))
    }

    @Test
    fun `HEALTHY is not unhealthy`() {
        assertFalse(isUnhealthy(SourceStatus.HEALTHY.value))
    }

    @Test
    fun `REPLACED is not unhealthy`() {
        assertFalse(isUnhealthy(SourceStatus.REPLACED.value))
    }

    // --- Filter with DISABLED (show all) ---

    @Test
    fun `DISABLED filter shows HEALTHY manga`() {
        assertTrue(filterSourceHealth(TriState.DISABLED, SourceStatus.HEALTHY.value))
    }

    @Test
    fun `DISABLED filter shows DEAD manga`() {
        assertTrue(filterSourceHealth(TriState.DISABLED, SourceStatus.DEAD.value))
    }

    // --- Filter with ENABLED_IS (show only unhealthy) ---

    @Test
    fun `ENABLED_IS filter shows DEAD manga`() {
        assertTrue(filterSourceHealth(TriState.ENABLED_IS, SourceStatus.DEAD.value))
    }

    @Test
    fun `ENABLED_IS filter shows DEGRADED manga`() {
        assertTrue(filterSourceHealth(TriState.ENABLED_IS, SourceStatus.DEGRADED.value))
    }

    @Test
    fun `ENABLED_IS filter hides HEALTHY manga`() {
        assertFalse(filterSourceHealth(TriState.ENABLED_IS, SourceStatus.HEALTHY.value))
    }

    @Test
    fun `ENABLED_IS filter hides REPLACED manga`() {
        assertFalse(filterSourceHealth(TriState.ENABLED_IS, SourceStatus.REPLACED.value))
    }

    // --- Filter with ENABLED_NOT (hide unhealthy) ---

    @Test
    fun `ENABLED_NOT filter hides DEAD manga`() {
        assertFalse(filterSourceHealth(TriState.ENABLED_NOT, SourceStatus.DEAD.value))
    }

    @Test
    fun `ENABLED_NOT filter hides DEGRADED manga`() {
        assertFalse(filterSourceHealth(TriState.ENABLED_NOT, SourceStatus.DEGRADED.value))
    }

    @Test
    fun `ENABLED_NOT filter shows HEALTHY manga`() {
        assertTrue(filterSourceHealth(TriState.ENABLED_NOT, SourceStatus.HEALTHY.value))
    }

    @Test
    fun `ENABLED_NOT filter shows REPLACED manga`() {
        assertTrue(filterSourceHealth(TriState.ENABLED_NOT, SourceStatus.REPLACED.value))
    }
}
