package ephyra.feature.category.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.shouldExpandFAB

@Composable
fun CategoryFloatingActionButton(
    lazyListState: LazyListState,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        text = { Text(text = stringResource(MR.strings.action_add)) },
        icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
        onClick = onCreate,
        expanded = lazyListState.shouldExpandFAB(),
        modifier = modifier,
    )
}
