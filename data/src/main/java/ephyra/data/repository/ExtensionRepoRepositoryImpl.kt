package ephyra.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ephyra.data.room.daos.ExtensionRepoDao
import ephyra.data.room.entities.ExtensionRepoEntity
import ephyra.domain.extensionrepo.exception.SaveExtensionRepoException
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository

class ExtensionRepoRepositoryImpl(
    private val extensionRepoDao: ExtensionRepoDao,
) : ExtensionRepoRepository {

    override fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return extensionRepoDao.getExtensionReposAsFlow().map { list ->
            list.map(ExtensionRepoMapper::mapExtensionRepo)
        }
    }

    override suspend fun getAll(): List<ExtensionRepo> {
        return extensionRepoDao.getExtensionRepos().map(ExtensionRepoMapper::mapExtensionRepo)
    }

    override suspend fun getRepo(baseUrl: String): ExtensionRepo? {
        return extensionRepoDao.getRepo(baseUrl)?.let(ExtensionRepoMapper::mapExtensionRepo)
    }

    override suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? {
        return extensionRepoDao.getRepoBySigningKeyFingerprint(fingerprint)
            ?.let(ExtensionRepoMapper::mapExtensionRepo)
    }

    override fun getCount(): Flow<Int> {
        return extensionRepoDao.getCountAsFlow()
    }

    override suspend fun insertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        try {
            val entity = ExtensionRepoEntity(
                baseUrl = baseUrl,
                name = name,
                shortName = shortName,
                website = website,
                signingKeyFingerprint = signingKeyFingerprint,
            )
            extensionRepoDao.insert(entity)
        } catch (e: Exception) {
            throw SaveExtensionRepoException(e)
        }
    }

    override suspend fun upsertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        try {
            val entity = ExtensionRepoEntity(
                baseUrl = baseUrl,
                name = name,
                shortName = shortName,
                website = website,
                signingKeyFingerprint = signingKeyFingerprint,
            )
            extensionRepoDao.upsert(entity)
        } catch (e: Exception) {
            throw SaveExtensionRepoException(e)
        }
    }

    override suspend fun replaceRepo(newRepo: ExtensionRepo) {
        val entity = ExtensionRepoEntity(
            baseUrl = newRepo.baseUrl,
            name = newRepo.name,
            shortName = newRepo.shortName,
            website = newRepo.website,
            signingKeyFingerprint = newRepo.signingKeyFingerprint,
        )
        extensionRepoDao.upsert(entity)
    }

    override suspend fun deleteRepo(baseUrl: String) {
        extensionRepoDao.delete(baseUrl)
    }
}
