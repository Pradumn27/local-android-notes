package com.localnotes.data.html

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
 * interchange format for the future Mac AppleScript sync.
 *
 * Supported subset:
 *  - <h1> title, <h2> heading, <h3> subheading
 *  - <div> / <p> body
 *  - <pre> monostyled
 *  - <ul class="Apple-dash-list"> bullets
 *  - <ol> numbered
 *  - <ul class="Apple-checklist"><li class="checked"> checklists
 *  - <b> <i> <u> <s> and highlight spans
 *
 * Incoming Notes HTML that uses a leading <div> as the title (common)
 * is treated as TITLE when it is the first block.
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
                    else -> {
                        append(renderParagraph(block))
                        index += 1
                    }
                }
            }
        }
        val plaintext = normalized.joinToString("\n") { it.text }.trimEnd()
        val title = normalized.firstOrNull { it.text.isNotBlank() }?.text
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
            if (block.text.isBlank() && block.type != BlockType.CHECKLIST) continue
            if (block.type == BlockType.NUMBERED) number += 1 else number = 0
            val indent = "    ".repeat(block.indent.coerceIn(0, 4))
            val prefix = when (block.type) {
                BlockType.BULLET -> "–  "
                BlockType.NUMBERED -> "$number. "
                BlockType.CHECKLIST -> if (block.checked) "☑  " else "○  "
                else -> ""
            }
            lines += DisplayLine(
                text = indent + prefix + block.text,
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
        return if (block.type == BlockType.MONO) sized else "<div>$sized</div>"
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
        MarkStyle.HIGHLIGHT -> "<span class=\"Apple-highlight\">"
        MarkStyle.LINK -> {
            val href = escapeAttr(mark.href ?: "")
            "<a href=\"$href\">"
        }
    }

    private fun closeTag(mark: TextMark): String = when (mark.style) {
        MarkStyle.BOLD -> "</b>"
        MarkStyle.ITALIC -> "</i>"
        MarkStyle.UNDERLINE -> "</u>"
        MarkStyle.STRIKE -> "</strike>"
        MarkStyle.HIGHLIGHT -> "</span>"
        MarkStyle.LINK -> "</a>"
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
                            val checked = li.hasClass("checked") ||
                                li.selectFirst("input[type=checkbox]")?.hasAttr("checked") == true
                            val (text, marks) = inlineContent(li)
                            into += NoteBlock(
                                id = newId(),
                                type = if (isChecklist) BlockType.CHECKLIST else BlockType.BULLET,
                                text = text,
                                checked = checked,
                                indent = li.attr("data-indent").toIntOrNull() ?: 0,
                                marks = mergeUrlMarks(text, marks),
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
                                indent = li.attr("data-indent").toIntOrNull() ?: 0,
                                marks = mergeUrlMarks(text, marks),
                            )
                        }
                    }
                    "br" -> into += NoteBlock(newId(), BlockType.BODY, "")
                    else -> node.childNodes().forEach { collectBlocks(it, into) }
                }
            }
        }
    }

    private val blockTags = setOf("div", "p", "h1", "h2", "h3", "pre", "ul", "ol")

    private fun paragraphFrom(element: Element, type: BlockType): NoteBlock {
        val (text, marks) = inlineContent(element)
        val heading = type == BlockType.TITLE || type == BlockType.HEADING || type == BlockType.SUBHEADING
        val cleaned = if (heading) {
            marks.filterNot { it.style == MarkStyle.BOLD && it.start == 0 && it.end == text.length }
        } else {
            marks
        }
        return NoteBlock(newId(), type, text, marks = mergeUrlMarks(text, cleaned))
    }

    private fun inlineText(element: Element): String = inlineContent(element).first

    private fun inlineContent(element: Element): Pair<String, List<TextMark>> {
        val text = StringBuilder()
        val marks = mutableListOf<TextMark>()
        data class Style(val styles: Set<MarkStyle>, val href: String?)
        fun walk(node: Node, state: Style) {
            when (node) {
                is TextNode -> {
                    val piece = node.wholeText.replace('\u00A0', ' ')
                    if (piece.isEmpty()) return
                    val start = text.length
                    text.append(piece)
                    state.styles.forEach { style ->
                        marks += TextMark(start, text.length, style, href = if (style == MarkStyle.LINK) state.href else null)
                    }
                }
                is Element -> {
                    if (node.tagName().equals("br", ignoreCase = true)) {
                        text.append('\n')
                        return
                    }
                    if (node.tagName().equals("input", ignoreCase = true)) return
                    val next = state.styles.toMutableSet()
                    var href = state.href
                    when (node.tagName().lowercase()) {
                        "b", "strong" -> next += MarkStyle.BOLD
                        "i", "em" -> next += MarkStyle.ITALIC
                        "u" -> next += MarkStyle.UNDERLINE
                        "s", "strike", "del" -> next += MarkStyle.STRIKE
                        "a" -> {
                            next += MarkStyle.LINK
                            href = node.attr("href").ifBlank { href }
                        }
                    }
                    next += cssMarks(node.attr("style"), node.classNames())
                    node.childNodes().forEach { walk(it, Style(next, href)) }
                }
            }
        }
        element.childNodes().forEach { walk(it, Style(emptySet(), null)) }
        val raw = text.toString().trimEnd('\n')
        if (raw == "<br>" || raw == "\n") return "" to emptyList()
        return raw to marks
    }

    private fun inferParagraphType(element: Element): BlockType {
        if (isMono(element)) return BlockType.MONO
        val size = maxFontSize(element) ?: return BlockType.BODY
        return when {
            size >= 19f -> BlockType.TITLE
            size >= 14.5f -> BlockType.HEADING
            size >= 13f -> BlockType.SUBHEADING
            else -> BlockType.BODY
        }
    }

    private fun maxFontSize(element: Element): Float? {
        val sizes = mutableListOf<Float>()
        fontSizePx(element.attr("style"))?.let { sizes += it }
        element.getAllElements().forEach { child ->
            fontSizePx(child.attr("style"))?.let { sizes += it }
        }
        return sizes.maxOrNull()
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

    private fun cssMarks(style: String, classes: Set<String>): Set<MarkStyle> {
        val out = mutableSetOf<MarkStyle>()
        val s = style.lowercase()
        if (s.contains("font-weight") && (s.contains("bold") || FONT_WEIGHT_BOLD.containsMatchIn(s))) {
            out += MarkStyle.BOLD
        }
        if (s.contains("italic")) out += MarkStyle.ITALIC
        if (s.contains("underline")) out += MarkStyle.UNDERLINE
        if (s.contains("line-through")) out += MarkStyle.STRIKE
        if (s.contains("background") || classes.any { it.contains("highlight", true) }) {
            out += MarkStyle.HIGHLIGHT
        }
        return out
    }

    private fun mergeUrlMarks(text: String, marks: List<TextMark>): List<TextMark> {
        val existing = marks.filter { it.style == MarkStyle.LINK }
        val extras = URL_REGEX.findAll(text).mapNotNull { match ->
            val start = match.range.first
            val raw = match.value.trimEnd { it in ".,);]}" }
            val end = start + raw.length
            if (existing.any { it.start <= start && it.end >= end }) null
            else TextMark(start, end, MarkStyle.LINK, href = raw)
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
    private val URL_REGEX = Regex("https?://[^\\s<]+", RegexOption.IGNORE_CASE)
}
