package com.localnotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localnotes.sync.LiveSyncService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localnotes.ui.NotesApp
import com.localnotes.ui.NotesViewModel
import com.localnotes.ui.theme.NotesTheme
import com.localnotes.widget.WidgetIntents
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val launches = MutableStateFlow(WidgetLaunch(null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launches.value = intent.toWidgetLaunch()
        LiveSyncService.startIfAllowed(this)
        enableEdgeToEdge()
        val app = application as NotesApplication
        setContent {
            NotesTheme {
                val factory = remember {
                    NotesViewModel.factory(app.repository, app.syncClient)
                }
                val viewModel: NotesViewModel = viewModel(factory = factory)
                val launch by launches.collectAsStateWithLifecycle()
                LaunchedEffect(launch) {
                    if (launch.noteId != null || launch.folderId != null) {
                        viewModel.openFromWidget(launch.noteId, launch.folderId)
                    }
                }
                NotesApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launches.value = intent.toWidgetLaunch()
    }
}

private data class WidgetLaunch(
    val noteId: String?,
    val folderId: String?,
    val token: Long = 0L,
)

private fun Intent.toWidgetLaunch(): WidgetLaunch {
    return WidgetLaunch(
        noteId = WidgetIntents.readNoteId(this),
        folderId = WidgetIntents.readFolderId(this),
        token = System.currentTimeMillis(),
    )
}
