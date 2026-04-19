package ephyra.feature.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.category.presentation.CategoryScreen
import ephyra.feature.category.presentation.components.CategoryCreateDialog
import ephyra.feature.category.presentation.components.CategoryDeleteDialog
import ephyra.feature.category.presentation.components.CategoryRenameDialog
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest

class CategoryScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CategoryScreenModel>()

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is CategoryScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as CategoryScreenState.Success

        CategoryScreen(
            state = successState,
            onClickCreate = { screenModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Create)) },
            onClickRename = { screenModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Rename(it))) },
            onClickDelete = { screenModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Delete(it))) },
            onChangeOrder = { category, newIndex ->
                screenModel.onEvent(CategoryScreenEvent.ChangeOrder(category, newIndex))
            },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            CategoryDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = { screenModel.onEvent(CategoryScreenEvent.DismissDialog) },
                    onCreate = { screenModel.onEvent(CategoryScreenEvent.CreateCategory(it)) },
                    categories = successState.categories.fastMap { it.name }.toImmutableList(),
                )
            }

            is CategoryDialog.Rename -> {
                CategoryRenameDialog(
                    onDismissRequest = { screenModel.onEvent(CategoryScreenEvent.DismissDialog) },
                    onRename = { screenModel.onEvent(CategoryScreenEvent.RenameCategory(dialog.category, it)) },
                    categories = successState.categories.fastMap { it.name }.toImmutableList(),
                    category = dialog.category.name,
                )
            }

            is CategoryDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = { screenModel.onEvent(CategoryScreenEvent.DismissDialog) },
                    onDelete = { screenModel.onEvent(CategoryScreenEvent.DeleteCategory(dialog.category.id)) },
                    category = dialog.category.name,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is CategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
