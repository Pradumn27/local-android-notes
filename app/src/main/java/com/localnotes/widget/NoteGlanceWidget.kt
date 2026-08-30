package com.localnotes.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.color.ColorProvider
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import com.localnotes.data.html.AppleNotesHtml
import com.localnotes.data.local.NoteEntity
import com.localnotes.data.local.NotesDatabase
import com.localnotes.ui.theme.NotesGold
import com.localnotes.ui.util.NoteDates

class NoteGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = androidx.glance.appwidget.state.getAppWidgetState(
            context,
            PreferencesGlanceStateDefinition,
            id,
        )
        val chosenId = prefs[WidgetKeys.noteId]
        val dao = NotesDatabase.get(context).notes()
        val note = chosenId?.let { dao.getById(it) }?.takeIf { it.deletedAt == null }
            ?: dao.latestActive()
        val lines = note?.let { AppleNotesHtml.displayLines(it.html, it.title, limit = 400) }.orEmpty()

        provideContent {
            GlanceTheme {
                NoteWidgetContent(note, lines)
            }
        }
    }
}

@Composable
private fun NoteWidgetContent(
    note: NoteEntity?,
    lines: List<AppleNotesHtml.DisplayLine>,
) {
    val context = LocalContext.current
    val open = note?.let { WidgetIntents.openNoteAction(context, it.id) }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(day = Color(0xFFFFFFF8), night = Color(0xFF1C1C1E)))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
            .let { base -> if (open != null) base.clickable(onClick = open) else base },
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        val header = GlanceModifier.fillMaxWidth().let { base ->
            if (open != null) {
                base.clickable(onClick = open)
            } else {
                base
            }
        }
        Column(modifier = header) {
            Text(
                text = note?.title?.ifBlank { "New Note" } ?: "No Notes",
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF1C1C1E), night = Color(0xFFF2F2F7)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            if (note != null) {
                Text(
                    text = NoteDates.listLabel(note.modifiedAt),
                    style = TextStyle(
                        color = ColorProvider(NotesGold, Color(0xFFE0C04A)),
                        fontSize = 12.sp,
                    ),
                )
            } else {
                Text(
                    text = "Open Notes to write one.",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)),
                        fontSize = 14.sp,
                    ),
                )
            }
        }
        if (note != null && open != null && lines.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                itemsIndexed(lines, itemId = { index, line -> (index.toLong() shl 16) xor line.text.hashCode().toLong() }) { _, line ->
                    Text(
                        text = line.text,
                        maxLines = 8,
                        style = TextStyle(
                            color = ColorProvider(
                                day = if (line.checked) Color(0xFF8E8E93) else Color(0xFF3A3A3C),
                                night = Color(0xFFC7C7CC),
                            ),
                            fontSize = when (line.type) {
                                com.localnotes.data.model.BlockType.HEADING -> 16.sp
                                com.localnotes.data.model.BlockType.SUBHEADING -> 15.sp
                                else -> 14.sp
                            },
                            fontWeight = when (line.type) {
                                com.localnotes.data.model.BlockType.HEADING,
                                com.localnotes.data.model.BlockType.SUBHEADING,
                                -> FontWeight.Medium
                                else -> FontWeight.Normal
                            },
                        ),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(onClick = open),
                    )
                }
            }
        }
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NoteGlanceWidget()
}
