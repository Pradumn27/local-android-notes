package com.localnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder")
    suspend fun getAll(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder, name")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE kind = :kind LIMIT 1")
    suspend fun getByKind(kind: com.localnotes.data.model.FolderKind): FolderEntity?

    @Query("SELECT * FROM folders WHERE appleId = :appleId LIMIT 1")
    suspend fun getByAppleId(appleId: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE appleId = :appleId LIMIT 1")
    suspend fun getByAppleId(appleId: String): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        ORDER BY pinned DESC, modifiedAt DESC
        LIMIT 1
        """,
    )
    suspend fun latestActive(): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        ORDER BY pinned DESC, modifiedAt DESC
        """,
    )
    suspend fun getActive(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND deletedAt IS NULL")
    suspend fun getByFolder(folderId: String): List<NoteEntity>

    @Query(
        """
        SELECT COUNT(*) FROM notes
        WHERE deletedAt IS NULL AND (:folderId IS NULL OR folderId = :folderId)
        """,
    )
    suspend fun countActive(folderId: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("SELECT * FROM notes WHERE dirty = 1")
    suspend fun getDirty(): List<NoteEntity>
}
