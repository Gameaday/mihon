package ephyra.feature.category

import ephyra.domain.category.model.Category

sealed interface CategoryScreenEvent {
    data class CreateCategory(val name: String) : CategoryScreenEvent
    data class DeleteCategory(val categoryId: Long) : CategoryScreenEvent
    data class ChangeOrder(val category: Category, val newIndex: Int) : CategoryScreenEvent
    data class RenameCategory(val category: Category, val name: String) : CategoryScreenEvent
    data class ShowDialog(val dialog: CategoryDialog) : CategoryScreenEvent
    data object DismissDialog : CategoryScreenEvent
}
