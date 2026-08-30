package com.localnotes.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localnotes.data.model.FolderItem
import com.localnotes.data.model.FolderKind
import com.localnotes.data.model.NoteSort
import com.localnotes.data.model.NoteSummary
import com.localnotes.data.model.NotesViewMode
import com.localnotes.ui.components.EmptyNotesHint
import com.localnotes.ui.components.NotesBackLabel
import com.localnotes.ui.components.NotesIconButton
import com.localnotes.ui.components.NotesPaneHeader
import com.localnotes.ui.components.NotesSearchField
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTypography
import com.localnotes.ui.util.NoteDates

@Composable
fun NotesListPane(
    folder: FolderItem?,
    notes: List<NoteSummary>,
    selectedNoteId: String?,
    search: String,
    sort: NoteSort,
    viewMode: NotesViewMode,
    showBack: Boolean,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onNewNote: () -> Unit,
    onTogglePin: (NoteSummary) -> Unit,
    onDelete: (NoteSummary) -> Unit,
    onRestore: (NoteSummary) -> Unit,
    onPermanentDelete: (NoteSummary) -> Unit,
    onSort: (NoteSort) -> Unit,
    onViewMode: (NotesViewMode) -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    val isTrash = folder?.kind == FolderKind.RECENTLY_DELETED
    val pinned = notes.filter { it.pinned }
    val rest = notes.filter { !it.pinned }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.list),
    ) {
        NotesPaneHeader(
            leading = {
                if (showBack) {
                    NotesBackLabel("Folders", onClick = onBack)
                }
            },
            title = if (showBack) null else folder?.name,
            trailing = {
                if (!isTrash) {
                    NotesIconButton(Icons.Outlined.EditNote, "New note", onClick = onNewNote)
                }
                ViewMenu(
                    sort = sort,
                    viewMode = viewMode,
                    canRename = folder?.kind == FolderKind.USER,
                    onSort = onSort,
                    onViewMode = onViewMode,
                    onRenameFolder = onRenameFolder,
                    onDeleteFolder = onDeleteFolder,
                )
            },
        )
        if (showBack) {
            Text(
                folder?.name ?: "Notes",
                style = NotesTypography.headlineMedium,
                color = colors.label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        NotesSearchField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyNotesHint(
                    title = if (isTrash) "No Recently Deleted Notes" else "No Notes",
                    body = if (isTrash) {
                        "Notes you delete stay here for 30 days."
                    } else {
                        "Tap the compose button to start a note."
                    },
                )
            }
        } else if (viewMode == NotesViewMode.GALLERY) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(notes, key = { it.id }) { note ->
                    GalleryCard(
                        note = note,
                        selected = note.id == selectedNoteId,
                        onClick = { onSelect(note.id) },
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (pinned.isNotEmpty()) {
                    item {
                        SectionLabel("Pinned")
                    }
                    itemsIndexed(pinned, key = { _, n -> n.id }) { index, note ->
                        NoteRow(
                            note = note,
                            selected = note.id == selectedNoteId,
                            isTrash = isTrash,
                            showDivider = index != pinned.lastIndex,
                            onClick = { onSelect(note.id) },
                            onPin = { onTogglePin(note) },
                            onDelete = { onDelete(note) },
                            onRestore = { onRestore(note) },
                            onPermanentDelete = { onPermanentDelete(note) },
                        )
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    if (rest.isNotEmpty()) {
                        item { SectionLabel("Notes") }
                    }
                }
                itemsIndexed(rest, key = { _, n -> n.id }) { index, note ->
                    NoteRow(
                        note = note,
                        selected = note.id == selectedNoteId,
                        isTrash = isTrash,
                        showDivider = index != rest.lastIndex,
                        onClick = { onSelect(note.id) },
                        onPin = { onTogglePin(note) },
                        onDelete = { onDelete(note) },
                        onRestore = { onRestore(note) },
                        onPermanentDelete = { onPermanentDelete(note) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalNotesColors.current
    Text(
        text,
        style = NotesTypography.labelMedium,
        color = colors.secondary,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun NoteRow(
    note: NoteSummary,
    selected: Boolean,
    isTrash: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    val colors = LocalNotesColors.current
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.rowSelected else colors.list)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(12.dp),
                )
            }
            Text(
                note.title,
                style = NotesTypography.titleSmall,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "Note actions",
                    tint = colors.tertiary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (isTrash) {
                        DropdownMenuItem(text = { Text("Recover") }, onClick = { menu = false; onRestore() })
                        DropdownMenuItem(text = { Text("Delete Forever") }, onClick = { menu = false; onPermanentDelete() })
                    } else {
                        DropdownMenuItem(
                            text = { Text(if (note.pinned) "Unpin" else "Pin") },
                            onClick = { menu = false; onPin() },
                        )
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row {
            Text(
                NoteDates.listLabel(note.modifiedAt),
                style = NotesTypography.bodySmall,
                color = colors.gold,
            )
            if (note.preview.isNotBlank()) {
                Text(
                    "  ${note.preview}",
                    style = NotesTypography.bodySmall,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = colors.separator,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun GalleryCard(note: NoteSummary, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalNotesColors.current
    Column(
        modifier = Modifier
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.editor)
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) colors.gold else colors.separator,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            note.title,
            style = NotesTypography.titleSmall,
            color = colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            note.preview,
            style = NotesTypography.bodySmall,
            color = colors.secondary,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            NoteDates.listLabel(note.modifiedAt),
            style = NotesTypography.labelSmall,
            color = colors.gold,
        )
    }
}

@Composable
private fun ViewMenu(
    sort: NoteSort,
    viewMode: NotesViewMode,
    canRename: Boolean,
    onSort: (NoteSort) -> Unit,
    onViewMode: (NotesViewMode) -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NotesIconButton(Icons.Outlined.MoreHoriz, "View options", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("View as List") },
                leadingIcon = { Icon(Icons.Outlined.ViewAgenda, null) },
                onClick = { expanded = false; onViewMode(NotesViewMode.LIST) },
            )
            DropdownMenuItem(
                text = { Text("View as Gallery") },
                leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                onClick = { expanded = false; onViewMode(NotesViewMode.GALLERY) },
            )
            DropdownMenuItem(text = { Text("Sort by Date Edited") }, onClick = { expanded = false; onSort(NoteSort.DATE_EDITED) })
            DropdownMenuItem(text = { Text("Sort by Date Created") }, onClick = { expanded = false; onSort(NoteSort.DATE_CREATED) })
            DropdownMenuItem(text = { Text("Sort by Title") }, onClick = { expanded = false; onSort(NoteSort.TITLE) })
            if (canRename) {
                DropdownMenuItem(text = { Text("Rename Folder") }, onClick = { expanded = false; onRenameFolder() })
                DropdownMenuItem(
                    text = { Text("Delete Folder") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { expanded = false; onDeleteFolder() },
                )
            }
        }
    }
}
