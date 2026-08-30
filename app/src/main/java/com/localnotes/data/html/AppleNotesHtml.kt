package com.localnotes.data.html

import com.localnotes.data.model.BlockAlign
import com.localnotes.data.model.BlockType
import com.localnotes.data.model.MarkStyle
import com.localnotes.data.model.NoteBlock
import com.localnotes.data.model.TextMark
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.UUID

data class EncodedNote(
    val title: String,
    val plaintext: String,
    val html: String,
)

/**
 * Apple Notes stores [body] as a small HTML dialect. This codec is the
 * interchange format for Mac AppleScript / JXA sync.
 *
 * Paragraphs: title / heading / subheading via font-size 21 / 15 / 13,
 * plus <h1>–<h3>, body, mono.
 * Lists: Apple-dash-list, <ol>, Apple-checklist (and unicode ☐/☑ prefixes).
 * Marks: bold, italic, underline, strike, color, highlight, font-size,
 * links, >> note links, #tags, @mentions.
 * Blocks: tables, images, audio, files (data URIs / cid:), hr, alignment,
 * indent, collapsed headings.
 */
object AppleNotesHtml {

    fun encode(blocks: List<NoteBlock>): EncodedNote {
        val normalized = if (blocks.isEmpty()) {
            listOf(NoteBlock(UUID.randomUUID().toString(), BlockType.TITLE, ""))
        } else {
            blocks
        }
        val html = buildString {
            var index = 0
            while (index < normalized.size) {
                val block = normalized[index]
                when (block.type) {
                    BlockType.BULLET -> {
                        val group = takeGroup(normalized, index, BlockType.BULLET)
                        append(renderList("ul", "Apple-dash-list", group))
                        index += group.size
                    }
                    BlockType.NUMBERED -> {
                        val group = takeGroup(normalized, index, BlockType.NUMBERED)
                        append(renderList("ol", null, group))
                        index += group.size
                    }
                    BlockType.CHECKLIST -> {
                        val group = takeGroup(normalized, index, BlockType.CHECKLIST)
                        append(renderChecklist(group))
                        index += group.size
                    }
                    BlockType.TABLE -> {
                        append(renderTable(block))
                        index += 1
                    }
                    BlockType.IMAGE -> {
                        append("""<div><img src="${escapeAttr(block.text)}" alt=""/></div>""")
                        index += 1
                    }
                    BlockType.AUDIO -> {
                        append("""<div><audio controls src="${escapeAttr(block.text)}"></audio></div>""")
                        index += 1
                    }
                    BlockType.FILE -> {
                        val name = fileNameOf(block)
                        append("""<div><a href="${escapeAttr(block.text)}" download="${escapeAttr(name)}">${escape(name)}</a></div>""")
                        index += 1
                    }
                    BlockType.DIVIDER -> {
                        append("<hr/>")
                        index += 1
                    }
                    else -> {
                        append(renderParagraph(block))
                        index += 1
                    }
                }
            }
        }
        val plaintext = normalized.joinToString("\n") { blockPlaintext(it) }.trimEnd()
        val title = normalized.firstOrNull { it.text.isNotBlank() && it.type != BlockType.DIVIDER }?.text
            ?.lineSequence()?.first()?.trim()
            .orEmpty()
            .ifBlank { "New Note" }
        return EncodedNote(title = title, plaintext = plaintext, html = html)
    }

    fun decode(html: String): List<NoteBlock> {
        if (html.isBlank()) {
            return listOf(NoteBlock(newId(), BlockType.TITLE, ""))
        }
        val document = Jsoup.parseBodyFragment(html)
        val blocks = mutableListOf<NoteBlock>()
        document.body().childNodes().forEach { node ->
            collectBlocks(node, blocks)
        }
        if (blocks.isEmpty()) {
            val fallback = document.body().wholeText().trim()
            if (fallback.isNotEmpty()) {
                fallback.split("\n").forEachIndexed { index, line ->
                    blocks += NoteBlock(
                        id = newId(),
                        type = if (index == 0) BlockType.TITLE else BlockType.BODY,
                        text = line,
                    )
                }
            }
        }
        if (blocks.isEmpty()) {
            blocks += NoteBlock(newId(), BlockType.TITLE, "")
        } else if (blocks.first().type == BlockType.BODY && blocks.first().text.isNotBlank()) {
            blocks[0] = blocks[0].copy(type = BlockType.TITLE)
        }
        return blocks
    }

    fun preview(plaintext: String, title: String): String {
        val lines = plaintext.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return ""
        val withoutTitle = if (lines.first() == title) lines.drop(1) else lines
        return withoutTitle.joinToString(" ")
    }

    data class DisplayLine(
        val text: String,
        val type: BlockType,
        val checked: Boolean = false,
    )

    fun displayLines(html: String, title: String, limit: Int = 16): List<DisplayLine> {
        val blocks = decode(html)
        val lines = mutableListOf<DisplayLine>()
        var number = 0
        for (block in blocks) {
            if (block.type == BlockType.TITLE &&
                lines.isEmpty() &&
                (block.text == title || block.text.isBlank())
            ) {
                continue
            }
            val body = displayText(block)
            if (body.isBlank() && block.type != BlockType.CHECKLIST && block.type != BlockType.DIVIDER) continue
            if (block.type == BlockType.NUMBERED) number += 1 else number = 0
            val indent = "    ".repeat(block.indent.coerceIn(0, 4))
            val prefix = when (block.type) {
                BlockType.BULLET -> "–  "
                BlockType.NUMBERED -> "$number. "
                BlockType.CHECKLIST -> if (block.checked) "☑  " else "○  "
                BlockType.IMAGE -> "🖼  "
                BlockType.AUDIO -> "▶  "
                BlockType.FILE -> "⤓  "
                BlockType.TABLE -> ""
                BlockType.DIVIDER -> ""
                else -> ""
            }
            lines += DisplayLine(
                text = indent + prefix + body,
                type = block.type,
                checked = block.checked,
            )
            if (lines.size >= limit) break
        }
        return lines
    }

    private fun takeGroup(blocks: List<NoteBlock>, start: Int, type: BlockType): List<NoteBlock> {
        val group = mutableListOf<NoteBlock>()
        var i = start
        while (i < blocks.size && blocks[i].type == type) {
            group += blocks[i]
            i += 1
        }
        return group
    }

    private fun renderParagraph(block: NoteBlock): String {
        val inner = applyMarks(block.text, block.marks).ifBlank { "<br>" }
        val sized = when (block.type) {
            BlockType.TITLE -> wrapSize(inner, 21, bold = true)
            BlockType.HEADING -> wrapSize(inner, 15, bold = true)
            BlockType.SUBHEADING -> wrapSize(inner, 13, bold = true)
            BlockType.MONO -> "<pre>$inner</pre>"
            else -> wrapSize(inner, 11, bold = false)
        }
        if (block.type == BlockType.MONO) return sized
        val styles = mutableListOf<String>()
        when (block.align) {
            BlockAlign.CENTER -> styles += "text-align: center"
            BlockAlign.END -> styles += "text-align: right"
            BlockAlign.START -> Unit
        }
        if (block.indent > 0) styles += "margin-left: ${block.indent * 20}px"
        val collapsed = if (block.collapsed) " data-collapsed=\"true\"" else ""
        val style = if (styles.isEmpty()) "" else " style=\"${styles.joinToString("; ")}\""
        return "<div$style$collapsed>$sized</div>"
    }

    private fun renderTable(block: NoteBlock): String {
        val rows = block.tableRows.ifEmpty { listOf(listOf("", "")) }
        val body = rows.joinToString("") { row ->
            val cells = row.joinToString("") { cell -> "<td>${escape(cell).ifBlank { "<br>" }}</td>" }
            "<tr>$cells</tr>"
        }
        return """<table cellspacing="0" cellpadding="6" border="1">$body</table>"""
    }

    private fun wrapSize(inner: String, px: Int, bold: Boolean): String {
        val span = "<span style=\"font-size: ${px}px\">$inner</span>"
        return if (bold) "<b>$span</b>" else span
    }

    private fun renderList(tag: String, cssClass: String?, items: List<NoteBlock>): String {
        val cls = cssClass?.let { " class=\"$it\"" }.orEmpty()
        val lis = items.joinToString("") { item ->
            val pad = if (item.indent > 0) " data-indent=\"${item.indent}\"" else ""
            val body = applyMarks(item.text, item.marks).ifBlank { "<br>" }
            "<li$pad><span style=\"font-size: 11px\">$body</span></li>"
        }
        return "<$tag$cls>$lis</$tag>"
    }

    private fun renderChecklist(items: List<NoteBlock>): String {
        val lis = items.joinToString("") { item ->
            val checked = if (item.checked) " class=\"checked\"" else ""
            val pad = if (item.indent > 0) " data-indent=\"${item.indent}\"" else ""
            val body = applyMarks(item.text, item.marks).ifBlank { "<br>" }
            "<li$checked$pad><span style=\"font-size: 11px\">$body</span></li>"
        }
        return "<ul class=\"Apple-checklist\">$lis</ul>"
    }

    private fun applyMarks(text: String, marks: List<TextMark>): String {
        if (text.isEmpty()) return ""
        if (marks.isEmpty()) return escape(text)
        val valid = marks.filter { it.start < it.end && it.start >= 0 && it.end <= text.length }
        val opens = Array(text.length + 1) { mutableListOf<TextMark>() }
        val closes = Array(text.length + 1) { mutableListOf<TextMark>() }
        valid.forEach { mark ->
            opens[mark.start] += mark
            closes[mark.end] += mark
        }
        return buildString {
            for (i in 0..text.length) {
                closes[i].asReversed().forEach { append(closeTag(it)) }
                if (i == text.length) break
                opens[i].forEach { append(openTag(it)) }
                append(escape(text[i].toString()))
            }
        }
    }

    private fun openTag(mark: TextMark): String = when (mark.style) {
        MarkStyle.BOLD -> "<b>"
        MarkStyle.ITALIC -> "<i>"
        MarkStyle.UNDERLINE -> "<u>"
        MarkStyle.STRIKE -> "<strike>"
        MarkStyle.HIGHLIGHT -> {
            val bg = mark.highlight ?: "#FFF2A8"
            "<span class=\"Apple-highlight\" style=\"background-color: $bg\">"
        }
        MarkStyle.LINK, MarkStyle.NOTE_LINK -> {
            val href = escapeAttr(mark.href ?: "")
            "<a href=\"$href\">"
        }
        MarkStyle.COLOR -> "<span style=\"color: ${mark.color ?: "#000000"}\">"
        MarkStyle.FONT_SIZE -> "<span style=\"font-size: ${mark.fontSizePx ?: 11f}px\">"
        MarkStyle.TAG -> "<span class=\"Apple-tag\">"
        MarkStyle.MENTION -> "<span class=\"Apple-mention\">"
    }

    private fun closeTag(mark: TextMark): String = when (mark.style) {
        MarkStyle.BOLD -> "</b>"
        MarkStyle.ITALIC -> "</i>"
        MarkStyle.UNDERLINE -> "</u>"
        MarkStyle.STRIKE -> "</strike>"
        MarkStyle.HIGHLIGHT, MarkStyle.COLOR, MarkStyle.FONT_SIZE, MarkStyle.TAG, MarkStyle.MENTION -> "</span>"
        MarkStyle.LINK, MarkStyle.NOTE_LINK -> "</a>"
    }

    private fun escapeAttr(text: String): String = escape(text).replace("\"", "&quot;")

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun collectBlocks(node: Node, into: MutableList<NoteBlock>) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText.trim('\n', '\r')
                if (text.isNotBlank()) {
                    into += NoteBlock(newId(), BlockType.BODY, text.trim())
                }
            }
            is Element -> {
                when (node.tagName().lowercase()) {
                    "h1" -> into += paragraphFrom(node, BlockType.TITLE)
                    "h2" -> into += paragraphFrom(node, BlockType.HEADING)
                    "h3" -> into += paragraphFrom(node, BlockType.SUBHEADING)
                    "pre" -> into += paragraphFrom(node, BlockType.MONO)
                    "div", "p" -> {
                        if (node.children().any { it.tagName() in blockTags }) {
                            node.childNodes().forEach { collectBlocks(it, into) }
                        } else {
                            val text = inlineText(node)
                            if (text.isNotBlank() || node.getElementsByTag("br").isNotEmpty()) {
                                into += paragraphFrom(node, inferParagraphType(node))
                            }
                        }
                    }
                    "span" -> {
                        val text = inlineText(node)
                        if (text.isNotBlank()) {
                            into += paragraphFrom(node, inferParagraphType(node))
                        }
                    }
                    "ul" -> {
                        val isChecklist = node.classNames().any { it.contains("check", ignoreCase = true) } ||
                            node.select("input[type=checkbox]").isNotEmpty()
                        node.children().filter { it.tagName() == "li" }.forEach { li ->
                            val (raw, marks) = inlineContent(li)
                            val (text, box) = checklistPrefix(raw)
                            val checked = li.hasClass("checked") ||
                                li.selectFirst("input[type=checkbox]")?.hasAttr("checked") == true ||
                                box == true
                            val type = when {
                                isChecklist || box != null -> BlockType.CHECKLIST
                                else -> BlockType.BULLET
                            }
                            into += NoteBlock(
                                id = newId(),
                                type = type,
                                text = text,
                                checked = checked,
                                indent = li.attr("data-indent").toIntOrNull() ?: indentOf(li),
                                marks = mergeInlineExtras(text, marks),
                                align = alignmentOf(li),
                            )
                        }
                    }
                    "ol" -> {
                        node.children().filter { it.tagName() == "li" }.forEach { li ->
                            val (text, marks) = inlineContent(li)
                            into += NoteBlock(
                                id = newId(),
                                type = BlockType.NUMBERED,
                                text = text,
                                indent = li.attr("data-indent").toIntOrNull() ?: indentOf(li),
                                marks = mergeInlineExtras(text, marks),
                                align = alignmentOf(li),
                            )
                        }
                    }
                    "br" -> into += NoteBlock(newId(), BlockType.BODY, "")
                    "hr" -> into += NoteBlock(newId(), BlockType.DIVIDER, "")
                    "table" -> into += tableFrom(node)
                    "img" -> mediaFrom(node, "src", node.attr("alt"))?.let { into += it }
                    "audio", "video" -> mediaFrom(node, "src", node.attr("type").ifBlank { node.tagName() })?.let { into += it }
                    "object", "embed" -> mediaFrom(node, "data", node.attr("type").ifBlank { node.attr("name") })?.let { into += it }
                    "a" -> {
                        val href = node.attr("href")
                        if (isFileHref(href) || node.hasAttr("download")) {
                            into += fileFromAnchor(node)
                        } else {
                            into += paragraphFrom(node, inferParagraphType(node))
                        }
                    }
                    else -> node.childNodes().forEach { collectBlocks(it, into) }
                }
            }
        }
    }

    private val blockTags = setOf(
        "div", "p", "h1", "h2", "h3", "pre", "ul", "ol",
        "table", "img", "audio", "video", "object", "embed", "hr",
    )

    private fun paragraphFrom(element: Element, type: BlockType): NoteBlock {
        val loneMedia = loneMediaChild(element)
        if (loneMedia != null) return loneMedia
        val (text, marks) = inlineContent(element)
        if (DASH_DIVIDER.matches(text)) {
            return NoteBlock(newId(), BlockType.DIVIDER, "")
        }
        val heading = type == BlockType.TITLE || type == BlockType.HEADING || type == BlockType.SUBHEADING
        val cleaned = if (heading) {
            marks.filterNot { it.style == MarkStyle.BOLD && it.start == 0 && it.end == text.length }
        } else {
            marks
        }
        return NoteBlock(
            id = newId(),
            type = type,
            text = text,
            marks = mergeInlineExtras(text, cleaned),
            align = alignmentOf(element),
            indent = indentOf(element),
            collapsed = element.hasAttr("data-collapsed") ||
                element.attr("data-collapsed").equals("true", true),
        )
    }

    private fun tableFrom(table: Element): NoteBlock {
        val rows = table.select("tr").map { tr ->
            tr.select("th, td").map { it.wholeText().replace('\u00A0', ' ').trim() }
        }.filter { it.isNotEmpty() }
        return NoteBlock(
            id = newId(),
            type = BlockType.TABLE,
            text = "",
            tableRows = rows.ifEmpty { listOf(listOf("", "")) },
        )
    }

    private fun alignmentOf(element: Element): BlockAlign {
        val style = (element.attr("style") + " " + element.parent()?.attr("style").orEmpty()).lowercase()
        return when {
            style.contains("text-align:center") || style.contains("text-align: center") -> BlockAlign.CENTER
            style.contains("text-align:right") || style.contains("text-align: right") -> BlockAlign.END
            else -> BlockAlign.START
        }
    }

    private fun indentOf(element: Element): Int {
        val attr = element.attr("data-indent").toIntOrNull()
        if (attr != null) return attr.coerceIn(0, 6)
        val style = element.attr("style") + " " + element.parent()?.attr("style").orEmpty()
        val px = MARGIN_LEFT.find(style)?.groupValues?.get(1)?.toFloatOrNull() ?: return 0
        return (px / 20f).toInt().coerceIn(0, 6)
    }

    private fun loneMediaChild(element: Element): NoteBlock? {
        element.selectFirst("img[src]")?.let { img ->
            if (inlineText(element).isBlank()) {
                return mediaFrom(img, "src", img.attr("alt") )
            }
        }
        element.selectFirst("audio[src], video[src]")?.let { media ->
            if (inlineText(element).isBlank()) {
                return mediaFrom(media, "src", media.attr("type").ifBlank { media.tagName() })
            }
        }
        element.selectFirst("object[data], embed[src], embed[data]")?.let { obj ->
            val attr = if (obj.hasAttr("data")) "data" else "src"
            return mediaFrom(obj, attr, obj.attr("type").ifBlank { obj.attr("name") })
        }
        val anchor = element.selectFirst("a[href]")
        if (anchor != null) {
            val href = anchor.attr("href")
            val label = inlineText(element)
            if (isFileHref(href) || (anchor.hasAttr("download") && (label.isBlank() || label == anchor.text()))) {
                return fileFromAnchor(anchor)
            }
        }
        return null
    }

    private fun mediaFrom(element: Element, attr: String, hint: String?): NoteBlock? {
        val src = element.attr(attr).ifBlank { element.attr("src") }.ifBlank { element.attr("data") }
        if (src.isBlank()) return null
        val hintMime = hint.orEmpty()
        val mime = when {
            hintMime.contains('/') -> hintMime
            src.startsWith("data:") -> src.substringAfter("data:").substringBefore(";").substringBefore(",")
            else -> hintMime.ifBlank { guessMime(src) }
        }
        val type = when {
            mime.startsWith("image/") || src.startsWith("data:image") || element.tagName() == "img" -> BlockType.IMAGE
            mime.startsWith("audio/") || src.startsWith("data:audio") || element.tagName() == "audio" -> BlockType.AUDIO
            mime.startsWith("video/") || src.startsWith("data:video") || element.tagName() == "video" -> BlockType.FILE
            else -> BlockType.FILE
        }
        val name = element.attr("download").ifBlank { element.attr("alt") }.ifBlank { fileNameFromSrc(src, mime) }
        return NoteBlock(
            id = newId(),
            type = type,
            text = src,
            mime = if (type == BlockType.FILE && name.isNotBlank()) "$mime|$name" else mime.ifBlank { null },
        )
    }

    private fun fileFromAnchor(anchor: Element): NoteBlock {
        val href = anchor.attr("href")
        val name = anchor.attr("download").ifBlank { anchor.text() }.ifBlank { fileNameFromSrc(href, "") }
        val mime = when {
            href.startsWith("data:") -> href.substringAfter("data:").substringBefore(";").substringBefore(",")
            else -> guessMime(name.ifBlank { href })
        }
        return NoteBlock(newId(), BlockType.FILE, href, mime = "$mime|$name")
    }

    private fun isFileHref(href: String): Boolean {
        if (href.startsWith("data:") && !href.startsWith("data:text/html")) return true
        val lower = href.lowercase()
        return lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".heic") ||
            lower.endsWith(".zip") || lower.endsWith(".doc") || lower.endsWith(".docx") ||
            lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".caf")
    }

    private fun fileNameOf(block: NoteBlock): String {
        val mime = block.mime.orEmpty()
        if ('|' in mime) return mime.substringAfter('|').ifBlank { "file" }
        if (mime.isNotBlank() && '/' !in mime) return mime
        return fileNameFromSrc(block.text, mime).ifBlank { "file" }
    }

    private fun fileNameFromSrc(src: String, mime: String): String {
        if (src.startsWith("data:")) {
            return when {
                mime.startsWith("image/") || src.startsWith("data:image") -> "image"
                mime.startsWith("audio/") || src.startsWith("data:audio") -> "audio"
                else -> "file"
            }
        }
        return src.substringAfterLast('/').substringBefore('?').ifBlank { "file" }
    }

    private fun guessMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".heic") -> "image/heic"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".caf") -> "audio/x-caf"
            else -> "application/octet-stream"
        }
    }

    private fun blockPlaintext(block: NoteBlock): String = when (block.type) {
        BlockType.TABLE -> block.tableRows.joinToString("\n") { row -> row.joinToString("\t") }
        BlockType.IMAGE -> "[Image]"
        BlockType.AUDIO -> "[Audio]"
        BlockType.FILE -> "[File ${fileNameOf(block)}]"
        BlockType.DIVIDER -> "—"
        else -> block.text
    }

    private fun displayText(block: NoteBlock): String = when (block.type) {
        BlockType.TABLE -> block.tableRows.joinToString("  |  ") { row -> row.joinToString(" · ") }
        BlockType.IMAGE -> block.mime?.substringAfter('|')?.ifBlank { "Image" } ?: "Image"
        BlockType.AUDIO -> "Audio"
        BlockType.FILE -> fileNameOf(block)
        BlockType.DIVIDER -> "————————"
        else -> block.text
    }

    fun preserveMedia(incoming: String, existing: String?): String {
        if (existing.isNullOrBlank()) return incoming
        if (DATA_URI.containsMatchIn(incoming)) return incoming
        val media = decode(existing).filter { block ->
            block.type == BlockType.IMAGE || block.type == BlockType.AUDIO || block.type == BlockType.FILE
        }.filter { it.text.startsWith("data:") }
        if (media.isEmpty()) return incoming
        val incomingBlocks = decode(incoming)
        if (incomingBlocks.any { it.text.startsWith("data:") }) return incoming
        return encode(incomingBlocks + media).html
    }

    private fun checklistPrefix(text: String): Pair<String, Boolean?> {
        val t = text.trimStart()
        return when {
            t.startsWith("☑") || t.startsWith("✓") || t.startsWith("✔") -> t.drop(1).trimStart() to true
            t.startsWith("☐") || t.startsWith("○") || t.startsWith("▢") -> t.drop(1).trimStart() to false
            else -> text to null
        }
    }

    private fun inlineText(element: Element): String = inlineContent(element).first

    private data class CssBits(
        val styles: Set<MarkStyle>,
        val color: String? = null,
        val highlight: String? = null,
        val fontSize: Float? = null,
    )

    private data class WalkStyle(
        val styles: Set<MarkStyle>,
        val href: String?,
        val color: String?,
        val highlight: String?,
        val fontSize: Float?,
    )

    private fun inlineContent(element: Element): Pair<String, List<TextMark>> {
        val text = StringBuilder()
        val marks = mutableListOf<TextMark>()
        fun walk(node: Node, state: WalkStyle) {
            when (node) {
                is TextNode -> {
                    val piece = node.wholeText.replace('\u00A0', ' ')
                    if (piece.isEmpty()) return
                    val start = text.length
                    text.append(piece)
                    val end = text.length
                    state.styles.forEach { style ->
                        marks += TextMark(
                            start = start,
                            end = end,
                            style = style,
                            href = if (style == MarkStyle.LINK || style == MarkStyle.NOTE_LINK) state.href else null,
                            color = if (style == MarkStyle.COLOR) state.color else null,
                            highlight = if (style == MarkStyle.HIGHLIGHT) state.highlight else null,
                            fontSizePx = if (style == MarkStyle.FONT_SIZE) state.fontSize else null,
                        )
                    }
                }
                is Element -> {
                    if (node.tagName().equals("br", ignoreCase = true)) {
                        text.append('\n')
                        return
                    }
                    if (node.tagName().equals("input", ignoreCase = true)) return
                    if (node.classNames().any { it.contains("mention", true) }) {
                        val next = state.styles + MarkStyle.MENTION
                        node.childNodes().forEach { walk(it, state.copy(styles = next)) }
                        return
                    }
                    val next = state.styles.toMutableSet()
                    var href = state.href
                    var color = state.color
                    var highlight = state.highlight
                    var fontSize = state.fontSize
                    when (node.tagName().lowercase()) {
                        "b", "strong" -> next += MarkStyle.BOLD
                        "i", "em" -> next += MarkStyle.ITALIC
                        "u" -> next += MarkStyle.UNDERLINE
                        "s", "strike", "del" -> next += MarkStyle.STRIKE
                        "a" -> {
                            href = node.attr("href").ifBlank { href }
                            val link = href.orEmpty()
                            next += if (link.startsWith("notes:") || link.startsWith("x-coredata:") || link.startsWith(">>")) {
                                MarkStyle.NOTE_LINK
                            } else {
                                MarkStyle.LINK
                            }
                        }
                    }
                    val css = cssMarks(node.attr("style"), node.classNames())
                    next += css.styles
                    if (css.color != null) {
                        next += MarkStyle.COLOR
                        color = css.color
                    }
                    if (css.highlight != null) {
                        next += MarkStyle.HIGHLIGHT
                        highlight = css.highlight
                    }
                    if (css.fontSize != null && css.fontSize !in listOf(11f, 13f, 15f, 21f, 24f)) {
                        next += MarkStyle.FONT_SIZE
                        fontSize = css.fontSize
                    }
                    val childState = WalkStyle(next, href, color, highlight, fontSize)
                    node.childNodes().forEach { walk(it, childState) }
                }
            }
        }
        element.childNodes().forEach { walk(it, WalkStyle(emptySet(), null, null, null, null)) }
        val raw = text.toString().trimEnd('\n')
        if (raw == "<br>" || raw == "\n") return "" to emptyList()
        return raw to marks
    }

    private fun inferParagraphType(element: Element): BlockType {
        if (isMono(element)) return BlockType.MONO
        val size = dominantFontSize(element) ?: return BlockType.BODY
        return when {
            size >= 19f -> BlockType.TITLE
            size >= 14.5f -> BlockType.HEADING
            size >= 13f -> BlockType.SUBHEADING
            else -> BlockType.BODY
        }
    }

    private fun dominantFontSize(element: Element): Float? {
        fontSizePx(element.attr("style"))?.let { return it }
        var current: Element? = element
        var last: Float? = null
        while (current != null) {
            fontSizePx(current.attr("style"))?.let { last = it }
            val kids = current.children().filter { it.tagName() != "br" && it.tagName() != "input" }
            current = kids.firstOrNull()
        }
        return last
    }

    private fun fontSizePx(style: String): Float? {
        val match = FONT_SIZE.find(style) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun isMono(element: Element): Boolean {
        val family = (element.attr("style") + " " + element.getAllElements().joinToString(" ") { it.attr("style") })
            .lowercase()
        return family.contains("menlo") || family.contains("monaco") ||
            family.contains("courier") || family.contains("monospace") || family.contains("sf mono")
    }

    private fun cssMarks(style: String, classes: Set<String>): CssBits {
        val out = mutableSetOf<MarkStyle>()
        val s = style.lowercase()
        if (s.contains("font-weight") && (s.contains("bold") || FONT_WEIGHT_BOLD.containsMatchIn(s))) {
            out += MarkStyle.BOLD
        }
        if (s.contains("italic")) out += MarkStyle.ITALIC
        if (s.contains("underline")) out += MarkStyle.UNDERLINE
        if (s.contains("line-through")) out += MarkStyle.STRIKE
        val color = parseCssColor(style, "color")?.takeUnless { it.equals("#000000", true) || it.equals("#1c1c1e", true) }
        val highlight = parseCssColor(style, "background-color")
            ?: parseCssColor(style, "background")
            ?: if (classes.any { it.contains("highlight", true) }) "#FFF2A8" else null
        val size = fontSizePx(style)
        return CssBits(out, color, highlight, size)
    }

    private fun parseCssColor(style: String, property: String): String? {
        val hex = Regex("$property:\\s*#([0-9a-fA-F]{3,8})").find(style)
        if (hex != null) {
            val raw = hex.groupValues[1]
            val full = if (raw.length == 3) raw.map { "$it$it" }.joinToString("") else raw.take(6)
            return "#$full"
        }
        val rgb = Regex("$property:\\s*rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").find(style) ?: return null
        val r = rgb.groupValues[1].toInt()
        val g = rgb.groupValues[2].toInt()
        val b = rgb.groupValues[3].toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun mergeInlineExtras(text: String, marks: List<TextMark>): List<TextMark> {
        val extras = mutableListOf<TextMark>()
        val links = marks.filter { it.style == MarkStyle.LINK || it.style == MarkStyle.NOTE_LINK }
        URL_REGEX.findAll(text).forEach { match ->
            val start = match.range.first
            val raw = match.value.trimEnd { it in ".,);]}" }
            val end = start + raw.length
            if (links.none { it.start <= start && it.end >= end }) {
                extras += TextMark(start, end, MarkStyle.LINK, href = raw)
            }
        }
        TAG_REGEX.findAll(text).forEach { match ->
            extras += TextMark(match.range.first, match.range.last + 1, MarkStyle.TAG)
        }
        MENTION_REGEX.findAll(text).forEach { match ->
            extras += TextMark(match.range.first, match.range.last + 1, MarkStyle.MENTION)
        }
        NOTE_LINK_REGEX.findAll(text).forEach { match ->
            val title = match.groupValues[1].trim()
            extras += TextMark(match.range.first, match.range.last + 1, MarkStyle.NOTE_LINK, href = "notes://$title")
        }
        return marks + extras
    }

    fun adjustMarks(marks: List<TextMark>, oldText: String, newText: String): List<TextMark> {
        if (oldText == newText || marks.isEmpty()) return marks
        var prefix = 0
        while (prefix < oldText.length && prefix < newText.length && oldText[prefix] == newText[prefix]) {
            prefix += 1
        }
        val delta = newText.length - oldText.length
        return marks.mapNotNull { mark ->
            var start = mark.start
            var end = mark.end
            when {
                end <= prefix -> mark
                start >= prefix -> {
                    start += delta
                    end += delta
                    if (start in 0 until end && end <= newText.length) mark.copy(start = start, end = end) else null
                }
                else -> {
                    end = (end + delta).coerceAtLeast(start)
                    if (end <= newText.length && start < end) mark.copy(end = end) else null
                }
            }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private val FONT_SIZE = Regex("font-size:\\s*([0-9.]+)px", RegexOption.IGNORE_CASE)
    private val FONT_WEIGHT_BOLD = Regex("font-weight:\\s*([6-9]00|bold)", RegexOption.IGNORE_CASE)
    private val MARGIN_LEFT = Regex("margin-left:\\s*([0-9.]+)px", RegexOption.IGNORE_CASE)
    private val URL_REGEX = Regex("https?://[^\\s<]+", RegexOption.IGNORE_CASE)
    private val TAG_REGEX = Regex("(?<![\\w/])#([A-Za-z][A-Za-z0-9_-]{1,40})")
    private val MENTION_REGEX = Regex("(?<![\\w/])@([\\p{L}][\\p{L}0-9._-]{0,40})")
    private val NOTE_LINK_REGEX = Regex(">>\\s*([^\\n<]{1,80})")
    private val DASH_DIVIDER = Regex("^[\\s]*[-–—_]{8,}[\\s]*$")
    private val DATA_URI = Regex("data:(image|audio|video|application)/", RegexOption.IGNORE_CASE)
}
