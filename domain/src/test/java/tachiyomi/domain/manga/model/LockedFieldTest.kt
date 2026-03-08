package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class LockedFieldTest {

    @Test
    fun `isLocked returns true for set bit`() {
        LockedField.isLocked(LockedField.DESCRIPTION, LockedField.DESCRIPTION) shouldBe true
    }

    @Test
    fun `isLocked returns false for unset bit`() {
        LockedField.isLocked(LockedField.DESCRIPTION, LockedField.AUTHOR) shouldBe false
    }

    @Test
    fun `isLocked works with combined mask`() {
        val mask = LockedField.DESCRIPTION or LockedField.COVER
        LockedField.isLocked(mask, LockedField.DESCRIPTION) shouldBe true
        LockedField.isLocked(mask, LockedField.COVER) shouldBe true
        LockedField.isLocked(mask, LockedField.AUTHOR) shouldBe false
        LockedField.isLocked(mask, LockedField.STATUS) shouldBe false
    }

    @Test
    fun `NONE has no bits set`() {
        LockedField.ALL_FIELDS.forEach { field ->
            LockedField.isLocked(LockedField.NONE, field) shouldBe false
        }
    }

    @Test
    fun `ALL has all bits set`() {
        LockedField.ALL_FIELDS.forEach { field ->
            LockedField.isLocked(LockedField.ALL, field) shouldBe true
        }
    }

    @Test
    fun `xor toggles a field on and off`() {
        var mask = LockedField.NONE
        // Toggle on
        mask = mask xor LockedField.AUTHOR
        LockedField.isLocked(mask, LockedField.AUTHOR) shouldBe true
        // Toggle off
        mask = mask xor LockedField.AUTHOR
        LockedField.isLocked(mask, LockedField.AUTHOR) shouldBe false
    }

    @Test
    fun `label returns human-readable names`() {
        LockedField.label(LockedField.DESCRIPTION) shouldBe "Description"
        LockedField.label(LockedField.AUTHOR) shouldBe "Author"
        LockedField.label(LockedField.ARTIST) shouldBe "Artist"
        LockedField.label(LockedField.COVER) shouldBe "Cover"
        LockedField.label(LockedField.STATUS) shouldBe "Status"
        LockedField.label(LockedField.CONTENT_TYPE) shouldBe "Content type"
        LockedField.label(LockedField.GENRE) shouldBe "Genre"
        LockedField.label(LockedField.TITLE) shouldBe "Title"
    }

    @Test
    fun `ALL_FIELDS contains exactly 8 fields`() {
        LockedField.ALL_FIELDS.size shouldBe 8
    }

    @Test
    fun `GENRE flag works correctly`() {
        val mask = LockedField.GENRE
        LockedField.isLocked(mask, LockedField.GENRE) shouldBe true
        LockedField.isLocked(mask, LockedField.DESCRIPTION) shouldBe false
    }

    @Test
    fun `TITLE flag works correctly`() {
        val mask = LockedField.TITLE
        LockedField.isLocked(mask, LockedField.TITLE) shouldBe true
        LockedField.isLocked(mask, LockedField.DESCRIPTION) shouldBe false
        LockedField.isLocked(mask, LockedField.GENRE) shouldBe false
    }
}
