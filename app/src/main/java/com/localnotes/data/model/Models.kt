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
    TABLE,
    IMAGE,
    AUDIO,
    FILE,
    DIVIDER,
}

enum class BlockAlign {
    START,
    CENTER,
    END,
}

enum class MarkStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKE,
    HIGHLIGHT,
    LINK,
    COLOR,
    FONT_SIZE,
    TAG,
    NOTE_LINK,
    MENTION,
}

data class TextMark(
    val start: Int,
    val end: Int,
    val style: MarkStyle,
    val href: String? = null,
    val color: String? = null,
    val highlight: String? = null,
    val fontSizePx: Float? = null,
)

data class NoteBlock(
    val id: String,
    val type: BlockType,
    val text: String,
    val checked: Boolean = false,
    val indent: Int = 0,
    val marks: List<TextMark> = emptyList(),
    val align: BlockAlign = BlockAlign.START,
    val collapsed: Boolean = false,
    val tableRows: List<List<String>> = emptyList(),
    val mime: String? = null,
) {
    fun isBlank(): Boolean = when (type) {
        BlockType.CHECKLIST -> false
        BlockType.TABLE -> tableRows.isEmpty()
        BlockType.IMAGE, BlockType.AUDIO, BlockType.FILE, BlockType.DIVIDER -> false
        else -> text.isBlank()
    }
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
