package com.localnotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.localnotes.NotesApplication
import com.localnotes.data.local.FolderEntity
import com.localnotes.data.local.NoteEntity
import com.localnotes.data.model.FolderKind
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTheme
import com.localnotes.ui.theme.NotesTypography
import com.localnotes.ui.util.NoteDates
import kotlinx.coroutines.launch

class NoteWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val repo = (application as NotesApplication).repository
        setContent {
            NotesTheme {
                val colors = LocalNotesColors.current
                val scope = rememberCoroutineScope()
                var notes by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
                LaunchedEffect(Unit) { notes = repo.activeNotes() }
                PickerScaffold(title = "Choose a Note") {
                    items(notes, key = { it.id }) { note ->
                        PickerRow(
                            title = note.title.ifBlank { "New Note" },
                            subtitle = NoteDates.listLabel(note.modifiedAt),
                            onClick = {
                                scope.launch {
                                    bindNoteWidget(this@NoteWidgetConfigActivity, widgetId, note.id)
                                    finishOk(widgetId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

class FolderWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val repo = (application as NotesApplication).repository
        setContent {
            NotesTheme {
                val scope = rememberCoroutineScope()
                var folders by remember { mutableStateOf<List<FolderEntity>>(emptyList()) }
                LaunchedEffect(Unit) {
                    folders = repo.allFolders().filter { it.kind != FolderKind.RECENTLY_DELETED }
                }
                PickerScaffold(title = "Choose a Folder") {
                    items(folders, key = { it.id }) { folder ->
                        PickerRow(
                            title = folder.name,
                            subtitle = if (folder.kind == FolderKind.ALL) "All notes" else "Folder",
                            onClick = {
                                scope.launch {
                                    bindFolderWidget(this@FolderWidgetConfigActivity, widgetId, folder.id)
                                    finishOk(widgetId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerScaffold(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val colors = LocalNotesColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.list)
            .safeDrawingPadding(),
    ) {
        Text(
            title,
            style = NotesTypography.headlineMedium,
            color = colors.label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalNotesColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = NotesTypography.titleSmall, color = colors.label)
        Text(subtitle, style = NotesTypography.bodySmall, color = colors.gold)
    }
}

private suspend fun bindNoteWidget(activity: Activity, widgetId: Int, noteId: String) {
    val manager = GlanceAppWidgetManager(activity)
    val glanceId = manager.getGlanceIdBy(widgetId)
    updateAppWidgetState(activity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
        prefs.toMutablePreferences().apply { this[WidgetKeys.noteId] = noteId }
    }
    NoteGlanceWidget().update(activity, glanceId)
}

private suspend fun bindFolderWidget(activity: Activity, widgetId: Int, folderId: String) {
    val manager = GlanceAppWidgetManager(activity)
    val glanceId = manager.getGlanceIdBy(widgetId)
    updateAppWidgetState(activity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
        prefs.toMutablePreferences().apply { this[WidgetKeys.folderId] = folderId }
    }
    FolderGlanceWidget().update(activity, glanceId)
}

private fun Activity.finishOk(widgetId: Int) {
    setResult(
        Activity.RESULT_OK,
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
    )
    finish()
}
