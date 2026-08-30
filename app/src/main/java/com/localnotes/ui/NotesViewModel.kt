package com.localnotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localnotes.data.local.NotesDatabase
import com.localnotes.data.model.FolderItem
import com.localnotes.data.model.FolderKind
import com.localnotes.data.model.NoteBlock
import com.localnotes.data.model.NoteDetail
import com.localnotes.data.model.NoteSort
import com.localnotes.data.model.NoteSummary
import com.localnotes.data.model.NotesViewMode
import com.localnotes.data.repository.NotesRepository
import com.localnotes.sync.NotesSyncClient
import com.localnotes.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CompactScreen { FOLDERS, LIST, EDITOR }

enum class DialogKind { NONE, NEW_FOLDER, RENAME_FOLDER, MOVE_NOTE, DELETE_FOLDER, CONNECT }

data class NotesUiState(
    val folders: List<FolderItem> = emptyList(),
    val selectedFolderId: String = NotesDatabase.DEFAULT_NOTES_FOLDER_ID,
    val selectedFolder: FolderItem? = null,
    val notes: List<NoteSummary> = emptyList(),
    val selectedNoteId: String? = null,
    val selectedNote: NoteDetail? = null,
    val search: String = "",
    val sort: NoteSort = NoteSort.DATE_EDITED,
    val viewMode: NotesViewMode = NotesViewMode.LIST,
    val compactScreen: CompactScreen = CompactScreen.LIST,
    val dialog: DialogKind = DialogKind.NONE,
    val sync: SyncStatus = SyncStatus(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NotesRepository,
    private val syncClient: NotesSyncClient,
) : ViewModel() {

    private val selectedFolderId = MutableStateFlow(NotesDatabase.DEFAULT_NOTES_FOLDER_ID)
    private val selectedNoteId = MutableStateFlow<String?>(null)
    private val search = MutableStateFlow("")
    private val sort = MutableStateFlow(NoteSort.DATE_EDITED)
    private val viewMode = MutableStateFlow(NotesViewMode.LIST)
    private val compactScreen = MutableStateFlow(CompactScreen.LIST)
    private val dialog = MutableStateFlow(DialogKind.NONE)

    private val summaries = combine(selectedFolderId, search, sort) { folderId, query, noteSort ->
        Triple(folderId, query, noteSort)
    }.flatMapLatest { (folderId, query, noteSort) ->
        repository.observeSummaries(folderId, query, noteSort)
    }

    private val openNote = selectedNoteId.flatMapLatest { id ->
        if (id == null) {
            MutableStateFlow(null)
        } else {
            repository.observeNote(id)
        }
    }

    val uiState: StateFlow<NotesUiState> = combine(
        combine(repository.folderTree, selectedFolderId, summaries, selectedNoteId, openNote) {
                folders, folderId, notes, noteId, note ->
            CoreSlice(folders, folderId, notes, noteId, note)
        },
        combine(search, sort, viewMode, compactScreen, dialog) { query, noteSort, mode, screen, dialogKind ->
            ChromeSlice(query, noteSort, mode, screen, dialogKind)
        },
        syncClient.status,
    ) { core, chrome, sync ->
        NotesUiState(
            folders = core.folders,
            selectedFolderId = core.folderId,
            selectedFolder = findFolder(core.folders, core.folderId),
            notes = core.notes,
            selectedNoteId = core.noteId,
            selectedNote = core.note,
            search = chrome.search,
            sort = chrome.sort,
            viewMode = chrome.viewMode,
            compactScreen = chrome.screen,
            dialog = chrome.dialog,
            sync = sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    private var saveJob: Job? = null

    init {
        syncClient.startAutoSync()
        viewModelScope.launch { repository.purgeExpiredDeleted() }
    }

    fun selectFolder(id: String) {
        selectedFolderId.value = id
        selectedNoteId.value = null
        compactScreen.value = CompactScreen.LIST
    }

    fun selectNote(id: String) {
        selectedNoteId.value = id
        compactScreen.value = CompactScreen.EDITOR
    }

    fun openLinkedNote(href: String) {
        viewModelScope.launch {
            val note = repository.findNoteForLink(href) ?: return@launch
            selectedFolderId.value = note.folderId
            selectedNoteId.value = note.id
            compactScreen.value = CompactScreen.EDITOR
        }
    }

    fun openFromWidget(noteId: String?, folderId: String?) {
        viewModelScope.launch {
            if (noteId != null) {
                val note = repository.noteById(noteId) ?: return@launch
                selectedFolderId.value = note.folderId
                selectedNoteId.value = note.id
                compactScreen.value = CompactScreen.EDITOR
                return@launch
            }
            if (folderId != null) {
                selectedFolderId.value = folderId
                selectedNoteId.value = null
                compactScreen.value = CompactScreen.LIST
            }
        }
    }

    fun closeNote() {
        selectedNoteId.value = null
        compactScreen.value = CompactScreen.LIST
    }

    fun showFolders() {
        compactScreen.value = CompactScreen.FOLDERS
    }

    fun showList() {
        compactScreen.value = CompactScreen.LIST
        selectedNoteId.value = null
    }

    fun onSearch(value: String) {
        search.value = value
    }

    fun setSort(value: NoteSort) {
        sort.value = value
    }

    fun setViewMode(value: NotesViewMode) {
        viewMode.value = value
    }

    fun openDialog(kind: DialogKind) {
        dialog.value = kind
        if (kind == DialogKind.CONNECT) {
            viewModelScope.launch { syncClient.startListening() }
        }
    }

    fun dismissDialog() {
        dialog.value = DialogKind.NONE
    }

    fun pairWithPeer(host: String, port: Int, name: String, pin: String) {
        viewModelScope.launch {
            runCatching {
                syncClient.pair(com.localnotes.sync.SyncPeer(name, host, port), pin)
            }
        }
    }

    fun pairManual(host: String, port: Int, pin: String) {
        viewModelScope.launch {
            runCatching { syncClient.pairManual(host, port, pin) }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            runCatching { syncClient.syncNow() }
        }
    }

    fun setLiveWidgetsOptIn(enabled: Boolean) {
        syncClient.setLiveWidgetsOptIn(enabled)
    }

    fun createNote() {
        viewModelScope.launch {
            val note = repository.createNote(selectedFolderId.value)
            selectedNoteId.value = note.id
            compactScreen.value = CompactScreen.EDITOR
            syncClient.notifyLocalChange(note.id)
        }
    }

    fun saveBlocks(noteId: String, blocks: List<NoteBlock>) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            repository.saveBlocks(noteId, blocks)
            syncClient.notifyLocalChange(noteId)
        }
    }

    fun togglePin(noteId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(noteId, !pinned)
            syncClient.notifyLocalChange(noteId)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            syncClient.notifyLocalChange(noteId)
            if (selectedNoteId.value == noteId) {
                selectedNoteId.value = null
                compactScreen.value = CompactScreen.LIST
            }
        }
    }

    fun restoreNote(noteId: String) {
        viewModelScope.launch {
            repository.restoreNote(noteId)
            syncClient.notifyLocalChange(noteId)
        }
    }

    fun permanentlyDelete(noteId: String) {
        viewModelScope.launch {
            repository.permanentlyDelete(noteId)
            if (selectedNoteId.value == noteId) {
                selectedNoteId.value = null
                compactScreen.value = CompactScreen.LIST
            }
        }
    }

    fun moveNote(noteId: String, folderId: String) {
        viewModelScope.launch {
            repository.moveNote(noteId, folderId)
            syncClient.notifyLocalChange(noteId)
            dismissDialog()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val folder = repository.createFolder(name)
            selectedFolderId.value = folder.id
            syncClient.notifyLocalChange()
            dismissDialog()
        }
    }

    fun renameFolder(name: String) {
        viewModelScope.launch {
            repository.renameFolder(selectedFolderId.value, name)
            syncClient.notifyLocalChange()
            dismissDialog()
        }
    }

    fun deleteFolder() {
        viewModelScope.launch {
            val id = selectedFolderId.value
            repository.deleteFolder(id)
            selectedFolderId.value = NotesDatabase.DEFAULT_NOTES_FOLDER_ID
            syncClient.notifyLocalChange()
            dismissDialog()
        }
    }

    fun onBack(): Boolean {
        return when (compactScreen.value) {
            CompactScreen.EDITOR -> {
                showList()
                true
            }
            CompactScreen.LIST -> {
                showFolders()
                true
            }
            CompactScreen.FOLDERS -> false
        }
    }

    companion object {
        fun factory(repository: NotesRepository, syncClient: NotesSyncClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NotesViewModel(repository, syncClient) as T
                }
            }
    }
}

private data class CoreSlice(
    val folders: List<FolderItem>,
    val folderId: String,
    val notes: List<NoteSummary>,
    val noteId: String?,
    val note: NoteDetail?,
)

private data class ChromeSlice(
    val search: String,
    val sort: NoteSort,
    val viewMode: NotesViewMode,
    val screen: CompactScreen,
    val dialog: DialogKind,
)

private fun findFolder(folders: List<FolderItem>, id: String): FolderItem? {
    folders.forEach { folder ->
        if (folder.id == id) return folder
        findFolder(folder.children, id)?.let { return it }
    }
    return null
}

fun FolderItem.flatten(): List<FolderItem> = buildList {
    add(this@flatten)
    children.forEach { addAll(it.flatten()) }
}

fun List<FolderItem>.writable(): List<FolderItem> =
    flatMap { it.flatten() }.filter { it.kind == FolderKind.USER || it.kind == FolderKind.NOTES }
