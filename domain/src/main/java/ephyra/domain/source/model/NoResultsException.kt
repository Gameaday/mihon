package ephyra.domain.source.model

/**
 * Thrown by a paging source when the remote search returns an empty result set.
 * Defined in the domain layer so that UI-layer formatters (presentation-core) can
 * match on it without importing from the data layer.
 */
class NoResultsException : Exception()
