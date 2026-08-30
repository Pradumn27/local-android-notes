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

fun NoteBlock.toAnnotated(base: TextStyle, colors: NotesColors): AnnotatedString {
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
        if (active.any { it.style == MarkStyle.UNDERLINE || it.style == MarkStyle.LINK }) {
            decorations += TextDecoration.Underline
        }
        builder.addStyle(
            SpanStyle(
                color = when {
                    checked -> colors.secondary
                    active.any { it.style == MarkStyle.LINK } -> colors.link
                    else -> colors.label
                },
                fontWeight = if (active.any { it.style == MarkStyle.BOLD }) FontWeight.Bold else base.fontWeight,
                fontStyle = if (active.any { it.style == MarkStyle.ITALIC }) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations),
                background = if (active.any { it.style == MarkStyle.HIGHLIGHT }) Color(0x66F5C518) else Color.Unspecified,
            ),
            start,
            end,
        )
        active.firstOrNull { it.style == MarkStyle.LINK && !it.href.isNullOrBlank() }?.let { link ->
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
        mark.style == MarkStyle.LINK && cursor in mark.start..mark.end && !mark.href.isNullOrBlank()
    }
}
