package ephyra.presentation.core.util

import android.graphics.RectF
import ephyra.domain.reader.service.ReaderPreferences

/**
 * Inverts the coordinates of this [RectF] based on the tapping invert mode.
 *
 * Used by reader navigation regions to support horizontal/vertical gesture inversion.
 */
fun RectF.invert(invertMode: ReaderPreferences.TappingInvertMode): RectF {
    val horizontal = invertMode.shouldInvertHorizontal
    val vertical = invertMode.shouldInvertVertical
    return when {
        horizontal && vertical -> RectF(1f - this.right, 1f - this.bottom, 1f - this.left, 1f - this.top)
        vertical -> RectF(this.left, 1f - this.bottom, this.right, 1f - this.top)
        horizontal -> RectF(1f - this.right, this.top, 1f - this.left, this.bottom)
        else -> this
    }
}
