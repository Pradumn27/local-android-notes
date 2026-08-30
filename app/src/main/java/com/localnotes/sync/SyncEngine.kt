package com.localnotes.sync

import com.localnotes.data.model.FolderKind
import com.localnotes.data.repository.NotesRepository

class SyncEngine(
    private val repository: NotesRepository,
    private val api: MacNotesApi,
    private val onProgress: (String) -> Unit = {},
    private val skipNoteIds: Set<String> = emptySet(),
    private val skipAppleIds: Set<String> = emptySet(),
) {
    suspend fun pushNote(noteId: String): String? {
        val note = repository.noteById(noteId) ?: return null
        return pushOne(note)
    }

    suspend fun run(): SyncReport {
        onProgress("Reading Notes on your Mac…")
        val catalog = api.catalog()
        var folderCount = 0
        val folderByAppleId = mutableMapOf<String, String>()

        catalog.folders.forEach { remote ->
            val local = repository.upsertRemoteFolder(remote.name, remote.appleId)
            folderByAppleId[remote.appleId] = local.id
            folderCount += 1
        }

        repository.allFolders()
            .filter { it.dirty && it.appleId == null && it.kind == FolderKind.USER }
            .forEach { folder ->
                val appleId = api.createFolder(folder.name)
                repository.markFolderClean(folder.id, appleId)
                folderByAppleId[appleId] = folder.id
                folderCount += 1
            }

        val defaultFolderId = repository.defaultFolderId()
        var pushed = 0
        var deleted = 0

        onProgress("Sending local edits…")
        repository.dirtyNotes().forEach { note ->
            val appleId = pushOne(note)
            if (note.deletedAt != null) deleted += 1 else pushed += 1
            appleId
        }

        onProgress("Bringing Mac notes over…")
        var pulled = 0
        val remoteIds = catalog.notes.map { it.appleId }.toSet()
        catalog.notes.forEach { meta ->
            val local = repository.noteByAppleId(meta.appleId)
            val folderId = meta.folderAppleId?.let { folderByAppleId[it] } ?: defaultFolderId
            val shouldPull = when {
                meta.appleId in skipAppleIds -> false
                local?.id in skipNoteIds -> false
                local == null -> true
                local.dirty -> false
                meta.modifiedAt > local.modifiedAt + 1200 -> true
                local.html.isBlank() && !meta.passwordProtected -> true
                else -> false
            }
            if (!shouldPull) return@forEach
            val full = api.getNote(meta.appleId)
            repository.applyRemoteNote(
                appleId = full.appleId,
                folderId = folderId,
                title = full.title,
                plaintext = full.plaintext,
                html = full.html,
                createdAt = full.createdAt,
                modifiedAt = full.modifiedAt,
                passwordProtected = full.passwordProtected,
            )
            pulled += 1
        }

        repository.allNotes().forEach { local ->
            val appleId = local.appleId ?: return@forEach
            if (!local.dirty && local.deletedAt == null && appleId !in remoteIds) {
                repository.markNoteDeletedFromRemote(local.id)
                deleted += 1
            }
        }

        return SyncReport(pulled = pulled, pushed = pushed, deleted = deleted, folders = folderCount)
    }

    private suspend fun pushOne(note: com.localnotes.data.local.NoteEntity): String? {
        val folder = repository.folderById(note.folderId)
        val folderAppleId = folder?.appleId
        if (note.deletedAt != null) {
            if (note.appleId != null) {
                runCatching { api.deleteNote(note.appleId) }
            }
            repository.markNoteSynced(note.id, note.appleId)
            return note.appleId
        }
        val (appleId, modifiedAt) = api.upsertNote(note.appleId, folderAppleId, note.html)
        repository.markNoteSynced(note.id, appleId, modifiedAt.takeIf { it > 0 })
        return appleId
    }
}
