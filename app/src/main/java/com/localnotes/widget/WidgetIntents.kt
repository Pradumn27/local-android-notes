package com.localnotes.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.stringPreferencesKey
import com.localnotes.MainActivity

object WidgetKeys {
    val noteId = stringPreferencesKey("note_id")
    val folderId = stringPreferencesKey("folder_id")
}

object WidgetIntents {
    const val EXTRA_NOTE_ID = "com.localnotes.extra.NOTE_ID"
    const val EXTRA_FOLDER_ID = "com.localnotes.extra.FOLDER_ID"
    const val ACTION_OPEN = "com.localnotes.action.OPEN"

    fun openNote(context: Context, noteId: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTE_ID, noteId)
        }
    }

    fun openFolder(context: Context, folderId: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FOLDER_ID, folderId)
        }
    }
}
