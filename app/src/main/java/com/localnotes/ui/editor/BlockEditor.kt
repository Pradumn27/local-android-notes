package com.localnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localnotes.data.model.BlockType
import com.localnotes.data.model.MarkStyle
import com.localnotes.data.model.NoteBlock
import com.localnotes.data.model.TextMark
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTypography
import java.util.UUID

@Composable
fun BlockEditor(
    blocks: List<NoteBlock>,
    readOnly: Boolean,
    focusedId: String?,
    onFocused: (String?) -> Unit,
    onChange: (List<NoteBlock>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            BlockRow(
                block = block,
                index = index,
                number = numberFor(blocks, index),
                readOnly = readOnly,
                requestFocus = focusedId == block.id,
                onFocused = { onFocused(if (it) block.id else null) },
                onText = { value ->
                    onChange(blocks.update(index) { it.copy(text = value) })
                },
                onChecked = { checked ->
                    onChange(blocks.update(index) { it.copy(checked = checked) })
                },
                onSplit = { before, after ->
                    val first = block.copy(text = before)
                    val second = NoteBlock(
                        id = UUID.randomUUID().toString(),
                        type = if (block.type == BlockType.TITLE) BlockType.BODY else block.type,
                        text = after,
                        indent = block.indent,
                    )
                    onChange(blocks.toMutableList().also {
                        it[index] = first
                        it.add(index + 1, second)
                    })
                    onFocused(second.id)
                },
                onBackspaceEmpty = {
                    if (blocks.size == 1) {
                        onChange(listOf(block.copy(text = "", type = BlockType.TITLE, marks = emptyList())))
                    } else {
                        val previous = blocks.getOrNull(index - 1)
                        val next = blocks.toMutableList().also { it.removeAt(index) }
                        if (previous != null) {
                            next[index - 1] = previous.copy(text = previous.text)
                            onFocused(previous.id)
                        }
                        onChange(next)
                    }
                },
            )
        }
        if (!readOnly) {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun BlockRow(
    block: NoteBlock,
    index: Int,
    number: Int,
    readOnly: Boolean,
    requestFocus: Boolean,
    onFocused: (Boolean) -> Unit,
    onText: (String) -> Unit,
    onChecked: (Boolean) -> Unit,
    onSplit: (String, String) -> Unit,
    onBackspaceEmpty: () -> Unit,
) {
    val colors = LocalNotesColors.current
    val focusRequester = remember { FocusRequester() }
    var value by remember(block.id) {
        mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length)))
    }
    LaunchedEffect(block.text) {
        if (block.text != value.text) {
            value = value.copy(text = block.text)
        }
    }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val style = blockTextStyle(block.type).copy(
        color = if (block.type == BlockType.CHECKLIST && block.checked) colors.secondary else colors.label,
        textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = blockVertical(block.type)),
        verticalAlignment = Alignment.Top,
    ) {
        when (block.type) {
            BlockType.CHECKLIST -> {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 10.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (block.checked) colors.checkFill else colors.tertiary, CircleShape)
                        .background(if (block.checked) colors.checkFill else colors.editor.copy(alpha = 0f))
                        .clickable(enabled = !readOnly) { onChecked(!block.checked) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (block.checked) {
                        Text("✓", color = colors.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            BlockType.BULLET -> {
                Text(
                    "–",
                    color = colors.label,
                    style = NotesTypography.bodyLarge,
                    modifier = Modifier.padding(top = 1.dp, end = 10.dp, start = (block.indent * 16).dp),
                )
            }
            BlockType.NUMBERED -> {
                Text(
                    "$number.",
                    color = colors.label,
                    style = NotesTypography.bodyLarge,
                    modifier = Modifier.padding(top = 1.dp, end = 10.dp, start = (block.indent * 16).dp),
                )
            }
            else -> Unit
        }

        Box(Modifier.weight(1f)) {
            if (value.text.isEmpty()) {
                Text(placeholder(block.type, index), style = style.copy(color = colors.tertiary))
            }
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val newline = next.text.indexOf('\n')
                    if (newline >= 0) {
                        val before = next.text.substring(0, newline)
                        val after = next.text.substring(newline + 1).replace("\n", "")
                        value = TextFieldValue(before, TextRange(before.length))
                        onSplit(before, after)
                    } else {
                        value = next
                        onText(next.text)
                    }
                },
                readOnly = readOnly,
                textStyle = style,
                cursorBrush = SolidColor(colors.gold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocused(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace &&
                            value.text.isEmpty()
                        ) {
                            onBackspaceEmpty()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

fun applyStyle(blocks: List<NoteBlock>, focusedId: String?, type: BlockType): List<NoteBlock> {
    if (focusedId == null) return blocks
    return blocks.map { block ->
        if (block.id == focusedId) block.copy(type = type) else block
    }
}

fun toggleMark(blocks: List<NoteBlock>, focusedId: String?, style: MarkStyle): List<NoteBlock> {
    if (focusedId == null) return blocks
    return blocks.map { block ->
        if (block.id != focusedId || block.text.isEmpty()) return@map block
        val existing = block.marks.any { it.style == style && it.start == 0 && it.end == block.text.length }
        val marks = if (existing) {
            block.marks.filterNot { it.style == style && it.start == 0 && it.end == block.text.length }
        } else {
            block.marks + TextMark(0, block.text.length, style)
        }
        block.copy(marks = marks)
    }
}

fun insertChecklist(blocks: List<NoteBlock>, focusedId: String?): Pair<List<NoteBlock>, String> {
    val item = NoteBlock(UUID.randomUUID().toString(), BlockType.CHECKLIST, "")
    if (focusedId == null) return (blocks + item) to item.id
    val index = blocks.indexOfFirst { it.id == focusedId }
    if (index < 0) return (blocks + item) to item.id
    val next = blocks.toMutableList()
    next.add(index + 1, item)
    return next to item.id
}

private fun List<NoteBlock>.update(index: Int, transform: (NoteBlock) -> NoteBlock): List<NoteBlock> {
    return mapIndexed { i, block -> if (i == index) transform(block) else block }
}

private fun blockTextStyle(type: BlockType): TextStyle = when (type) {
    BlockType.TITLE -> NotesTypography.displaySmall
    BlockType.HEADING -> NotesTypography.headlineMedium
    BlockType.SUBHEADING -> NotesTypography.headlineSmall
    BlockType.MONO -> TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    else -> NotesTypography.bodyLarge
}

private fun blockVertical(type: BlockType) = when (type) {
    BlockType.TITLE -> 10.dp
    BlockType.HEADING -> 8.dp
    BlockType.SUBHEADING -> 6.dp
    else -> 4.dp
}

private fun placeholder(type: BlockType, index: Int): String = when {
    type == BlockType.TITLE && index == 0 -> "Title"
    type == BlockType.CHECKLIST -> "Checklist"
    type == BlockType.BULLET || type == BlockType.NUMBERED -> "List"
    else -> ""
}

private fun numberFor(blocks: List<NoteBlock>, index: Int): Int {
    var n = 0
    for (i in index downTo 0) {
        if (blocks[i].type != BlockType.NUMBERED) break
        n += 1
    }
    return n.coerceAtLeast(1)
}

@Composable
fun FormatBar(
    current: BlockType?,
    onStyle: (BlockType) -> Unit,
    onMark: (MarkStyle) -> Unit,
    onChecklist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.toolbar)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FormatChip("Title", current == BlockType.TITLE) { onStyle(BlockType.TITLE) }
            FormatChip("Heading", current == BlockType.HEADING) { onStyle(BlockType.HEADING) }
            FormatChip("Subheading", current == BlockType.SUBHEADING) { onStyle(BlockType.SUBHEADING) }
            FormatChip("Body", current == BlockType.BODY) { onStyle(BlockType.BODY) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FormatChip("Aa Mono", current == BlockType.MONO) { onStyle(BlockType.MONO) }
            FormatChip("– List", current == BlockType.BULLET) { onStyle(BlockType.BULLET) }
            FormatChip("1. List", current == BlockType.NUMBERED) { onStyle(BlockType.NUMBERED) }
            FormatChip("☑", current == BlockType.CHECKLIST, onClick = onChecklist)
            FormatChip("B", false, bold = true) { onMark(MarkStyle.BOLD) }
            FormatChip("I", false, italic = true) { onMark(MarkStyle.ITALIC) }
            FormatChip("U", false, underline = true) { onMark(MarkStyle.UNDERLINE) }
            FormatChip("S", false, strike = true) { onMark(MarkStyle.STRIKE) }
        }
    }
}

@Composable
private fun FormatChip(
    label: String,
    selected: Boolean,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalNotesColors.current
    Text(
        text = label,
        color = if (selected) colors.label else colors.secondary,
        fontSize = 13.sp,
        fontWeight = if (bold || selected) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            underline -> TextDecoration.Underline
            strike -> TextDecoration.LineThrough
            else -> TextDecoration.None
        },
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (selected) colors.sidebarSelected else colors.search.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
