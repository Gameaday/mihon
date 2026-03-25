package ephyra.domain.manga.model

/**
 * Bitmask flags for per-field metadata locking (Jellyfin-style).
 *
 * When a user manually edits a metadata field, or explicitly locks it via the UI,
 * that field's bit is set in [Manga.lockedFields]. Authority metadata refresh
 * ([RefreshCanonicalMetadata]) checks these bits and skips locked fields — the
 * user's customization is preserved even as the canonical source refreshes.
 *
 * ## Usage
 *
 * ```kotlin
 * // Lock a field
 * val newMask = manga.lockedFields or LockedField.DESCRIPTION
 *
 * // Toggle a field (lock if unlocked, unlock if locked)
 * val newMask = manga.lockedFields xor LockedField.DESCRIPTION
 *
 * // Check if a field is locked
 * val isLocked = manga.lockedFields and LockedField.DESCRIPTION != 0L
 * ```
 *
 * Stored as an INTEGER column in the `mangas` table. Adding new fields is
 * backward-compatible — unset bits default to "unlocked".
 */
object LockedField {

    /** Description / synopsis. */
    const val DESCRIPTION: Long = 1L shl 0 // 0x01

    /** Author name(s). */
    const val AUTHOR: Long = 1L shl 1 // 0x02

    /** Artist name(s). */
    const val ARTIST: Long = 1L shl 2 // 0x04

    /** Cover / thumbnail URL. */
    const val COVER: Long = 1L shl 3 // 0x08

    /** Publishing status (ongoing, completed, etc.). */
    const val STATUS: Long = 1L shl 4 // 0x10

    /** Content type (manga, novel, book). */
    const val CONTENT_TYPE: Long = 1L shl 5 // 0x20

    /** Genre / tag list. */
    const val GENRE: Long = 1L shl 6 // 0x40

    /** Title (primary series name). */
    const val TITLE: Long = 1L shl 7 // 0x80

    /** All lockable fields ORed together. */
    const val ALL: Long = DESCRIPTION or AUTHOR or ARTIST or COVER or STATUS or CONTENT_TYPE or GENRE or TITLE

    /** No fields locked. */
    const val NONE: Long = 0L

    /** Human-readable label for a field flag. */
    fun label(field: Long): String = when (field) {
        DESCRIPTION -> "Description"
        AUTHOR -> "Author"
        ARTIST -> "Artist"
        COVER -> "Cover"
        STATUS -> "Status"
        CONTENT_TYPE -> "Content type"
        GENRE -> "Genre"
        TITLE -> "Title"
        else -> "Unknown"
    }

    /** All individual field flags in display order. */
    val ALL_FIELDS: List<Long> = listOf(
        TITLE,
        DESCRIPTION,
        AUTHOR,
        ARTIST,
        COVER,
        STATUS,
        CONTENT_TYPE,
        GENRE,
    )

    /** Returns true if [field] is set in [mask]. */
    fun isLocked(mask: Long, field: Long): Boolean = mask and field != 0L
}
