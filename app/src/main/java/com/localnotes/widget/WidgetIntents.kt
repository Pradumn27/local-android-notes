package com.localnotes.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import com.localnotes.MainActivity

object WidgetKeys {
    val noteId = stringPreferencesKey("note_id")
    val folderId = stringPreferencesKey("folder_id")
}

object WidgetIntents {
    const val EXTRA_NOTE_ID = "com.localnotes.extra.NOTE_ID"
    const val EXTRA_FOLDER_ID = "com.localnotes.extra.FOLDER_ID"
    const val ACTION_OPEN = "com.localnotes.action.OPEN"
    const val SCHEME = "localnotes"

    val NoteIdKey = ActionParameters.Key<String>(EXTRA_NOTE_ID)
    val FolderIdKey = ActionParameters.Key<String>(EXTRA_FOLDER_ID)

    fun openNote(context: Context, noteId: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("$SCHEME://note/${Uri.encode(noteId)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_NOTE_ID, noteId)
        }
    }

    fun openFolder(context: Context, folderId: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("$SCHEME://folder/${Uri.encode(folderId)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_FOLDER_ID, folderId)
        }
    }

    fun openNoteAction(context: Context, noteId: String): Action {
        return actionStartActivity(
            openNote(context, noteId),
            actionParametersOf(NoteIdKey to noteId),
        )
    }

    fun openFolderAction(context: Context, folderId: String): Action {
        return actionStartActivity(
            openFolder(context, folderId),
            actionParametersOf(FolderIdKey to folderId),
        )
    }

    fun readNoteId(intent: Intent): String? {
        intent.getStringExtra(EXTRA_NOTE_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val data = intent.data ?: return null
        if (data.scheme == SCHEME && data.host == "note") {
            return data.lastPathSegment?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
        }
        return null
    }

    fun readFolderId(intent: Intent): String? {
        intent.getStringExtra(EXTRA_FOLDER_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val data = intent.data ?: return null
        if (data.scheme == SCHEME && data.host == "folder") {
            return data.lastPathSegment?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
