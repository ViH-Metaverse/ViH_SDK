package com.vihmessenger.vihchatbot.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression cover for the template body running together in the chat.
 *
 * A portal-authored body reaches the app in one of four shapes depending on which
 * portal/API path produced it. All four must end up with the author's paragraphs and
 * bullet lines intact — that is what the "Sample Preview" in the portal shows.
 */
class TemplateTextTest {

    private val expected = "Dear Customer,\n\nOffer details:\n• A\n• B"

    private val wireShapes = mapOf(
        "real newlines" to "Dear Customer,\n\nOffer details:\n• A\n• B\n",
        "br tags" to "Dear Customer,<br /><br />Offer details:<br/>• A<BR>• B",
        "double-escaped" to """Dear Customer,\n\nOffer details:\n• A\n• B""",
        "crlf" to "Dear Customer,\r\n\r\nOffer details:\r\n• A\r\n• B"
    )

    @Test
    fun `plain preserves the author's line breaks for every wire shape`() {
        for ((shape, raw) in wireShapes) {
            assertEquals("wire shape: $shape", expected, TemplateText.plain(raw))
        }
    }

    @Test
    fun `toHtmlSource promotes newlines to br so fromHtml cannot swallow them`() {
        // The bug: a real newline is whitespace to an HTML parser, so an un-promoted
        // body collapsed into one run-on paragraph.
        assertEquals(
            "Dear Customer,<br><br>Offer details:<br>• A",
            TemplateText.toHtmlSource("Dear Customer,\n\nOffer details:\n• A")
        )
        // Blank lines must survive as two adjacent <br>, not one.
        assertEquals("a<br><br>b", TemplateText.toHtmlSource("a\n\nb"))
        // Markup already in the body is left for fromHtml to decode.
        assertEquals("<b>Hi</b><br>there", TemplateText.toHtmlSource("<b>Hi</b>\nthere"))
    }

    @Test
    fun `blank and null bodies collapse to empty`() {
        assertEquals("", TemplateText.plain(null))
        assertEquals("", TemplateText.plain(""))
    }
}
