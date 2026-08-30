package com.localnotes.data.model

enum class FolderKind {
    ALL,
    NOTES,
    USER,
    QUICK_NOTES,
    RECENTLY_DELETED,
}

enum class NoteSort {
    DATE_EDITED,
    DATE_CREATED,
    TITLE,
}

enum class NotesViewMode {
    LIST,
    GALLERY,
}

enum class BlockType {
    TITLE,
    HEADING,
    SUBHEADING,
    BODY,
    MONO,
    BULLET,
    NUMBERED,
    CHECKLIST,
}

enum class MarkStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKE,
    HIGHLIGHT,
}

data class TextMark(
    val start: Int,
    val end: Int,
    val style: MarkStyle,
)

data class NoteBlock(
    val id: String,
    val type: BlockType,
    val text: String,
    val checked: Boolean = false,
    val indent: Int = 0,
    val marks: List<TextMark> = emptyList(),
) {
    fun isBlank(): Boolean = text.isBlank() && type != BlockType.CHECKLIST
}

data class FolderItem(
    val id: String,
    val name: String,
    val kind: FolderKind,
    val parentId: String?,
    val accountId: String,
    val appleId: String?,
    val noteCount: Int,
    val depth: Int,
    val children: List<FolderItem> = emptyList(),
)

data class NoteSummary(
    val id: String,
    val folderId: String,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val pinned: Boolean,
    val deletedAt: Long?,
    val appleId: String?,
    val dirty: Boolean,
)

data class NoteDetail(
    val id: String,
    val folderId: String,
    val title: String,
    val plaintext: String,
    val html: String,
    val blocks: List<NoteBlock>,
    val createdAt: Long,
    val modifiedAt: Long,
    val pinned: Boolean,
    val deletedAt: Long?,
    val appleId: String?,
    val passwordProtected: Boolean,
    val dirty: Boolean,
)
