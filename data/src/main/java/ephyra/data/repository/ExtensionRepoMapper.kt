package ephyra.data.repository

import ephyra.data.room.entities.ExtensionRepoEntity
import ephyra.domain.extensionrepo.model.ExtensionRepo

object ExtensionRepoMapper {
    fun mapExtensionRepo(entity: ExtensionRepoEntity): ExtensionRepo = ExtensionRepo(
        baseUrl = entity.baseUrl,
        name = entity.name,
        shortName = entity.shortName,
        website = entity.website,
        signingKeyFingerprint = entity.signingKeyFingerprint,
    )
}
