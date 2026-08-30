package com.localnotes.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localnotes.data.model.FolderItem
import com.localnotes.data.model.FolderKind
import com.localnotes.ui.components.NotesIconButton
import com.localnotes.ui.components.NotesSearchField
import com.localnotes.ui.flatten
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTypography

@Composable
fun FoldersPane(
    folders: List<FolderItem>,
    selectedFolderId: String,
    onSelect: (String) -> Unit,
    onNewFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    liveLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    var search by remember { mutableStateOf("") }
    val visible = folders.flatMap { it.flatten() }.filter { folder ->
        search.isBlank() || folder.name.contains(search, ignoreCase = true)
    }
    val icloud = visible.filter { it.kind != FolderKind.RECENTLY_DELETED }
    val deleted = visible.filter { it.kind == FolderKind.RECENTLY_DELETED }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.sidebar),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Folders",
                style = NotesTypography.headlineMedium,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
            if (liveLabel != null) {
                Text(
                    liveLabel,
                    style = NotesTypography.labelMedium,
                    color = colors.gold,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            NotesIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "Mac sync",
                onClick = onOpenSettings,
            )
        }
        NotesSearchField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            item {
                Text(
                    "ICLOUD",
                    style = NotesTypography.labelMedium,
                    color = colors.secondary,
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp),
                )
            }
            items(icloud, key = { it.id }) { folder ->
                FolderRow(
                    folder = folder,
                    selected = folder.id == selectedFolderId,
                    onClick = { onSelect(folder.id) },
                )
            }
            if (deleted.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                items(deleted, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        selected = folder.id == selectedFolderId,
                        onClick = { onSelect(folder.id) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNewFolder)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CreateNewFolder,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("New Folder", color = colors.gold, style = NotesTypography.bodyLarge)
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalNotesColors.current
    val icon = folderIcon(folder.kind)
    val tint = if (folder.kind == FolderKind.RECENTLY_DELETED) colors.secondary else colors.folder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.sidebarSelected else colors.sidebar.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(start = (8 + folder.depth * 16).dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            folder.name,
            style = NotesTypography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            folder.noteCount.toString(),
            style = NotesTypography.bodySmall,
            color = colors.secondary,
        )
    }
}

private fun folderIcon(kind: FolderKind): ImageVector = when (kind) {
    FolderKind.ALL -> Icons.Outlined.Inbox
    FolderKind.RECENTLY_DELETED -> Icons.Outlined.DeleteOutline
    else -> Icons.Outlined.Folder
}
