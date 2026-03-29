package ephyra.core.common.util.lang

import androidx.core.text.parseAsHtml
import java.nio.charset.StandardCharsets
import kotlin.math.floor

/**
 * Replaces the given string to have at most [count] characters using [replacement] at its end.
 * If [replacement] is longer than [count] an exception will be thrown when `length > count`.
 */
fun String.chop(count: Int, replacement: String = "…"): String {
    return if (length > count) {
        take(count - replacement.length) + replacement
    } else {
        this
    }
}

/**
 * Replaces the given string to have at most [count] characters using [replacement] near the center.
 * If [replacement] is longer than [count] an exception will be thrown when `length > count`.
 */
fun String.truncateCenter(count: Int, replacement: String = "..."): String {
    if (length <= count) {
        return this
    }

    val pieceLength: Int = floor((count - replacement.length).div(2.0)).toInt()

    return "${take(pieceLength)}$replacement${takeLast(pieceLength)}"
}

/**
 * Case-insensitive natural order comparison for strings.
 *
 * Splits each string into alternating numeric and non-numeric chunks, comparing numeric chunks by
 * their integer value and non-numeric chunks case-insensitively. This replaces the external
 * `java-nat-sort` (CaseInsensitiveSimpleNaturalComparator) dependency with an equivalent
 * pure-Kotlin implementation.
 *
 * Behavior notes (matching the original library):
 * - Leading zeros are ignored: "001" == "1" (both parse to 1 numerically).
 * - Numeric chunks are parsed as [Long]; sequences longer than 18 digits are unlikely in file
 *   or chapter names and would overflow silently — the same trade-off as the original library.
 */
fun String.compareToCaseInsensitiveNaturalOrder(other: String): Int {
    val a = this
    val b = other
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            // Extract and compare numeric chunks by value
            var numA = 0L
            while (i < a.length && a[i].isDigit()) {
                numA = numA * 10 + a[i].digitToInt()
                i++
            }
            var numB = 0L
            while (j < b.length && b[j].isDigit()) {
                numB = numB * 10 + b[j].digitToInt()
                j++
            }
            val cmp = numA.compareTo(numB)
            if (cmp != 0) return cmp
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return a.length.compareTo(b.length)
}

/**
 * Returns the size of the string as the number of bytes.
 */
fun String.byteSize(): Int {
    return toByteArray(StandardCharsets.UTF_8).size
}

/**
 * Returns a string containing the first [n] bytes from this string, or the entire string if this
 * string is shorter.
 */
fun String.takeBytes(n: Int): String {
    val bytes = toByteArray(StandardCharsets.UTF_8)
    return if (bytes.size <= n) {
        this
    } else {
        bytes.decodeToString(endIndex = n).replace("\uFFFD", "")
    }
}

/**
 * HTML-decode the string
 */
fun String.htmlDecode(): String {
    return this.parseAsHtml().toString()
}
