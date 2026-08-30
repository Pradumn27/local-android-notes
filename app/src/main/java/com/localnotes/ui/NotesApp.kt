package com.localnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localnotes.ui.components.NotesTextDialog
import com.localnotes.ui.editor.EditorPane
import com.localnotes.ui.folders.FoldersPane
import com.localnotes.ui.notes.NotesListPane
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTypography

private enum class LayoutSize { COMPACT, MEDIUM, EXPANDED }

@Composable
fun NotesApp(viewModel: NotesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalNotesColors.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.list)
            .safeDrawingPadding(),
    ) {
        val layout = when {
            maxWidth >= 900.dp -> LayoutSize.EXPANDED
            maxWidth >= 600.dp -> LayoutSize.MEDIUM
            else -> LayoutSize.COMPACT
        }

        if (layout == LayoutSize.COMPACT) {
            BackHandler(enabled = state.compactScreen != CompactScreen.FOLDERS) {
                viewModel.onBack()
            }
        }

        when (layout) {
            LayoutSize.EXPANDED -> {
                Row(Modifier.fillMaxSize()) {
                    FoldersPaneSlot(state, viewModel, Modifier.width(240.dp))
                    PaneDivider()
                    NotesListSlot(state, viewModel, showBack = false, Modifier.width(320.dp))
                    PaneDivider()
                    EditorSlot(state, viewModel, showBack = false, Modifier.weight(1f))
                }
            }
            LayoutSize.MEDIUM -> {
                Row(Modifier.fillMaxSize()) {
                    if (state.compactScreen == CompactScreen.FOLDERS) {
                        FoldersPaneSlot(state, viewModel, Modifier.width(260.dp))
                        PaneDivider()
                        NotesListSlot(state, viewModel, showBack = false, Modifier.weight(1f))
                    } else {
                        NotesListSlot(state, viewModel, showBack = true, Modifier.width(320.dp))
                        PaneDivider()
                        EditorSlot(state, viewModel, showBack = false, Modifier.weight(1f))
                    }
                }
            }
            LayoutSize.COMPACT -> {
                when (state.compactScreen) {
                    CompactScreen.FOLDERS -> FoldersPaneSlot(state, viewModel, Modifier.fillMaxSize())
                    CompactScreen.LIST -> NotesListSlot(state, viewModel, showBack = true, Modifier.fillMaxSize())
                    CompactScreen.EDITOR -> EditorSlot(state, viewModel, showBack = true, Modifier.fillMaxSize())
                }
            }
        }

        when (state.dialog) {
            DialogKind.NEW_FOLDER -> DialogScrim(onDismiss = viewModel::dismissDialog) {
                NotesTextDialog(
                    title = "New Folder",
                    confirmLabel = "Save",
                    initial = "",
                    onDismiss = viewModel::dismissDialog,
                    onConfirm = viewModel::createFolder,
                )
            }
            DialogKind.RENAME_FOLDER -> DialogScrim(onDismiss = viewModel::dismissDialog) {
                NotesTextDialog(
                    title = "Rename Folder",
                    confirmLabel = "Rename",
                    initial = state.selectedFolder?.name.orEmpty(),
                    onDismiss = viewModel::dismissDialog,
                    onConfirm = viewModel::renameFolder,
                )
            }
            DialogKind.DELETE_FOLDER -> DialogScrim(onDismiss = viewModel::dismissDialog) {
                ConfirmCard(
                    title = "Delete Folder?",
                    body = "Notes inside will move to Notes.",
                    confirm = "Delete",
                    onDismiss = viewModel::dismissDialog,
                    onConfirm = viewModel::deleteFolder,
                )
            }
            DialogKind.MOVE_NOTE -> DialogScrim(onDismiss = viewModel::dismissDialog) {
                MoveSheet(
                    folders = state.folders.writable(),
                    onDismiss = viewModel::dismissDialog,
                    onPick = { folderId ->
                        state.selectedNoteId?.let { viewModel.moveNote(it, folderId) }
                    },
                )
            }
            DialogKind.CONNECT -> DialogScrim(onDismiss = viewModel::dismissDialog) {
                ConnectCard(
                    sync = state.sync,
                    onDismiss = viewModel::dismissDialog,
                    onPair = viewModel::pairWithPeer,
                    onManual = viewModel::pairManual,
                    onSyncNow = viewModel::syncNow,
                    onLiveWidgets = viewModel::setLiveWidgetsOptIn,
                )
            }
            DialogKind.NONE -> Unit
        }
    }
}

@Composable
private fun FoldersPaneSlot(
    state: NotesUiState,
    viewModel: NotesViewModel,
    modifier: Modifier,
) {
    FoldersPane(
        folders = state.folders,
        selectedFolderId = state.selectedFolderId,
        onSelect = viewModel::selectFolder,
        onNewFolder = { viewModel.openDialog(DialogKind.NEW_FOLDER) },
        onOpenSettings = { viewModel.openDialog(DialogKind.CONNECT) },
        liveLabel = when {
            state.sync.live -> "Live"
            state.sync.paired -> "Waiting"
            else -> null
        },
        modifier = modifier,
    )
}

@Composable
private fun NotesListSlot(
    state: NotesUiState,
    viewModel: NotesViewModel,
    showBack: Boolean,
    modifier: Modifier,
) {
    NotesListPane(
        folder = state.selectedFolder,
        notes = state.notes,
        selectedNoteId = state.selectedNoteId,
        search = state.search,
        sort = state.sort,
        viewMode = state.viewMode,
        showBack = showBack,
        onBack = viewModel::showFolders,
        onSearch = viewModel::onSearch,
        onSelect = viewModel::selectNote,
        onNewNote = viewModel::createNote,
        onTogglePin = { viewModel.togglePin(it.id, it.pinned) },
        onDelete = { viewModel.deleteNote(it.id) },
        onRestore = { viewModel.restoreNote(it.id) },
        onPermanentDelete = { viewModel.permanentlyDelete(it.id) },
        onSort = viewModel::setSort,
        onViewMode = viewModel::setViewMode,
        onRenameFolder = { viewModel.openDialog(DialogKind.RENAME_FOLDER) },
        onDeleteFolder = { viewModel.openDialog(DialogKind.DELETE_FOLDER) },
        modifier = modifier,
    )
}

@Composable
private fun EditorSlot(
    state: NotesUiState,
    viewModel: NotesViewModel,
    showBack: Boolean,
    modifier: Modifier,
) {
    EditorPane(
        note = state.selectedNote,
        folderName = state.selectedFolder?.name ?: "Notes",
        folderKind = state.selectedFolder?.kind,
        showBack = showBack,
        onBack = viewModel::showList,
        onNewNote = viewModel::createNote,
        onBlocks = viewModel::saveBlocks,
        onPin = { viewModel.togglePin(it.id, it.pinned) },
        onDelete = { viewModel.deleteNote(it.id) },
        onRestore = { viewModel.restoreNote(it.id) },
        onPermanentDelete = { viewModel.permanentlyDelete(it.id) },
        onMove = { viewModel.openDialog(DialogKind.MOVE_NOTE) },
        onOpenNote = viewModel::openLinkedNote,
        modifier = modifier,
    )
}

@Composable
private fun PaneDivider() {
    val colors = LocalNotesColors.current
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(0.6.dp)
            .background(colors.separator),
    )
}

@Composable
private fun DialogScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalNotesColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clickable(enabled = false, onClick = {}),
        ) {
            content()
        }
    }
}

@Composable
private fun ConfirmCard(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalNotesColors.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.list)
            .padding(20.dp),
    ) {
        Text(title, style = NotesTypography.titleMedium, color = colors.label)
        Spacer(Modifier.height(8.dp))
        Text(body, style = NotesTypography.bodyMedium, color = colors.secondary)
        Spacer(Modifier.height(18.dp))
        RowButtons(cancel = onDismiss, confirmLabel = confirm, destructive = true, onConfirm = onConfirm)
    }
}

@Composable
private fun MoveSheet(
    folders: List<com.localnotes.data.model.FolderItem>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = LocalNotesColors.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.list)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Move to Folder", style = NotesTypography.titleMedium, color = colors.label)
        Spacer(Modifier.height(12.dp))
        folders.forEach { folder ->
            Text(
                folder.name,
                style = NotesTypography.bodyLarge,
                color = colors.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(folder.id) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Cancel",
            color = colors.secondary,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onDismiss)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ConnectCard(
    sync: com.localnotes.sync.SyncStatus,
    onDismiss: () -> Unit,
    onPair: (host: String, port: Int, name: String, pin: String) -> Unit,
    onManual: (host: String, port: Int, pin: String) -> Unit,
    onSyncNow: () -> Unit,
    onLiveWidgets: (Boolean) -> Unit,
) {
    val colors = LocalNotesColors.current
    var pin by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf(sync.connectedPeer?.host.orEmpty()) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.list)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Sync with Mac", style = NotesTypography.titleMedium, color = colors.label)
        Spacer(Modifier.height(8.dp))
        Text(
            "On the Mac, run the helper and use the PIN it prints — or pick your own with --pin. Type that PIN here once. After that, same Wi-Fi syncs while the app is open.",
            style = NotesTypography.bodyMedium,
            color = colors.secondary,
        )
        Spacer(Modifier.height(14.dp))
        when {
            sync.live -> Text(
                "Live with ${sync.connectedPeer?.name ?: "Mac"} — edits move as you type.",
                color = colors.gold,
                style = NotesTypography.bodyMedium,
            )
            sync.syncing || sync.pairing -> Text(sync.progress ?: "Working…", color = colors.gold, style = NotesTypography.bodyMedium)
            sync.lastError != null -> Text(sync.lastError, color = colors.destructive, style = NotesTypography.bodyMedium)
            sync.paired -> Text("Waiting for the Mac on this Wi-Fi…", color = colors.secondary, style = NotesTypography.bodyMedium)
            sync.scanning -> Text("Looking for your Mac…", color = colors.secondary, style = NotesTypography.bodyMedium)
            else -> Text("If nothing appears, type the Mac address below.", color = colors.secondary, style = NotesTypography.bodySmall)
        }
        if (sync.peers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Nearby", style = NotesTypography.labelMedium, color = colors.secondary)
            sync.peers.forEach { peer ->
                Text(
                    "${peer.name}  ·  ${peer.host}",
                    style = NotesTypography.bodyLarge,
                    color = colors.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { manualHost = peer.host }
                        .padding(vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val notifyLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) onLiveWidgets(true)
        }
        Text(
            if (sync.liveWidgetsOptedIn) "Live widgets are on. A silent notification keeps the listener running so the home-screen widget updates while Notes is closed."
            else "Widgets only update live in the background if you allow notifications. This is off until you turn it on.",
            style = NotesTypography.bodySmall,
            color = colors.secondary,
        )
        Text(
            if (sync.liveWidgetsOptedIn) "Turn off live widgets" else "Enable live widgets",
            color = colors.gold,
            style = NotesTypography.titleSmall,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable {
                    if (sync.liveWidgetsOptedIn) {
                        onLiveWidgets(false)
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onLiveWidgets(true)
                    }
                }
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        ConnectField(value = manualHost, onValueChange = { manualHost = it }, placeholder = "Mac address, e.g. 192.168.1.102")
        Spacer(Modifier.height(8.dp))
        ConnectField(value = pin, onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) }, placeholder = "6-digit PIN")
        Spacer(Modifier.height(16.dp))
        val busy = sync.syncing || sync.pairing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            Text(
                "Close",
                color = colors.secondary,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (sync.paired) {
                Text(
                    if (busy) "Syncing…" else "Sync Now",
                    color = if (busy) colors.tertiary else colors.gold,
                    style = NotesTypography.titleSmall,
                    modifier = Modifier
                        .clickable(enabled = !busy, onClick = onSyncNow)
                        .padding(8.dp),
                )
            }
            Text(
                if (sync.paired) "Re-pair" else "Connect",
                color = if (busy || pin.length < 6) colors.tertiary else colors.gold,
                style = NotesTypography.titleSmall,
                modifier = Modifier
                    .clickable(enabled = !busy && pin.length == 6 && manualHost.isNotBlank()) {
                        val peer = sync.peers.find { it.host == manualHost.trim() }
                        if (peer != null) {
                            onPair(peer.host, peer.port, peer.name, pin)
                        } else {
                            onManual(manualHost, 18765, pin)
                        }
                    }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun ConnectField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = LocalNotesColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.search)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.secondary, style = NotesTypography.bodyMedium)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = NotesTypography.bodyMedium.copy(color = colors.label),
            cursorBrush = SolidColor(colors.gold),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RowButtons(
    cancel: () -> Unit,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
) {
    val colors = LocalNotesColors.current
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
    ) {
        Text(
            "Cancel",
            color = colors.secondary,
            modifier = Modifier
                .clickable(onClick = cancel)
                .padding(8.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            confirmLabel,
            color = if (destructive) colors.destructive else colors.gold,
            modifier = Modifier
                .clickable(onClick = onConfirm)
                .padding(8.dp),
        )
    }
}
