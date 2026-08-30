package com.localnotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localnotes.data.html.AppleNotesHtml
import com.localnotes.data.model.BlockType
import com.localnotes.data.model.FolderKind
import com.localnotes.data.model.NoteBlock
import java.util.UUID

class NotesConverters {
    @TypeConverter
    fun folderKindToString(kind: FolderKind): String = kind.name

    @TypeConverter
    fun stringToFolderKind(value: String): FolderKind = FolderKind.valueOf(value)
}

@Database(
    entities = [AccountEntity::class, FolderEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(NotesConverters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun folders(): FolderDao
    abstract fun notes(): NoteDao

    companion object {
        const val DEFAULT_ACCOUNT_ID = "account-icloud"
        const val DEFAULT_NOTES_FOLDER_ID = "folder-notes"
        const val ALL_FOLDER_ID = "folder-all"
        const val DELETED_FOLDER_ID = "folder-deleted"

        @Volatile
        private var instance: NotesDatabase? = null

        fun get(context: Context): NotesDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes.db",
                )
                    .addCallback(SeedCallback())
                    .build()
                    .also { instance = it }
            }
        }
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO accounts (id, name, appleId, sortOrder)
            VALUES ('${NotesDatabase.DEFAULT_ACCOUNT_ID}', 'iCloud', NULL, 0)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO folders (id, accountId, parentId, name, kind, appleId, sortOrder, dirty)
            VALUES
            ('${NotesDatabase.ALL_FOLDER_ID}', '${NotesDatabase.DEFAULT_ACCOUNT_ID}', NULL, 'All iCloud', 'ALL', NULL, 0, 0),
            ('${NotesDatabase.DEFAULT_NOTES_FOLDER_ID}', '${NotesDatabase.DEFAULT_ACCOUNT_ID}', NULL, 'Notes', 'NOTES', NULL, 1, 0),
            ('${NotesDatabase.DELETED_FOLDER_ID}', '${NotesDatabase.DEFAULT_ACCOUNT_ID}', NULL, 'Recently Deleted', 'RECENTLY_DELETED', NULL, 900, 0)
            """.trimIndent(),
        )

        val welcomeId = UUID.randomUUID().toString()
        val blocks = welcomeBlocks()
        val encoded = AppleNotesHtml.encode(blocks)
        val title = encoded.title.replace("'", "''")
        val plaintext = encoded.plaintext.replace("'", "''")
        val html = encoded.html.replace("'", "''")
        db.execSQL(
            """
            INSERT INTO notes (id, folderId, appleId, title, plaintext, html, createdAt, modifiedAt, pinned, deletedAt, passwordProtected, dirty, lastSyncedAt)
            VALUES ('$welcomeId', '${NotesDatabase.DEFAULT_NOTES_FOLDER_ID}', NULL, '$title', '$plaintext', '$html', $now, $now, 1, NULL, 0, 1, NULL)
            """.trimIndent(),
        )
    }
}

private fun welcomeBlocks(): List<NoteBlock> {
    fun block(type: BlockType, text: String, checked: Boolean = false) = NoteBlock(
        id = UUID.randomUUID().toString(),
        type = type,
        text = text,
        checked = checked,
    )
    return listOf(
        block(BlockType.TITLE, "Welcome to Notes"),
        block(BlockType.BODY, "This is the Android companion to Notes on your Mac. Folders, notes, and the editor are shaped like Apple Notes so they can stay in lockstep once Wi-Fi sync is on."),
        block(BlockType.HEADING, "On this phone"),
        block(BlockType.CHECKLIST, "Write and format notes", checked = false),
        block(BlockType.CHECKLIST, "Pin, search, and file them into folders", checked = false),
        block(BlockType.CHECKLIST, "Deleted notes sit in Recently Deleted", checked = false),
        block(BlockType.HEADING, "Coming next"),
        block(BlockType.BULLET, "A small Mac helper talks to Notes through AppleScript"),
        block(BlockType.BULLET, "Both devices find each other on the same Wi-Fi"),
        block(BlockType.BULLET, "Edits move both ways, using the same HTML Notes already stores"),
        block(BlockType.BODY, "Create a note or a folder to get started. Everything saves as you type."),
    )
}
