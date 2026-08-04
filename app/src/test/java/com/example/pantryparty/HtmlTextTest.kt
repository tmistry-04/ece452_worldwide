package com.example.pantryparty

import com.example.pantryparty.recipe.htmlParagraphs
import com.example.pantryparty.recipe.stripHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {

    @Test
    fun removesTagsAndDecodesEntities() {
        assertEquals("Salt & pepper", stripHtml("<b>Salt &amp; pepper</b>"))
    }

    @Test
    fun doesNotDoubleDecode() {
        // A single pass: "&amp;" becomes "&" and scanning resumes past it, so the
        // trailing "lt;" is never re-read as part of an entity.
        assertEquals("&lt;", stripHtml("&amp;lt;"))
    }

    @Test
    fun turnsListItemsIntoSeparateLines() {
        assertEquals(
            listOf("Boil the water.", "Add pasta.", "Serve."),
            htmlParagraphs("<ol><li>Boil the water.</li><li>Add pasta.</li><li>Serve.</li></ol>")
        )
    }

    @Test
    fun treatsBrAsALineBreak() {
        assertEquals(listOf("One", "Two"), htmlParagraphs("One<br/>Two"))
    }

    @Test
    fun decodesNumericAndHexEntities() {
        assertEquals("350°F", stripHtml("350&#176;F"))
        assertEquals("½ cup", stripHtml("&#xBD; cup"))
    }

    @Test
    fun leavesUnknownEntitiesAlone() {
        assertEquals("&notarealentity;", stripHtml("&notarealentity;"))
    }

    @Test
    fun collapsesRunsOfWhitespaceAndTrims() {
        assertEquals("Mash the bananas.", stripHtml("  Mash   the&nbsp;&nbsp;bananas.  "))
    }

    @Test
    fun isANoOpOnPlainText() {
        assertEquals("Mash the bananas.", stripHtml("Mash the bananas."))
    }

    @Test
    fun nullOrBlankInput_isEmpty() {
        assertEquals("", stripHtml(null))
        assertEquals("", stripHtml("   "))
        assertTrue(htmlParagraphs(null).isEmpty())
    }

    @Test
    fun paragraphs_dropBlankLines() {
        assertEquals(listOf("One", "Two"), htmlParagraphs("<p>One</p><p></p><p>Two</p>"))
    }
}
