package ephyra.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import ephyra.core.common.i18n.stringResource
import ephyra.domain.category.model.Category
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource

val Category.visualName: String
    @Composable
    get() = when {
        isSystemCategory -> stringResource(MR.strings.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        isSystemCategory -> context.stringResource(MR.strings.label_default)
        else -> name
    }
