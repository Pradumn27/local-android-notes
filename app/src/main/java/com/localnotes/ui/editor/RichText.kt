package com.localnotes.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.localnotes.data.model.MarkStyle
import com.localnotes.data.model.NoteBlock
import com.localnotes.data.model.TextMark
import com.localnotes.ui.theme.NotesColors

fun NoteBlock.toAnnotated(base: TextStyle, colors: NotesColors, applySize: Boolean = true): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.append(text)
    if (text.isEmpty()) return builder.toAnnotatedString()
    val bounds = (marks.flatMap { listOf(it.start.coerceIn(0, text.length), it.end.coerceIn(0, text.length) ) } + 0 + text.length)
        .distinct()
        .sorted()
    for (i in 0 until bounds.lastIndex) {
        val start = bounds[i]
        val end = bounds[i + 1]
        if (start >= end) continue
        val active = marks.filter { it.start < end && it.end > start }
        val decorations = mutableListOf<TextDecoration>()
        if (checked || active.any { it.style == MarkStyle.STRIKE }) decorations += TextDecoration.LineThrough
        if (active.any { it.style == MarkStyle.UNDERLINE || it.style == MarkStyle.LINK || it.style == MarkStyle.NOTE_LINK }) {
            decorations += TextDecoration.Underline
        }
        val colorMark = active.firstOrNull { it.style == MarkStyle.COLOR && !it.color.isNullOrBlank() }
        val highlightMark = active.firstOrNull { it.style == MarkStyle.HIGHLIGHT }
        val sizeMark = active.firstOrNull { it.style == MarkStyle.FONT_SIZE && it.fontSizePx != null }
        builder.addStyle(
            SpanStyle(
                color = when {
                    checked -> colors.secondary
                    active.any { it.style == MarkStyle.LINK || it.style == MarkStyle.NOTE_LINK } -> colors.link
                    active.any { it.style == MarkStyle.TAG || it.style == MarkStyle.MENTION } -> colors.gold
                    colorMark != null -> parseHex(colorMark.color)
                    else -> colors.label
                },
                fontWeight = if (active.any { it.style == MarkStyle.BOLD }) FontWeight.Bold else base.fontWeight,
                fontStyle = if (active.any { it.style == MarkStyle.ITALIC }) FontStyle.Italic else FontStyle.Normal,
                fontSize = if (applySize) {
                    sizeMark?.fontSizePx?.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }
                        ?: base.fontSize
                } else {
                    base.fontSize
                },
                textDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations),
                background = when {
                    highlightMark?.highlight != null -> parseHex(highlightMark.highlight).copy(alpha = 0.55f)
                    highlightMark != null -> Color(0x66F5C518)
                    else -> Color.Unspecified
                },
            ),
            start,
            end,
        )
        active.firstOrNull { (it.style == MarkStyle.LINK || it.style == MarkStyle.NOTE_LINK) && !it.href.isNullOrBlank() }?.let { link ->
            builder.addStringAnnotation("URL", link.href.orEmpty(), start, end)
        }
    }
    return builder.toAnnotatedString()
}

fun toggleMark(
    blocks: List<NoteBlock>,
    focusedId: String?,
    style: MarkStyle,
    selectionStart: Int,
    selectionEnd: Int,
): List<NoteBlock> {
    if (focusedId == null) return blocks
    return blocks.map { block ->
        if (block.id != focusedId || block.text.isEmpty()) return@map block
        val rawStart = minOf(selectionStart, selectionEnd).coerceIn(0, block.text.length)
        val rawEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, block.text.length)
        val start = if (rawStart == rawEnd) 0 else rawStart
        val end = if (rawStart == rawEnd) block.text.length else rawEnd
        if (start >= end) return@map block
        val covering = block.marks.filter { it.style == style && it.start <= start && it.end >= end }
        val marks = if (covering.isNotEmpty()) {
            block.marks - covering.toSet()
        } else {
            val href = if (style == MarkStyle.LINK) {
                block.text.substring(start, end).trim().let { piece ->
                    when {
                        piece.startsWith("http://") || piece.startsWith("https://") -> piece
                        piece.contains('.') -> "https://$piece"
                        else -> piece
                    }
                }
            } else {
                null
            }
            block.marks + TextMark(start, end, style, href = href)
        }
        block.copy(marks = marks)
    }
}

fun linkAt(block: NoteBlock?, cursor: Int): TextMark? {
    if (block == null) return null
    return block.marks.firstOrNull { mark ->
        (mark.style == MarkStyle.LINK || mark.style == MarkStyle.NOTE_LINK) &&
            cursor in mark.start..mark.end &&
            !mark.href.isNullOrBlank()
    }
}

fun applyColor(
    blocks: List<NoteBlock>,
    focusedId: String?,
    hex: String?,
    selectionStart: Int,
    selectionEnd: Int,
): List<NoteBlock> = applyValueMark(blocks, focusedId, MarkStyle.COLOR, selectionStart, selectionEnd) { mark, start, end ->
    if (hex == null) null else mark.copy(start = start, end = end, style = MarkStyle.COLOR, color = hex)
}

fun applyHighlight(
    blocks: List<NoteBlock>,
    focusedId: String?,
    hex: String?,
    selectionStart: Int,
    selectionEnd: Int,
): List<NoteBlock> = applyValueMark(blocks, focusedId, MarkStyle.HIGHLIGHT, selectionStart, selectionEnd) { mark, start, end ->
    if (hex == null) null else mark.copy(start = start, end = end, style = MarkStyle.HIGHLIGHT, highlight = hex)
}

fun applyFontSize(
    blocks: List<NoteBlock>,
    focusedId: String?,
    size: Float?,
    selectionStart: Int,
    selectionEnd: Int,
): List<NoteBlock> = applyValueMark(blocks, focusedId, MarkStyle.FONT_SIZE, selectionStart, selectionEnd) { mark, start, end ->
    if (size == null) null else mark.copy(start = start, end = end, style = MarkStyle.FONT_SIZE, fontSizePx = size)
}

private fun applyValueMark(
    blocks: List<NoteBlock>,
    focusedId: String?,
    style: MarkStyle,
    selectionStart: Int,
    selectionEnd: Int,
    make: (TextMark, Int, Int) -> TextMark?,
): List<NoteBlock> {
    if (focusedId == null) return blocks
    return blocks.map { block ->
        if (block.id != focusedId || block.text.isEmpty()) return@map block
        val rawStart = minOf(selectionStart, selectionEnd).coerceIn(0, block.text.length)
        val rawEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, block.text.length)
        val start = if (rawStart == rawEnd) 0 else rawStart
        val end = if (rawStart == rawEnd) block.text.length else rawEnd
        if (start >= end) return@map block
        val without = block.marks.filterNot { it.style == style && it.start < end && it.end > start }
        val created = make(TextMark(start, end, style), start, end)
        block.copy(marks = if (created == null) without else without + created)
    }
}

private fun parseHex(hex: String?): Color {
    val raw = hex?.removePrefix("#").orEmpty()
    val value = raw.toLongOrNull(16) ?: return Color.Unspecified
    return when (raw.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color.Unspecified
    }
}
