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
        val tag = when (block.type) {
            BlockType.TITLE -> "h1"
            BlockType.HEADING -> "h2"
            BlockType.SUBHEADING -> "h3"
            BlockType.MONO -> "pre"
            else -> "div"
        }
        return "<$tag>$inner</$tag>"
    }

    private fun renderList(tag: String, cssClass: String?, items: List<NoteBlock>): String {
        val cls = cssClass?.let { " class=\"$it\"" }.orEmpty()
        val lis = items.joinToString("") { item ->
            val pad = if (item.indent > 0) " data-indent=\"${item.indent}\"" else ""
            "<li$pad>${applyMarks(item.text, item.marks).ifBlank { "<br>" }}</li>"
        }
        return "<$tag$cls>$lis</$tag>"
    }

    private fun renderChecklist(items: List<NoteBlock>): String {
        val lis = items.joinToString("") { item ->
            val checked = if (item.checked) " class=\"checked\"" else ""
            val pad = if (item.indent > 0) " data-indent=\"${item.indent}\"" else ""
            "<li$checked$pad>${applyMarks(item.text, item.marks).ifBlank { "<br>" }}</li>"
        }
        return "<ul class=\"Apple-checklist\">$lis</ul>"
    }

    private fun applyMarks(text: String, marks: List<TextMark>): String {
        if (text.isEmpty()) return ""
        if (marks.isEmpty()) return escape(text)
        val opens = Array(text.length + 1) { mutableListOf<MarkStyle>() }
        val closes = Array(text.length + 1) { mutableListOf<MarkStyle>() }
        marks.filter { it.start < it.end && it.start >= 0 && it.end <= text.length }
            .forEach { mark ->
                opens[mark.start] += mark.style
                closes[mark.end] += mark.style
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

    private fun openTag(style: MarkStyle): String = when (style) {
        MarkStyle.BOLD -> "<b>"
        MarkStyle.ITALIC -> "<i>"
        MarkStyle.UNDERLINE -> "<u>"
        MarkStyle.STRIKE -> "<s>"
        MarkStyle.HIGHLIGHT -> "<span class=\"Apple-highlight\">"
    }

    private fun closeTag(style: MarkStyle): String = when (style) {
        MarkStyle.BOLD -> "</b>"
        MarkStyle.ITALIC -> "</i>"
        MarkStyle.UNDERLINE -> "</u>"
        MarkStyle.STRIKE -> "</s>"
        MarkStyle.HIGHLIGHT -> "</span>"
    }

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
                    "div", "p", "span" -> {
                        if (node.children().any { it.tagName() in blockTags }) {
                            node.childNodes().forEach { collectBlocks(it, into) }
                        } else {
                            val text = inlineText(node)
                            if (text.isNotBlank() || node.getElementsByTag("br").isNotEmpty()) {
                                into += paragraphFrom(node, BlockType.BODY)
                            }
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
                                marks = marks,
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
                                marks = marks,
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
        return NoteBlock(newId(), type, text, marks = marks)
    }

    private fun inlineText(element: Element): String = inlineContent(element).first

    private fun inlineContent(element: Element): Pair<String, List<TextMark>> {
        val text = StringBuilder()
        val marks = mutableListOf<TextMark>()
        fun walk(node: Node, styles: Set<MarkStyle>) {
            when (node) {
                is TextNode -> {
                    val piece = node.wholeText.replace('\u00A0', ' ')
                    if (piece.isEmpty()) return
                    val start = text.length
                    text.append(piece)
                    styles.forEach { marks += TextMark(start, text.length, it) }
                }
                is Element -> {
                    if (node.tagName().equals("br", ignoreCase = true)) {
                        text.append('\n')
                        return
                    }
                    if (node.tagName().equals("input", ignoreCase = true)) return
                    val next = styles.toMutableSet()
                    when (node.tagName().lowercase()) {
                        "b", "strong" -> next += MarkStyle.BOLD
                        "i", "em" -> next += MarkStyle.ITALIC
                        "u" -> next += MarkStyle.UNDERLINE
                        "s", "strike", "del" -> next += MarkStyle.STRIKE
                        "span" -> if (node.classNames().any { it.contains("highlight", true) } ||
                            node.attr("style").contains("background", true)
                        ) {
                            next += MarkStyle.HIGHLIGHT
                        }
                    }
                    node.childNodes().forEach { walk(it, next) }
                }
            }
        }
        element.childNodes().forEach { walk(it, emptySet()) }
        val raw = text.toString().trimEnd('\n')
        if (raw == "<br>" || raw == "\n") return "" to emptyList()
        return raw to marks
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
