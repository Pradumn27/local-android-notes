package com.localnotes.html

import com.localnotes.data.html.AppleNotesHtml
import com.localnotes.data.model.BlockType
import com.localnotes.data.model.NoteBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleNotesHtmlTest {

    @Test
    fun encodesAppleDashList() {
        val blocks = listOf(
            NoteBlock("1", BlockType.TITLE, "What all to do:"),
            NoteBlock("2", BlockType.BULLET, "Learncpp"),
            NoteBlock("3", BlockType.BULLET, "Learn about taxes"),
        )
        val html = AppleNotesHtml.encode(blocks).html
        assertTrue(html.contains("font-size: 21px"))
        assertTrue(html.contains("class=\"Apple-dash-list\""))
        assertTrue(html.contains("Learncpp"))
    }

    @Test
    fun decodesMacNotesBody() {
        val html = """
            <div>What all to do:</div>
            <ul class="Apple-dash-list">
            <li>Learncpp</li>
            <li>Learn about taxes</li>
            </ul>
            <div>Currently Focusing on:</div>
            <ul class="Apple-dash-list">
            <li>Sync of Mac and android</li>
            </ul>
        """.trimIndent()
        val blocks = AppleNotesHtml.decode(html)
        assertEquals(BlockType.TITLE, blocks.first().type)
        assertEquals("What all to do:", blocks.first().text)
        assertTrue(blocks.any { it.type == BlockType.BULLET && it.text == "Learncpp" })
        assertTrue(blocks.any { it.text == "Currently Focusing on:" })
        assertTrue(blocks.any { it.type == BlockType.BULLET && it.text == "Sync of Mac and android" })
    }

    @Test
    fun checklistRoundTrip() {
        val original = listOf(
            NoteBlock("1", BlockType.TITLE, "Groceries"),
            NoteBlock("2", BlockType.CHECKLIST, "Milk", checked = true),
            NoteBlock("3", BlockType.CHECKLIST, "Eggs", checked = false),
        )
        val encoded = AppleNotesHtml.encode(original)
        val decoded = AppleNotesHtml.decode(encoded.html)
        assertEquals("Groceries", decoded[0].text)
        assertEquals(BlockType.CHECKLIST, decoded[1].type)
        assertTrue(decoded[1].checked)
        assertEquals("Eggs", decoded[2].text)
        assertEquals(false, decoded[2].checked)
        assertEquals("Groceries", encoded.title)
    }

    @Test
    fun previewSkipsTitle() {
        val preview = AppleNotesHtml.preview("Hello\nWorld of notes", "Hello")
        assertEquals("World of notes", preview)
    }

    @Test
    fun infersAppleHeadingSizesAndUnderline() {
        val html = """
            <div><b><span style="font-size: 21px">Nice</span></b></div>
            <div><b><span style="font-size: 15px">On this phone</span></b></div>
            <div><u><span style="font-size: 11px">underlined bit</span></u></div>
            <div><span style="font-size: 11px">see https://example.com/path for more</span></div>
        """.trimIndent()
        val blocks = AppleNotesHtml.decode(html)
        assertEquals(BlockType.TITLE, blocks[0].type)
        assertEquals("Nice", blocks[0].text)
        assertEquals(BlockType.HEADING, blocks[1].type)
        assertEquals("On this phone", blocks[1].text)
        assertTrue(blocks[2].marks.any { it.style == com.localnotes.data.model.MarkStyle.UNDERLINE })
        assertTrue(blocks[3].marks.any { it.style == com.localnotes.data.model.MarkStyle.LINK && it.href?.contains("example.com") == true })
    }

    @Test
    fun displayLinesKeepsListShape() {
        val html = """
            <div>What all to do:</div>
            <ul class="Apple-dash-list">
            <li>Learncpp</li>
            <li>Learn about taxes</li>
            </ul>
            <ul class="Apple-checklist">
            <li class="checked">Done item</li>
            <li>Open item</li>
            </ul>
        """.trimIndent()
        val lines = AppleNotesHtml.displayLines(html, "What all to do:")
        assertEquals("–  Learncpp", lines[0].text)
        assertEquals("–  Learn about taxes", lines[1].text)
        assertEquals("☑  Done item", lines[2].text)
        assertEquals("○  Open item", lines[3].text)
        assertEquals(BlockType.BULLET, lines[0].type)
        assertEquals(BlockType.CHECKLIST, lines[2].type)
    }

    @Test
    fun colorAlignHighlightAndCollapseRoundTrip() {
        val original = listOf(
            NoteBlock(
                "1",
                BlockType.TITLE,
                "Painted",
                align = com.localnotes.data.model.BlockAlign.CENTER,
                collapsed = true,
            ),
            NoteBlock(
                "2",
                BlockType.BODY,
                "red and yellow",
                marks = listOf(
                    com.localnotes.data.model.TextMark(0, 3, com.localnotes.data.model.MarkStyle.COLOR, color = "#FF3B30"),
                    com.localnotes.data.model.TextMark(8, 14, com.localnotes.data.model.MarkStyle.HIGHLIGHT, highlight = "#FFF2A8"),
                ),
                indent = 1,
            ),
        )
        val html = AppleNotesHtml.encode(original).html
        assertTrue(html.contains("text-align: center"))
        assertTrue(html.contains("data-collapsed=\"true\""))
        assertTrue(html.contains("color: #FF3B30"))
        assertTrue(html.contains("background-color: #FFF2A8"))
        assertTrue(html.contains("margin-left: 20px"))
        val decoded = AppleNotesHtml.decode(html)
        assertEquals(BlockType.TITLE, decoded[0].type)
        assertEquals(com.localnotes.data.model.BlockAlign.CENTER, decoded[0].align)
        assertTrue(decoded[0].collapsed)
        assertTrue(decoded[1].marks.any { it.style == com.localnotes.data.model.MarkStyle.COLOR && it.color == "#FF3B30" })
        assertTrue(decoded[1].marks.any { it.style == com.localnotes.data.model.MarkStyle.HIGHLIGHT && it.highlight == "#FFF2A8" })
        assertEquals(1, decoded[1].indent)
    }

    @Test
    fun tableImageFileAndDividerRoundTrip() {
        val original = listOf(
            NoteBlock("1", BlockType.TITLE, "Media"),
            NoteBlock("2", BlockType.TABLE, "", tableRows = listOf(listOf("A", "B"), listOf("1", "2"))),
            NoteBlock("3", BlockType.IMAGE, "data:image/jpeg;base64,abcd", mime = "image/jpeg"),
            NoteBlock("4", BlockType.FILE, "data:application/pdf;base64,abcd", mime = "application/pdf|tax.pdf"),
            NoteBlock("5", BlockType.DIVIDER, ""),
            NoteBlock("6", BlockType.AUDIO, "data:audio/mp4;base64,abcd", mime = "audio/mp4"),
        )
        val encoded = AppleNotesHtml.encode(original)
        assertTrue(encoded.html.contains("<table"))
        assertTrue(encoded.html.contains("<img"))
        assertTrue(encoded.html.contains("download=\"tax.pdf\""))
        assertTrue(encoded.html.contains("<hr"))
        assertTrue(encoded.html.contains("<audio"))
        assertTrue(encoded.plaintext.contains("[Image]"))
        val decoded = AppleNotesHtml.decode(encoded.html)
        assertTrue(decoded.any { it.type == BlockType.TABLE && it.tableRows[0][0] == "A" })
        assertTrue(decoded.any { it.type == BlockType.IMAGE && it.text.startsWith("data:image") })
        assertTrue(decoded.any { it.type == BlockType.FILE && it.text.startsWith("data:application/pdf") })
        assertTrue(decoded.any { it.type == BlockType.DIVIDER })
        assertTrue(decoded.any { it.type == BlockType.AUDIO })
    }

    @Test
    fun tagsMentionsNoteLinksAndDashDivider() {
        val html = """
            <div><span style="font-size: 11px">See >> Groceries and #taxes with @Ada</span></div>
            <div>——————————</div>
            <div><a href="data:application/pdf;base64,QQ==" download="scan.pdf">scan.pdf</a></div>
        """.trimIndent()
        val blocks = AppleNotesHtml.decode(html)
        val body = blocks.first { it.text.contains("Groceries") }
        assertTrue(body.marks.any { it.style == com.localnotes.data.model.MarkStyle.NOTE_LINK && it.href?.contains("Groceries") == true })
        assertTrue(body.marks.any { it.style == com.localnotes.data.model.MarkStyle.TAG })
        assertTrue(body.marks.any { it.style == com.localnotes.data.model.MarkStyle.MENTION })
        assertTrue(blocks.any { it.type == BlockType.DIVIDER })
        assertTrue(blocks.any { it.type == BlockType.FILE && it.text.startsWith("data:application/pdf") })
    }

    @Test
    fun displayLinesShowsTablesAndMedia() {
        val html = AppleNotesHtml.encode(
            listOf(
                NoteBlock("1", BlockType.TITLE, "Album"),
                NoteBlock("2", BlockType.TABLE, "", tableRows = listOf(listOf("left", "right"))),
                NoteBlock("3", BlockType.IMAGE, "data:image/jpeg;base64,xx", mime = "image/jpeg"),
                NoteBlock("4", BlockType.DIVIDER, ""),
            ),
        ).html
        val lines = AppleNotesHtml.displayLines(html, "Album")
        assertTrue(lines.any { it.type == BlockType.TABLE && it.text.contains("left") })
        assertTrue(lines.any { it.type == BlockType.IMAGE })
        assertTrue(lines.any { it.type == BlockType.DIVIDER })
    }

    @Test
    fun preserveMediaKeepsDataUrisAcrossLiveHtml() {
        val withPhoto = AppleNotesHtml.encode(
            listOf(
                NoteBlock("1", BlockType.TITLE, "Shot"),
                NoteBlock("2", BlockType.IMAGE, "data:image/jpeg;base64,qq", mime = "image/jpeg"),
            ),
        ).html
        val live = """<div><b><span style="font-size: 21px">Shot</span></b></div><div>typed more</div>"""
        val merged = AppleNotesHtml.preserveMedia(live, withPhoto)
        val blocks = AppleNotesHtml.decode(merged)
        assertTrue(blocks.any { it.text.contains("typed more") })
        assertTrue(blocks.any { it.type == BlockType.IMAGE && it.text.startsWith("data:image") })
    }

    @Test
    fun mixedFontSizeDoesNotPromoteWholeParagraph() {
        val html = """
            <div><b><span style="font-size: 21px">Nice</span></b></div>
            <div><span style="font-size: 11px">hello </span><span style="font-size: 28px">BIG</span></div>
        """.trimIndent()
        val blocks = AppleNotesHtml.decode(html)
        assertEquals(BlockType.TITLE, blocks[0].type)
        assertEquals(BlockType.BODY, blocks[1].type)
        assertEquals("hello BIG", blocks[1].text)
        assertTrue(blocks[1].marks.any { it.style == com.localnotes.data.model.MarkStyle.FONT_SIZE && it.fontSizePx == 28f })
    }
}
