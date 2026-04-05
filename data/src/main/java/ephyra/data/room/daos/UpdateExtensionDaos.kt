package ephyra.data.room.daos

import androidx.room.*
import ephyra.data.room.entities.ExtensionRepoEntity
import ephyra.data.room.views.UpdatesView
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateDao {
    @Query("SELECT * FROM updatesView WHERE read = :read AND dateFetch > :after ORDER BY dateFetch DESC LIMIT :limit")
    fun getUpdatesByReadStatus(read: Boolean, after: Long, limit: Long): Flow<List<UpdatesView>>

    @Query("SELECT * FROM updatesView WHERE read = :read AND dateFetch > :after ORDER BY dateFetch DESC LIMIT :limit")
    suspend fun getUpdatesByReadStatusBlocking(read: Boolean, after: Long, limit: Long): List<UpdatesView>

    @Query(
        """
        SELECT * FROM updatesView
        WHERE dateFetch > :after
        AND (:read IS NULL OR read = :read)
        AND (:bookmarked IS NULL OR bookmark = :bookmarked)
        AND (:hideExcludedScanlators = 0 OR excludedScanlator IS NULL)
        ORDER BY dateFetch DESC
        LIMIT :limit
    """,
    )
    fun getRecentUpdatesWithFilters(
        after: Long,
        limit: Long,
        read: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Int,
    ): Flow<List<UpdatesView>>
}

@Dao
interface ExtensionRepoDao {
    @Query("SELECT * FROM extension_repos")
    fun getExtensionReposAsFlow(): Flow<List<ExtensionRepoEntity>>

    @Query("SELECT * FROM extension_repos")
    suspend fun getExtensionRepos(): List<ExtensionRepoEntity>

    @Query("SELECT * FROM extension_repos WHERE base_url = :baseUrl")
    suspend fun getRepo(baseUrl: String): ExtensionRepoEntity?

    @Query("SELECT * FROM extension_repos WHERE signing_key_fingerprint = :fingerprint")
    suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepoEntity?

    @Query("SELECT count(*) FROM extension_repos")
    fun getCountAsFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: ExtensionRepoEntity): Long

    @Update
    suspend fun update(repo: ExtensionRepoEntity)

    @Transaction
    suspend fun upsert(repo: ExtensionRepoEntity) {
        val existing = getRepo(repo.baseUrl)
        if (existing != null) {
            update(repo)
        } else {
            insert(repo)
        }
    }

    @Query("DELETE FROM extension_repos WHERE base_url = :baseUrl")
    suspend fun delete(baseUrl: String)
}
