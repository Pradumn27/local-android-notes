package com.localnotes

import android.app.Application
import com.localnotes.data.local.NotesDatabase
import com.localnotes.data.repository.NotesRepository
import com.localnotes.sync.LanNotesSyncClient
import com.localnotes.sync.LiveSyncService
import com.localnotes.sync.NotesSyncClient
import com.localnotes.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotesApplication : Application() {
    lateinit var repository: NotesRepository
        private set
    lateinit var syncClient: NotesSyncClient
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val db = NotesDatabase.get(this)
        repository = NotesRepository(db)
        syncClient = LanNotesSyncClient(this, repository)
        syncClient.startAutoSync()
        LiveSyncService.startIfAllowed(this)
        appScope.launch {
            repository.notes.collectLatest {
                WidgetRefresher.schedule(this@NotesApplication)
            }
        }
    }
}
