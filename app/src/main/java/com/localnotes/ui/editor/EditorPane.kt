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
    onOpenNote: (String) -> Unit = {},
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
    fun insertMedia(type: BlockType, src: String, mime: String) {
        val item = NoteBlock(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            text = src,
            mime = mime,
        )
        val (next, id) = insertAfter(blocks, focusedId, item)
        focusedId = id
        lastEditAt = System.currentTimeMillis()
        blocks = next
        onBlocks(note.id, next)
    }
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val jpeg = compressJpeg(bytes)
        val src = "data:image/jpeg;base64," + android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        insertMedia(BlockType.IMAGE, src, "image/jpeg")
    }
    val pickAudio = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = readPickedFile(context, uri, "audio/mp4") ?: return@rememberLauncherForActivityResult
        insertMedia(BlockType.AUDIO, picked.first, picked.second)
    }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = readPickedFile(context, uri, "application/octet-stream") ?: return@rememberLauncherForActivityResult
        val mime = picked.second
        if (mime.startsWith("image/")) {
            val jpeg = compressJpeg(android.util.Base64.decode(picked.first.substringAfter("base64,"), android.util.Base64.DEFAULT))
            val src = "data:image/jpeg;base64," + android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
            insertMedia(BlockType.IMAGE, src, "image/jpeg")
        } else if (mime.startsWith("audio/")) {
            insertMedia(BlockType.AUDIO, picked.first, mime)
        } else {
            insertMedia(BlockType.FILE, picked.first, mime)
        }
    }

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
                onOpenLink = { href ->
                    val localTitle = href.removePrefix("notes://").removePrefix(">>").trim()
                    val local = if (href.startsWith("http")) {
                        null
                    } else {
                        blocks.firstOrNull { it.text.equals(localTitle, true) }
                    }
                    when {
                        local != null -> focusedId = local.id
                        href.startsWith("notes://") || href.startsWith("x-coredata:") || href.startsWith(">>") ->
                            onOpenNote(href)
                        else -> runCatching { uriHandler.openUri(href) }
                    }
                },
                onChecklist = {
                    val (next, id) = insertChecklist(blocks, focusedId)
                    focusedId = id
                    commit(next)
                },
                onAlign = { align ->
                    commit(blocks.map { if (it.id == focusedId) it.copy(align = align) else it })
                },
                onColor = { hex ->
                    commit(applyColor(blocks, focusedId, hex, selection.first, selection.second))
                },
                onHighlight = { hex ->
                    commit(applyHighlight(blocks, focusedId, hex, selection.first, selection.second))
                },
                onSize = { size ->
                    commit(applyFontSize(blocks, focusedId, size, selection.first, selection.second))
                },
                onIndent = { delta ->
                    commit(blocks.map {
                        if (it.id == focusedId) it.copy(indent = (it.indent + delta).coerceIn(0, 6)) else it
                    })
                },
                onTable = {
                    val item = NoteBlock(java.util.UUID.randomUUID().toString(), BlockType.TABLE, "", tableRows = listOf(listOf("", ""), listOf("", "")))
                    val (next, id) = insertAfter(blocks, focusedId, item)
                    focusedId = id
                    commit(next)
                },
                onImage = { pickImage.launch("image/*") },
                onAudio = { pickAudio.launch("audio/*") },
                onFile = { pickFile.launch("*/*") },
                onDivider = {
                    val item = NoteBlock(java.util.UUID.randomUUID().toString(), BlockType.DIVIDER, "")
                    val (next, id) = insertAfter(blocks, focusedId, item)
                    focusedId = id
                    commit(next)
                },
                onCollapse = {
                    commit(blocks.map { if (it.id == focusedId) it.copy(collapsed = !it.collapsed) else it })
                },
            )
        }
    }
}

private fun readPickedFile(
    context: android.content.Context,
    uri: android.net.Uri,
    fallbackMime: String,
): Pair<String, String>? {
    val mime = context.contentResolver.getType(uri)?.ifBlank { null } ?: fallbackMime
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
    }.getOrNull()
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > 6 * 1024 * 1024) return null
    val src = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    val labeled = if (!name.isNullOrBlank()) "$mime|$name" else mime
    return src to labeled
}

private fun compressJpeg(bytes: ByteArray): ByteArray {
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val scale = 1280f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
    val scaled = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray()
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
