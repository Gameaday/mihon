package ephyra.domain.track.model

fun Track.copyPersonalFrom(other: Track): Track {
    return this.copy(
        lastChapterRead = other.lastChapterRead,
        score = other.score,
        status = other.status,
        startDate = other.startDate,
        finishDate = other.finishDate,
        isPrivate = other.isPrivate,
    )
}
