package com.localnotes.data.repository

import com.localnotes.data.html.AppleNotesHtml
import com.localnotes.data.local.AccountEntity
import com.localnotes.data.local.FolderEntity
import com.localnotes.data.local.NoteEntity
import com.localnotes.data.local.NotesDatabase
import com.localnotes.data.model.FolderItem
import com.localnotes.data.model.FolderKind
import com.localnotes.data.model.NoteBlock
import com.localnotes.data.model.NoteDetail
import com.localnotes.data.model.NoteSort
import com.localnotes.data.model.NoteSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

class NotesRepository(private val db: NotesDatabase) {

    val accounts: Flow<List<AccountEntity>> = db.accounts().observeAll()
    val folders: Flow<List<FolderEntity>> = db.folders().observeAll()
    val notes: Flow<List<NoteEntity>> = db.notes().observeAll()

    val folderTree: Flow<List<FolderItem>> = combine(folders, notes) { folderRows, noteRows ->
        buildFolderTree(folderRows, noteRows)
    }

    fun observeNote(id: String): Flow<NoteDetail?> =
        combine(db.notes().observeById(id), notes) { entity, all ->
            entity?.toDetail(noteTargets(all, exceptId = entity.id))
        }

    fun observeSummaries(
        folderId: String,
        query: String,
        sort: NoteSort,
    ): Flow<List<NoteSummary>> {
        return combine(notes, folders) { all, folderRows ->
            val kind = folderRows.find { it.id == folderId }?.kind
            val filtered = all.filter { note ->
                when (kind) {
                    FolderKind.ALL -> note.deletedAt == null
                    FolderKind.RECENTLY_DELETED -> note.deletedAt != null
                    else -> note.deletedAt == null && note.folderId == folderId
                }
            }.filter { note ->
                query.isBlank() ||
                    note.title.contains(query, ignoreCase = true) ||
                    note.plaintext.contains(query, ignoreCase = true)
            }.map { it.toSummary() }
            sortSummaries(filtered, sort)
        }
    }

    suspend fun createFolder(name: String, parentId: String? = null): FolderEntity {
        val trimmed = name.trim().ifBlank { "New Folder" }
        val existing = db.folders().getAll()
        val sortOrder = (existing.filter { it.kind == FolderKind.USER }.maxOfOrNull { it.sortOrder } ?: 10) + 1
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            accountId = NotesDatabase.DEFAULT_ACCOUNT_ID,
            parentId = parentId?.takeIf { it != NotesDatabase.ALL_FOLDER_ID && it != NotesDatabase.DELETED_FOLDER_ID },
            name = trimmed,
            kind = FolderKind.USER,
            appleId = null,
            sortOrder = sortOrder,
            dirty = true,
        )
        db.folders().upsert(folder)
        return folder
    }

    suspend fun renameFolder(id: String, name: String) {
        val folder = db.folders().getById(id) ?: return
        if (folder.kind != FolderKind.USER) return
        db.folders().update(folder.copy(name = name.trim().ifBlank { folder.name }, dirty = true))
    }

    suspend fun deleteFolder(id: String) {
        val folder = db.folders().getById(id) ?: return
        if (folder.kind != FolderKind.USER) return
        val notesFolder = db.folders().getByKind(FolderKind.NOTES) ?: return
        db.notes().getByFolder(id).forEach { note ->
            db.notes().update(note.copy(folderId = notesFolder.id, dirty = true, modifiedAt = System.currentTimeMillis()))
        }
        db.folders().getAll().filter { it.parentId == id }.forEach { child ->
            db.folders().update(child.copy(parentId = folder.parentId, dirty = true))
        }
        db.folders().delete(id)
    }

    suspend fun createNote(folderId: String): NoteEntity {
        val targetFolderId = resolveWritableFolder(folderId)
        val now = System.currentTimeMillis()
        val blocks = listOf(NoteBlock(UUID.randomUUID().toString(), com.localnotes.data.model.BlockType.TITLE, ""))
        val encoded = AppleNotesHtml.encode(blocks)
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            folderId = targetFolderId,
            appleId = null,
            title = encoded.title,
            plaintext = encoded.plaintext,
            html = encoded.html,
            createdAt = now,
            modifiedAt = now,
            pinned = false,
            deletedAt = null,
            passwordProtected = false,
            dirty = true,
            lastSyncedAt = null,
        )
        db.notes().upsert(note)
        return note
    }

    suspend fun saveBlocks(noteId: String, blocks: List<NoteBlock>) {
        val existing = db.notes().getById(noteId) ?: return
        val encoded = AppleNotesHtml.encode(blocks)
        db.notes().update(
            existing.copy(
                title = encoded.title,
                plaintext = encoded.plaintext,
                html = encoded.html,
                modifiedAt = System.currentTimeMillis(),
                dirty = true,
            ),
        )
    }

    suspend fun setPinned(noteId: String, pinned: Boolean) {
        val existing = db.notes().getById(noteId) ?: return
        db.notes().update(existing.copy(pinned = pinned, modifiedAt = System.currentTimeMillis(), dirty = true))
    }

    suspend fun moveNote(noteId: String, folderId: String) {
        val existing = db.notes().getById(noteId) ?: return
        val target = resolveWritableFolder(folderId)
        db.notes().update(
            existing.copy(
                folderId = target,
                deletedAt = null,
                modifiedAt = System.currentTimeMillis(),
                dirty = true,
            ),
        )
    }

    suspend fun deleteNote(noteId: String) {
        val existing = db.notes().getById(noteId) ?: return
        db.notes().update(
            existing.copy(
                deletedAt = System.currentTimeMillis(),
                pinned = false,
                dirty = true,
            ),
        )
    }

    suspend fun restoreNote(noteId: String) {
        val existing = db.notes().getById(noteId) ?: return
        db.notes().update(existing.copy(deletedAt = null, dirty = true, modifiedAt = System.currentTimeMillis()))
    }

    suspend fun permanentlyDelete(noteId: String) {
        db.notes().delete(noteId)
    }

    suspend fun purgeExpiredDeleted() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        db.notes().purgeDeletedBefore(cutoff)
    }

    suspend fun defaultFolderId(): String {
        return db.folders().getByKind(FolderKind.NOTES)?.id ?: NotesDatabase.DEFAULT_NOTES_FOLDER_ID
    }

    suspend fun allNotes(): List<NoteEntity> = db.notes().getAll()

    suspend fun allFolders(): List<FolderEntity> = db.folders().getAll()

    suspend fun dirtyNotes(): List<NoteEntity> = db.notes().getDirty()

    suspend fun noteByAppleId(appleId: String): NoteEntity? = db.notes().getByAppleId(appleId)

    suspend fun noteById(id: String): NoteEntity? = db.notes().getById(id)

    suspend fun findNoteForLink(href: String): NoteEntity? {
        val all = db.notes().getAll().filter { it.deletedAt == null }
        val raw = href.trim()
        if (raw.startsWith("x-coredata:")) {
            return all.find { it.appleId == raw }
        }
        val identifier = Regex("(?:applenotes|notes|mobilenotes):(?://showNote\\?identifier=|//note/|note/)([^\\s&]+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)
        if (identifier != null) {
            all.find { it.appleId?.endsWith(identifier) == true || it.appleId?.contains(identifier) == true }?.let { return it }
        }
        val title = when {
            raw.startsWith(">>") -> raw.removePrefix(">>").trim()
            raw.startsWith("notes://") && !raw.contains("identifier=", ignoreCase = true) ->
                raw.removePrefix("notes://").trim()
            else -> ""
        }
        if (title.isBlank() || title.contains("://")) return null
        return all.find { it.title.equals(title, ignoreCase = true) }
            ?: all.find { title.length >= 3 && it.title.contains(title, ignoreCase = true) }
    }

    suspend fun latestActiveNote(): NoteEntity? = db.notes().latestActive()

    suspend fun activeNotes(): List<NoteEntity> = db.notes().getActive()

    suspend fun attachFolderAppleId(folderId: String, appleId: String) {
        val folder = db.folders().getById(folderId) ?: return
        db.folders().update(folder.copy(appleId = appleId, dirty = false))
    }

    suspend fun markFolderClean(folderId: String, appleId: String? = null) {
        val folder = db.folders().getById(folderId) ?: return
        db.folders().update(folder.copy(appleId = appleId ?: folder.appleId, dirty = false))
    }

    suspend fun upsertRemoteFolder(name: String, appleId: String): FolderEntity {
        val existing = db.folders().getByAppleId(appleId)
            ?: db.folders().getAll().firstOrNull {
                it.appleId == null &&
                    it.name.equals(name, ignoreCase = true) &&
                    it.kind != FolderKind.ALL &&
                    it.kind != FolderKind.RECENTLY_DELETED
            }
        if (existing != null) {
            val updated = existing.copy(appleId = appleId, dirty = false)
            db.folders().update(updated)
            return updated
        }
        val kind = when {
            name.equals("Notes", ignoreCase = true) -> FolderKind.NOTES
            name.equals("Quick Notes", ignoreCase = true) -> FolderKind.QUICK_NOTES
            else -> FolderKind.USER
        }
        if (kind == FolderKind.NOTES) {
            db.folders().getByKind(FolderKind.NOTES)?.let { notes ->
                val updated = notes.copy(appleId = appleId, dirty = false)
                db.folders().update(updated)
                return updated
            }
        }
        val sortOrder = (db.folders().getAll().maxOfOrNull { it.sortOrder } ?: 10) + 1
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            accountId = NotesDatabase.DEFAULT_ACCOUNT_ID,
            parentId = null,
            name = name,
            kind = kind,
            appleId = appleId,
            sortOrder = sortOrder,
            dirty = false,
        )
        db.folders().upsert(folder)
        return folder
    }

    suspend fun applyRemoteNote(
        appleId: String,
        folderId: String,
        title: String,
        plaintext: String,
        html: String,
        createdAt: Long,
        modifiedAt: Long,
        passwordProtected: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val existing = db.notes().getByAppleId(appleId)
        if (existing != null) {
            db.notes().update(
                existing.copy(
                    folderId = folderId,
                    title = title.ifBlank { existing.title },
                    plaintext = if (passwordProtected) existing.plaintext else plaintext,
                    html = if (passwordProtected) existing.html else html,
                    createdAt = createdAt.takeIf { it > 0 } ?: existing.createdAt,
                    modifiedAt = modifiedAt,
                    deletedAt = null,
                    passwordProtected = passwordProtected,
                    dirty = false,
                    lastSyncedAt = now,
                    appleId = appleId,
                ),
            )
            return
        }
        db.notes().upsert(
            NoteEntity(
                id = UUID.randomUUID().toString(),
                folderId = folderId,
                appleId = appleId,
                title = title.ifBlank { "New Note" },
                plaintext = plaintext,
                html = html,
                createdAt = createdAt.takeIf { it > 0 } ?: now,
                modifiedAt = modifiedAt.takeIf { it > 0 } ?: now,
                pinned = false,
                deletedAt = null,
                passwordProtected = passwordProtected,
                dirty = false,
                lastSyncedAt = now,
            ),
        )
    }

    suspend fun markNoteSynced(noteId: String, appleId: String?, modifiedAt: Long? = null) {
        val existing = db.notes().getById(noteId) ?: return
        db.notes().update(
            existing.copy(
                appleId = appleId ?: existing.appleId,
                dirty = false,
                lastSyncedAt = System.currentTimeMillis(),
                modifiedAt = modifiedAt ?: existing.modifiedAt,
            ),
        )
    }

    suspend fun markNoteDeletedFromRemote(noteId: String) {
        val existing = db.notes().getById(noteId) ?: return
        if (existing.deletedAt != null) {
            db.notes().update(existing.copy(dirty = false, lastSyncedAt = System.currentTimeMillis()))
            return
        }
        db.notes().update(
            existing.copy(
                deletedAt = System.currentTimeMillis(),
                pinned = false,
                dirty = false,
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun folderById(id: String): FolderEntity? = db.folders().getById(id)

    private suspend fun resolveWritableFolder(folderId: String): String {
        val folder = db.folders().getById(folderId)
        return when (folder?.kind) {
            FolderKind.USER, FolderKind.NOTES, FolderKind.QUICK_NOTES -> folder.id
            else -> defaultFolderId()
        }
    }

    private fun buildFolderTree(folders: List<FolderEntity>, notes: List<NoteEntity>): List<FolderItem> {
        val active = notes.filter { it.deletedAt == null }
        val deleted = notes.count { it.deletedAt != null }
        val byParent = folders.groupBy { it.parentId }
        fun childrenOf(parentId: String?): List<FolderEntity> =
            byParent[parentId].orEmpty().sortedWith(compareBy<FolderEntity> { it.sortOrder }.thenBy { it.name })

        fun toItem(folder: FolderEntity, depth: Int): FolderItem {
            val count = when (folder.kind) {
                FolderKind.ALL -> active.size
                FolderKind.RECENTLY_DELETED -> deleted
                else -> active.count { it.folderId == folder.id } +
                    childrenOf(folder.id).sumOf { child -> active.count { it.folderId == child.id } }
            }
            val childItems = if (folder.kind == FolderKind.USER || folder.kind == FolderKind.NOTES) {
                childrenOf(folder.id).map { toItem(it, depth + 1) }
            } else {
                emptyList()
            }
            return FolderItem(
                id = folder.id,
                name = folder.name,
                kind = folder.kind,
                parentId = folder.parentId,
                accountId = folder.accountId,
                appleId = folder.appleId,
                noteCount = count,
                depth = depth,
                children = childItems,
            )
        }

        val roots = folders
            .filter { it.parentId == null }
            .sortedWith(compareBy<FolderEntity> { it.sortOrder }.thenBy { it.name })
        return roots.map { toItem(it, 0) }
    }

    private fun sortSummaries(notes: List<NoteSummary>, sort: NoteSort): List<NoteSummary> {
        val comparator = when (sort) {
            NoteSort.DATE_EDITED -> compareByDescending<NoteSummary> { it.pinned }
                .thenByDescending { it.modifiedAt }
            NoteSort.DATE_CREATED -> compareByDescending<NoteSummary> { it.pinned }
                .thenByDescending { it.createdAt }
            NoteSort.TITLE -> compareByDescending<NoteSummary> { it.pinned }
                .thenBy { it.title.lowercase() }
        }
        return notes.sortedWith(comparator)
    }
}

private fun NoteEntity.toSummary(): NoteSummary = NoteSummary(
    id = id,
    folderId = folderId,
    title = title.ifBlank { "New Note" },
    preview = AppleNotesHtml.preview(plaintext, title),
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    pinned = pinned,
    deletedAt = deletedAt,
    appleId = appleId,
    dirty = dirty,
)

private fun noteTargets(all: List<NoteEntity>, exceptId: String): List<AppleNotesHtml.NoteTarget> {
    return all.filter { it.deletedAt == null && it.id != exceptId && it.title.isNotBlank() }
        .map { AppleNotesHtml.NoteTarget(id = it.id, appleId = it.appleId, title = it.title) }
}

private fun NoteEntity.toDetail(
    targets: List<AppleNotesHtml.NoteTarget> = emptyList(),
): NoteDetail = NoteDetail(
    id = id,
    folderId = folderId,
    title = title,
    plaintext = plaintext,
    html = html,
    blocks = AppleNotesHtml.linkRelatedNotes(AppleNotesHtml.decode(html), targets),
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    pinned = pinned,
    deletedAt = deletedAt,
    appleId = appleId,
    passwordProtected = passwordProtected,
    dirty = dirty,
)
