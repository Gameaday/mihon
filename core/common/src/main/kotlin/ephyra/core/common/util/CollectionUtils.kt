package ephyra.core.common.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun <E> HashSet<E>.addOrRemove(value: E, shouldAdd: Boolean) {
    if (shouldAdd) {
        add(value)
    } else {
        remove(value)
    }
}

fun <E> MutableList<E>.addOrRemove(value: E, shouldAdd: Boolean) {
    if (shouldAdd) {
        add(value)
    } else {
        remove(value)
    }
}

fun <T : R, R : Any> List<T>.insertSeparators(
    generator: (before: T?, after: T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in -1..lastIndex) {
        val before = getOrNull(i)
        before?.let(newList::add)
        val after = getOrNull(i + 1)
        val separator = generator.invoke(before, after)
        separator?.let(newList::add)
    }
    return newList
}

/**
 * Similar to [insertSeparators] but iterates from last to first element.
 */
fun <T : R, R : Any> List<T>.insertSeparatorsReversed(
    generator: (before: T?, after: T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in size downTo 0) {
        val after = getOrNull(i)
        after?.let(newList::add)
        val before = getOrNull(i - 1)
        val separator = generator.invoke(before, after)
        separator?.let(newList::add)
    }
    return newList.asReversed()
}

/**
 * Returns a list containing all elements **not** matching the given [predicate].
 *
 * Only use for collections that are known to support random access.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastFilterNot(predicate: (T) -> Boolean): List<T> {
    contract { callsInPlace(predicate) }
    return filter { !predicate(it) }
}

/**
 * Splits the original collection into a pair of lists.
 *
 * Only use for collections that are known to support random access.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastPartition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    contract { callsInPlace(predicate) }
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    forEach {
        if (predicate(it)) {
            first.add(it)
        } else {
            second.add(it)
        }
    }
    return Pair(first, second)
}

/**
 * Returns the number of entries **not** matching the given [predicate].
 *
 * Only use for collections that are known to support random access.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastCountNot(predicate: (T) -> Boolean): Int {
    contract { callsInPlace(predicate) }
    var count = size
    forEach { if (predicate(it)) --count }
    return count
}
