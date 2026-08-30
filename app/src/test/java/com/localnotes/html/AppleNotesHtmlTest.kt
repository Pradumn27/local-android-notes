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
}
