package ephyra.source.local.filter

import android.content.Context
import eu.kanade.tachiyomi.source.model.Filter
import ephyra.core.common.i18n.stringResource
import ephyra.i18n.MR

sealed class OrderBy(context: Context, selection: Selection) : Filter.Sort(
    context.stringResource(MR.strings.local_filter_order_by),
    arrayOf(context.stringResource(MR.strings.title), context.stringResource(MR.strings.date)),
    selection,
) {
    class Popular(context: Context) : OrderBy(context, Selection(0, true))
    class Latest(context: Context) : OrderBy(context, Selection(1, false))
}
