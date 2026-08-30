package com.localnotes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.localnotes.data.html.AppleNotesHtml
import com.localnotes.data.model.BlockType
import com.localnotes.data.model.MarkStyle
import com.localnotes.data.model.NoteBlock
import com.localnotes.ui.theme.LocalNotesColors
import com.localnotes.ui.theme.NotesTypography
import java.util.UUID

@Composable
fun BlockEditor(
    blocks: List<NoteBlock>,
    readOnly: Boolean,
    focusedId: String?,
    onFocused: (String?) -> Unit,
    onSelection: (Int, Int) -> Unit = { _, _ -> },
    onChange: (List<NoteBlock>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        val hidden = collapsedHiddenIds(blocks)
        blocks.forEachIndexed { index, block ->
            if (block.id in hidden) return@forEachIndexed
            BlockRow(
                block = block,
                index = index,
                number = numberFor(blocks, index),
                readOnly = readOnly,
                requestFocus = focusedId == block.id,
                onFocused = { onFocused(if (it) block.id else null) },
                onText = { value ->
                    onChange(blocks.update(index) {
                        it.copy(text = value, marks = AppleNotesHtml.adjustMarks(it.marks, it.text, value))
                    })
                },
                onSelection = onSelection,
                onChecked = { checked ->
                    onChange(blocks.update(index) { it.copy(checked = checked) })
                },
                onToggleCollapse = {
                    onChange(blocks.update(index) { it.copy(collapsed = !it.collapsed) })
                },
                onTableChange = { rows ->
                    onChange(blocks.update(index) { it.copy(tableRows = rows) })
                },
                onRemove = {
                    val next = blocks.toMutableList().also { it.removeAt(index) }
                    onChange(
                        next.ifEmpty {
                            listOf(NoteBlock(UUID.randomUUID().toString(), BlockType.TITLE, ""))
                        },
                    )
                },
                onSplit = { before, after ->
                    val first = block.copy(
                        text = before,
                        marks = AppleNotesHtml.adjustMarks(block.marks, block.text, before),
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockRow(
    block: NoteBlock,
    index: Int,
    number: Int,
    readOnly: Boolean,
    requestFocus: Boolean,
    onFocused: (Boolean) -> Unit,
    onText: (String) -> Unit,
    onSelection: (Int, Int) -> Unit,
    onChecked: (Boolean) -> Unit,
    onSplit: (String, String) -> Unit,
    onBackspaceEmpty: () -> Unit,
    onToggleCollapse: () -> Unit,
    onTableChange: (List<List<String>>) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalNotesColors.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var value by remember(block.id) {
        mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length)))
    }
    LaunchedEffect(block.text) {
        if (block.text != value.text) {
            val nextSelection = value.selection.start.coerceIn(0, block.text.length)
            value = value.copy(text = block.text, selection = TextRange(nextSelection))
        }
    }
    LaunchedEffect(requestFocus) {
        if (requestFocus && !hasFocus) {
            runCatching { focusRequester.requestFocus() }
            delay(80)
            runCatching { bringIntoView.bringIntoView() }
        }
    }

    val align = when (block.align) {
        com.localnotes.data.model.BlockAlign.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
        com.localnotes.data.model.BlockAlign.END -> androidx.compose.ui.text.style.TextAlign.End
        else -> androidx.compose.ui.text.style.TextAlign.Start
    }
    val style = blockTextStyle(block.type).copy(
        color = if (block.type == BlockType.CHECKLIST && block.checked) colors.secondary else colors.label,
        textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
        textAlign = align,
    )
    val marksTransform = remember(block.marks, block.checked, colors.isDark, style) {
        VisualTransformation { text ->
            TransformedText(
                block.copy(text = text.text).toAnnotated(style, colors, applySize = false),
                OffsetMapping.Identity,
            )
        }
    }

    if (block.type == BlockType.TABLE || block.type == BlockType.IMAGE ||
        block.type == BlockType.AUDIO || block.type == BlockType.FILE ||
        block.type == BlockType.DIVIDER
    ) {
        SpecialBlock(
            block = block,
            readOnly = readOnly,
            onFocused = { onFocused(true) },
            onTableChange = onTableChange,
            onRemove = onRemove,
        )
        return
    }

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
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    onSelection(next.selection.start, next.selection.end)
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
                singleLine = false,
                textStyle = style,
                cursorBrush = SolidColor(colors.gold),
                visualTransformation = marksTransform,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoView)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        hasFocus = it.isFocused
                        onFocused(it.isFocused)
                        if (it.isFocused) onSelection(value.selection.start, value.selection.end)
                    }
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
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty()) {
                            Text(placeholder(block.type, index), style = style.copy(color = colors.tertiary))
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun SpecialBlock(
    block: NoteBlock,
    readOnly: Boolean,
    onFocused: () -> Unit,
    onTableChange: (List<List<String>>) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalNotesColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        when (block.type) {
            BlockType.DIVIDER -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(1.dp)
                        .background(colors.separator)
                        .clickable(onClick = onFocused),
                )
            }
            BlockType.IMAGE -> {
                val bitmap = remember(block.text) { decodeDataImage(block.text) }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = block.mime ?: "image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable(onClick = onFocused),
                    )
                } else {
                    Text("Image", color = colors.secondary, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            BlockType.AUDIO -> {
                Text(
                    "▶  Audio",
                    color = colors.gold,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            onFocused()
                            runCatching { playDataAudio(context, block.text) }
                        },
                )
            }
            BlockType.FILE -> {
                val name = block.mime?.substringAfter('|')?.ifBlank { null } ?: "File"
                Text(
                    "⤓  $name",
                    color = colors.link,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            onFocused()
                            runCatching { openDataFile(context, block.text, block.mime) }
                        },
                )
            }
            BlockType.TABLE -> {
                val rows = block.tableRows.ifEmpty { listOf(listOf("", "")) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(0.5.dp, colors.separator, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                ) {
                    rows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEachIndexed { c, cell ->
                                BasicTextField(
                                    value = cell,
                                    onValueChange = { next ->
                                        val copy = rows.map { it.toMutableList() }.toMutableList()
                                        copy[r][c] = next
                                        onTableChange(copy.map { it.toList() })
                                    },
                                    readOnly = readOnly,
                                    textStyle = NotesTypography.bodyMedium.copy(color = colors.label),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp)
                                        .clickable(onClick = onFocused),
                                )
                            }
                        }
                    }
                    if (!readOnly) {
                        Text(
                            "+ Row",
                            color = colors.secondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable {
                                    val width = rows.firstOrNull()?.size ?: 2
                                    onTableChange(rows + listOf(List(width) { "" }))
                                },
                        )
                    }
                }
            }
            else -> Unit
        }
        if (!readOnly) {
            Text(
                "Remove",
                color = colors.tertiary,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

private fun decodeDataImage(src: String): androidx.compose.ui.graphics.ImageBitmap? {
    return runCatching {
        if (!src.startsWith("data:image")) return null
        val b64 = src.substringAfter("base64,", missingDelimiterValue = "")
        if (b64.isBlank()) return null
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

private fun playDataAudio(context: android.content.Context, src: String) {
    val file = writeDataCache(context, src, "note-audio.dat") ?: return
    android.media.MediaPlayer().apply {
        setOnCompletionListener { player -> player.release() }
        setOnErrorListener { player, _, _ ->
            player.release()
            true
        }
        setDataSource(file.absolutePath)
        prepare()
        start()
    }
}

private fun openDataFile(context: android.content.Context, src: String, mimeHint: String?) {
    val mime = mimeHint?.substringBefore('|')?.takeIf { it.contains('/') }
        ?: src.substringAfter("data:").substringBefore(";").substringBefore(",").ifBlank { "*/*" }
    val ext = when {
        mime.startsWith("image/") -> ".jpg"
        mime.startsWith("audio/") -> ".m4a"
        mime == "application/pdf" -> ".pdf"
        else -> ".bin"
    }
    val file = writeDataCache(context, src, "note-file$ext") ?: return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Open"))
}

private fun writeDataCache(context: android.content.Context, src: String, name: String): java.io.File? {
    val b64 = src.substringAfter("base64,", missingDelimiterValue = "")
    if (b64.isBlank()) return null
    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    val file = java.io.File(context.cacheDir, name)
    file.writeBytes(bytes)
    return file
}

private fun collapsedHiddenIds(blocks: List<NoteBlock>): Set<String> {
    val hidden = mutableSetOf<String>()
    var hiding = false
    var hideLevel = 99
    fun level(type: BlockType) = when (type) {
        BlockType.TITLE -> 0
        BlockType.HEADING -> 1
        BlockType.SUBHEADING -> 2
        else -> 3
    }
    blocks.forEach { block ->
        val lvl = level(block.type)
        if (lvl <= 2) {
            if (hiding && lvl <= hideLevel) hiding = false
            if (block.collapsed) {
                hiding = true
                hideLevel = lvl
            }
        } else if (hiding) {
            hidden += block.id
        }
    }
    return hidden
}

fun applyStyle(blocks: List<NoteBlock>, focusedId: String?, type: BlockType): List<NoteBlock> {
    if (focusedId == null) return blocks
    return blocks.map { block ->
        if (block.id == focusedId) block.copy(type = type) else block
    }
}

fun insertAfter(blocks: List<NoteBlock>, focusedId: String?, item: NoteBlock): Pair<List<NoteBlock>, String> {
    val index = blocks.indexOfFirst { it.id == focusedId }
    val next = blocks.toMutableList()
    if (index < 0) next.add(item) else next.add(index + 1, item)
    return next to item.id
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
    linkHref: String? = null,
    expanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    onStyle: (BlockType) -> Unit,
    onMark: (MarkStyle) -> Unit,
    onOpenLink: (String) -> Unit = {},
    onChecklist: () -> Unit,
    onAlign: (com.localnotes.data.model.BlockAlign) -> Unit = {},
    onColor: (String?) -> Unit = {},
    onHighlight: (String?) -> Unit = {},
    onSize: (Float?) -> Unit = {},
    onIndent: (Int) -> Unit = {},
    onTable: () -> Unit = {},
    onImage: () -> Unit = {},
    onAudio: () -> Unit = {},
    onFile: () -> Unit = {},
    onDivider: () -> Unit = {},
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalNotesColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .background(colors.toolbar)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        ScrollRow {
            FormatChip("Aa", expanded) { onToggleExpanded() }
            FormatChip("B", false, bold = true) { onMark(MarkStyle.BOLD) }
            FormatChip("I", false, italic = true) { onMark(MarkStyle.ITALIC) }
            FormatChip("U", false, underline = true) { onMark(MarkStyle.UNDERLINE) }
            FormatChip("S", false, strike = true) { onMark(MarkStyle.STRIKE) }
            FormatChip("–", current == BlockType.BULLET) { onStyle(BlockType.BULLET) }
            FormatChip("1.", current == BlockType.NUMBERED) { onStyle(BlockType.NUMBERED) }
            FormatChip("☑", current == BlockType.CHECKLIST, onClick = onChecklist)
            FormatChip("Link", false) { onMark(MarkStyle.LINK) }
            if (!linkHref.isNullOrBlank()) FormatChip("Open", false) { onOpenLink(linkHref) }
            FormatChip("Photo", false, onClick = onImage)
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            ScrollRow {
                FormatChip("Title", current == BlockType.TITLE) { onStyle(BlockType.TITLE) }
                FormatChip("Heading", current == BlockType.HEADING) { onStyle(BlockType.HEADING) }
                FormatChip("Subheading", current == BlockType.SUBHEADING) { onStyle(BlockType.SUBHEADING) }
                FormatChip("Body", current == BlockType.BODY) { onStyle(BlockType.BODY) }
                FormatChip("Mono", current == BlockType.MONO) { onStyle(BlockType.MONO) }
                FormatChip("Left", false) { onAlign(com.localnotes.data.model.BlockAlign.START) }
                FormatChip("Center", false) { onAlign(com.localnotes.data.model.BlockAlign.CENTER) }
                FormatChip("Right", false) { onAlign(com.localnotes.data.model.BlockAlign.END) }
                FormatChip("⇤", false) { onIndent(-1) }
                FormatChip("⇥", false) { onIndent(1) }
                FormatChip("Fold", false) { onCollapse() }
            }
            Spacer(Modifier.height(6.dp))
            ScrollRow {
                FormatChip("Table", false, onClick = onTable)
                FormatChip("Audio", false, onClick = onAudio)
                FormatChip("File", false, onClick = onFile)
                FormatChip("Line", false, onClick = onDivider)
                ColorDot("#1C1C1E") { onColor(null) }
                listOf("#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#5856D6", "#AF52DE").forEach { hex ->
                    ColorDot(hex) { onColor(hex) }
                }
                listOf("#FFF2A8", "#C6F6D5", "#FBB6CE", "#BEE3F8", "#E9D8FD").forEach { hex ->
                    ColorDot(hex, ring = true) { onHighlight(hex) }
                }
            }
        }
    }
}

@Composable
private fun ScrollRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ColorDot(hex: String, ring: Boolean = false, onClick: () -> Unit) {
    val parsed = hex.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: Color.Gray
    Box(
        modifier = Modifier
            .size(22.dp)
            .focusProperties { canFocus = false }
            .clip(CircleShape)
            .background(parsed)
            .then(if (ring) Modifier.border(1.dp, Color.DarkGray, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
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
            .focusProperties { canFocus = false }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (selected) colors.sidebarSelected else colors.search.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
