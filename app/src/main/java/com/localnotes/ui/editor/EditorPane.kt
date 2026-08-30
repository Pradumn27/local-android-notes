package com.localnotes.ui.editor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localnotes.data.model.BlockType
import com.localnotes.data.model.FolderKind
import com.localnotes.data.model.NoteBlock
import com.localnotes.data.model.NoteDetail
import com.localnotes.ui.components.EmptyNotesHint
import com.localnotes.ui.components.NotesBackLabel
import com.localnotes.ui.components.NotesIconButton
import com.localnotes.ui.components.NotesPaneHeader
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTypography
import com.localnotes.ui.util.NoteDates

@Composable
fun EditorPane(
    note: NoteDetail?,
    folderName: String,
    folderKind: FolderKind?,
    showBack: Boolean,
    onBack: () -> Unit,
    onNewNote: () -> Unit,
    onBlocks: (String, List<NoteBlock>) -> Unit,
    onPin: (NoteDetail) -> Unit,
    onDelete: (NoteDetail) -> Unit,
    onRestore: (NoteDetail) -> Unit,
    onPermanentDelete: (NoteDetail) -> Unit,
    onMove: (NoteDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    if (note == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.editor),
            contentAlignment = Alignment.Center,
        ) {
            EmptyNotesHint(
                title = "No Note Selected",
                body = "Choose a note from the list, or compose a new one.",
            )
        }
        return
    }

    var blocks by remember(note.id) { mutableStateOf(note.blocks) }
    var focusedId by remember(note.id) { mutableStateOf(note.blocks.firstOrNull()?.id) }
    var showFormat by remember { mutableStateOf(true) }
    var lastEditAt by remember(note.id) { mutableStateOf(0L) }
    var selection by remember(note.id) { mutableStateOf(0 to 0) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = LocalContext.current
    val isTrash = note.deletedAt != null || folderKind == FolderKind.RECENTLY_DELETED

    LaunchedEffect(note.html, note.modifiedAt) {
        if (System.currentTimeMillis() - lastEditAt < 1500) return@LaunchedEffect
        val incoming = note.blocks.joinToString("\n") { "${it.type}|${it.checked}|${it.text}" }
        val local = blocks.joinToString("\n") { "${it.type}|${it.checked}|${it.text}" }
        if (incoming != local) {
            blocks = note.blocks
        }
    }

    fun commit(next: List<NoteBlock>) {
        lastEditAt = System.currentTimeMillis()
        blocks = next
        onBlocks(note.id, next)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.editor)
            .imePadding(),
    ) {
        NotesPaneHeader(
            leading = {
                if (showBack) {
                    NotesBackLabel(folderName, onClick = onBack)
                }
            },
            trailing = {
                NotesIconButton(Icons.Outlined.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, note.title)
                        putExtra(Intent.EXTRA_TEXT, note.plaintext.ifBlank { blocks.joinToString("\n") { it.text } })
                    }
                    context.startActivity(Intent.createChooser(send, "Share Note"))
                }
                NotesIconButton(
                    Icons.Outlined.PushPin,
                    if (note.pinned) "Unpin" else "Pin",
                    onClick = { onPin(note) },
                    tint = if (note.pinned) colors.gold else colors.secondary,
                )
                if (!isTrash) {
                    NotesIconButton(
                        Icons.Outlined.TextFormat,
                        "Format",
                        tint = if (showFormat) colors.gold else colors.secondary,
                        onClick = { showFormat = !showFormat },
                    )
                }
                EditorMenu(
                    isTrash = isTrash,
                    onFormat = { showFormat = !showFormat },
                    onMove = { onMove(note) },
                    onDelete = { onDelete(note) },
                    onRestore = { onRestore(note) },
                    onPermanentDelete = { onPermanentDelete(note) },
                )
                if (!isTrash) {
                    NotesIconButton(Icons.Outlined.EditNote, "New note", onClick = onNewNote)
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                NoteDates.editorLabel(note.modifiedAt),
                style = NotesTypography.bodySmall,
                color = colors.tertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            BlockEditor(
                blocks = blocks,
                readOnly = isTrash,
                focusedId = focusedId,
                onFocused = { id -> if (id != null) focusedId = id },
                onSelection = { start, end -> selection = start to end },
                onChange = { commit(it) },
            )
            Spacer(Modifier.height(24.dp))
        }

        if (showFormat && !isTrash) {
            val focused = blocks.firstOrNull { it.id == focusedId }
            FormatBar(
                current = focused?.type ?: BlockType.BODY,
                linkHref = linkAt(focused, selection.first)?.href,
                onStyle = { type ->
                    commit(applyStyle(blocks, focusedId, type))
                },
                onMark = { mark ->
                    commit(toggleMark(blocks, focusedId, mark, selection.first, selection.second))
                },
                onOpenLink = { href -> runCatching { uriHandler.openUri(href) } },
                onChecklist = {
                    val (next, id) = insertChecklist(blocks, focusedId)
                    focusedId = id
                    commit(next)
                },
            )
        }
    }
}

@Composable
private fun EditorMenu(
    isTrash: Boolean,
    onFormat: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NotesIconButton(Icons.Outlined.MoreHoriz, "More", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isTrash) {
                DropdownMenuItem(text = { Text("Recover") }, onClick = { expanded = false; onRestore() })
                DropdownMenuItem(text = { Text("Delete Forever") }, onClick = { expanded = false; onPermanentDelete() })
            } else {
                DropdownMenuItem(text = { Text("Aa  Format") }, onClick = { expanded = false; onFormat() })
                DropdownMenuItem(
                    text = { Text("Move to Folder") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                    onClick = { expanded = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { expanded = false; onDelete() },
                )
            }
        }
    }
}
