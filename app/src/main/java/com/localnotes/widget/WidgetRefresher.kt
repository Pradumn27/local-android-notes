package com.localnotes.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun schedule(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            delay(800)
            runCatching { refreshNow(app) }
        }
    }

    suspend fun refreshNow(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(NoteGlanceWidget::class.java).forEach { id ->
            NoteGlanceWidget().update(context, id)
        }
        manager.getGlanceIds(FolderGlanceWidget::class.java).forEach { id ->
            FolderGlanceWidget().update(context, id)
        }
    }
}
