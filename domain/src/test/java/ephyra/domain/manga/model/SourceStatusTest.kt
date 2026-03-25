package ephyra.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SourceStatusTest {

    @Test
    fun `fromValue returns correct enum for known values`() {
        SourceStatus.fromValue(0) shouldBe SourceStatus.HEALTHY
        SourceStatus.fromValue(1) shouldBe SourceStatus.DEGRADED
        SourceStatus.fromValue(2) shouldBe SourceStatus.DEAD
        SourceStatus.fromValue(3) shouldBe SourceStatus.REPLACED
    }

    @Test
    fun `fromValue returns HEALTHY for unknown values`() {
        SourceStatus.fromValue(-1) shouldBe SourceStatus.HEALTHY
        SourceStatus.fromValue(99) shouldBe SourceStatus.HEALTHY
    }

    @Test
    fun `value property matches integer representation`() {
        SourceStatus.HEALTHY.value shouldBe 0
        SourceStatus.DEGRADED.value shouldBe 1
        SourceStatus.DEAD.value shouldBe 2
        SourceStatus.REPLACED.value shouldBe 3
    }

    @Test
    fun `all entries have unique values`() {
        val values = SourceStatus.entries.map { it.value }
        values.toSet().size shouldBe values.size
    }

    @Test
    fun `roundtrip through value and fromValue`() {
        SourceStatus.entries.forEach { status ->
            SourceStatus.fromValue(status.value) shouldBe status
        }
    }

    @Test
    fun `entries count is 4`() {
        SourceStatus.entries.size shouldBe 4
    }

    @Test
    fun `HEALTHY is the default status value`() {
        // Default source_status column value is 0 which maps to HEALTHY
        SourceStatus.fromValue(0) shouldBe SourceStatus.HEALTHY
    }
}
