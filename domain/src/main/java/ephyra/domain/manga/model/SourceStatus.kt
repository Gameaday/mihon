package ephyra.domain.manga.model

/**
 * Represents the health status of a manga's chapter source.
 * Detected automatically during library refresh by comparing chapter counts.
 */
enum class SourceStatus(val value: Int) {
    /** Source is working normally. */
    HEALTHY(0),

    /** Source returned significantly fewer chapters than expected (possible partial removal). */
    DEGRADED(1),

    /** Source returned 0 chapters or errored on recent refreshes (likely dead/removed). */
    DEAD(2),

    /** User has switched to a replacement source after detecting issues. */
    REPLACED(3),
    ;

    companion object {
        private val map = entries.associateBy(SourceStatus::value)

        fun fromValue(value: Int): SourceStatus = map[value] ?: HEALTHY
    }
}
