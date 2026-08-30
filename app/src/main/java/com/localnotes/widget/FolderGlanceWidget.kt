package com.localnotes.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.localnotes.data.local.NoteEntity
import com.localnotes.data.local.NotesDatabase
import com.localnotes.data.model.FolderKind
import com.localnotes.ui.theme.NotesGold
import com.localnotes.ui.util.NoteDates

class FolderGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = androidx.glance.appwidget.state.getAppWidgetState(
            context,
            PreferencesGlanceStateDefinition,
            id,
        )
        val db = NotesDatabase.get(context)
        val chosenId = prefs[WidgetKeys.folderId]
        val folders = db.folders().getAll()
        val folder = chosenId?.let { id0 -> folders.find { it.id == id0 } }
            ?: folders.find { it.kind == FolderKind.NOTES }
            ?: folders.firstOrNull()
        val allNotes = db.notes().getActive()
        val notes = when (folder?.kind) {
            FolderKind.ALL, null -> allNotes
            FolderKind.RECENTLY_DELETED -> db.notes().getAll().filter { it.deletedAt != null }
            else -> allNotes.filter { it.folderId == folder.id }
        }.sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.modifiedAt })
            .take(8)

        provideContent {
            GlanceTheme {
                FolderWidgetContent(
                    folderName = folder?.name ?: "Notes",
                    folderId = folder?.id,
                    count = notes.size,
                    notes = notes,
                )
            }
        }
    }
}

@Composable
private fun FolderWidgetContent(
    folderName: String,
    folderId: String?,
    count: Int,
    notes: List<NoteEntity>,
) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(day = Color(0xFFF4F4F7), night = Color(0xFF1C1C1E)))
            .padding(top = 14.dp, start = 14.dp, end = 14.dp, bottom = 8.dp),
    ) {
        val header = GlanceModifier.fillMaxWidth().let { base ->
            if (folderId != null) {
                base.clickable(onClick = actionStartActivity(WidgetIntents.openFolder(context, folderId)))
            } else {
                base
            }
        }
        Column(modifier = header) {
            Text(
                text = folderName,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF1C1C1E), night = Color(0xFFF2F2F7)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = if (count == 1) "1 note" else "$count notes",
                style = TextStyle(
                    color = ColorProvider(NotesGold, Color(0xFFE0C04A)),
                    fontSize = 12.sp,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (notes.isEmpty()) {
            Text(
                text = "No notes in this folder",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)),
                    fontSize = 14.sp,
                ),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(notes, itemId = { it.id.hashCode().toLong() }) { note ->
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable(onClick = actionStartActivity(WidgetIntents.openNote(context, note.id))),
                    ) {
                        Text(
                            text = note.title.ifBlank { "New Note" },
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF1C1C1E), night = Color(0xFFF2F2F7)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Text(
                            text = NoteDates.listLabel(note.modifiedAt),
                            style = TextStyle(
                                color = ColorProvider(NotesGold, Color(0xFFE0C04A)),
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

class FolderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FolderGlanceWidget()
}
