package ephyra.domain.extensionrepo.service

import eu.kanade.ephyra.network.GET
import eu.kanade.ephyra.network.NetworkHelper
import eu.kanade.ephyra.network.awaitSuccess
import eu.kanade.ephyra.network.parseAs
import kotlinx.serialization.json.Json
import logcat.LogPriority
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
) {
    val client = networkHelper.client

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            try {
                with(json) {
                    client.newCall(GET("$repo/repo.json"))
                        .awaitSuccess()
                        .parseAs<ExtensionRepoMetaDto>()
                        .toExtensionRepo(baseUrl = repo)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch repo details" }
                null
            }
        }
    }
}
