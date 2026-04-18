package ephyra.domain.manga.model

/**
 * Thrown when a manga record cannot be found by the requested identifier.
 *
 * Using a typed domain exception (rather than a generic [Exception]) lets
 * callers distinguish "this manga does not exist" from other I/O or mapping
 * failures so they can respond appropriately (e.g. navigate away rather than
 * show a generic error).
 */
class MangaNotFoundException(id: Long) : Exception("Manga not found: id=$id")
