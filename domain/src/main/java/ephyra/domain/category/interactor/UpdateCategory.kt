package ephyra.domain.category.interactor

import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.domain.category.model.CategoryUpdate
import ephyra.domain.category.repository.CategoryRepository

class UpdateCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(payload: CategoryUpdate): Result = withNonCancellableContext {
        try {
            categoryRepository.updatePartial(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
