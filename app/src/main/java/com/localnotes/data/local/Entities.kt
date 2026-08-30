package com.localnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localnotes.data.model.FolderKind

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val appleId: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("parentId"), Index("appleId")],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val parentId: String?,
    val name: String,
    val kind: FolderKind,
    val appleId: String?,
    val sortOrder: Int,
    val dirty: Boolean,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("folderId"),
        Index("appleId"),
        Index("modifiedAt"),
        Index("deletedAt"),
        Index("pinned"),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val appleId: String?,
    val title: String,
    val plaintext: String,
    val html: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val pinned: Boolean,
    val deletedAt: Long?,
    val passwordProtected: Boolean,
    val dirty: Boolean,
    val lastSyncedAt: Long?,
)
