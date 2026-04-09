package ephyra.app.extension.model

import ephyra.domain.extension.model.Extension

sealed interface LoadResult {
    data class Success(val extension: Extension.Installed) : LoadResult
    data class Untrusted(val extension: Extension.Untrusted) : LoadResult
    data object Error : LoadResult
}
