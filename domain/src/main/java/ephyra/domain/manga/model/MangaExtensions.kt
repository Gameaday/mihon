package ephyra.domain.manga.model

/**
 * Computes an updated alternative titles list by merging new titles into the manga's existing list.
 * Deduplicates case-insensitively, filters blanks, and excludes the primary title.
 * Returns null if no new titles were added (caller can skip the DB update).
 */
fun Manga.mergedAlternativeTitles(newTitles: List<String>): List<String>? {
    val primary = title
    val existing = alternativeTitles.toMutableList()
    val previousSize = existing.size
    val existingLower = existing.map { it.lowercase() }.toMutableSet()
    val primaryLower = primary.lowercase()

    for (t in newTitles) {
        if (t.isBlank()) continue
        val lower = t.lowercase()
        if (lower == primaryLower) continue
        if (!existingLower.add(lower)) continue
        existing.add(t)
    }
    return if (existing.size > previousSize) existing else null
}
