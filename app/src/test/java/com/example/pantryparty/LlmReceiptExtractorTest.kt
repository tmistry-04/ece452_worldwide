package com.example.pantryparty

import com.example.pantryparty.receipt.LlmReceiptExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmReceiptExtractorTest {

    @Test
    fun parseResponse_readsAPlainJsonArray() {
        val lines = LlmReceiptExtractor.parseResponse(
            """[{"raw": "WHL MILK 2L 5.49", "name": "Whole  Milk", "quantity": 2, "unit": "l"}]"""
        )!!

        val line = lines.single()
        assertEquals("WHL MILK 2L 5.49", line.raw)
        assertEquals("whole milk", line.query)   // normalized: lowercased, spaces collapsed
        assertEquals(2, line.quantity)
        assertEquals("l", line.unitHint)
    }

    @Test
    fun parseResponse_survivesFencesAndProse() {
        val lines = LlmReceiptExtractor.parseResponse(
            "Here are the items:\n```json\n" +
                """[{"raw": "BANANAS", "name": "bananas", "quantity": 1}]""" +
                "\n```\nLet me know if you need anything else!"
        )!!

        assertEquals("bananas", lines.single().query)
        assertNull(lines.single().unitHint)
    }

    @Test
    fun parseResponse_isNullForAnythingWithoutAJsonArray() {
        assertNull(LlmReceiptExtractor.parseResponse("Sorry, I can't read this receipt."))
        assertNull(LlmReceiptExtractor.parseResponse("""{"name": "not an array"}"""))
        assertNull(LlmReceiptExtractor.parseResponse("[this is not json]"))
    }

    @Test
    fun parseResponse_emptyArrayIsAnEmptyList_notNull() {
        // "[]" is the model saying "no products here" — a valid answer, distinct
        // from an unusable reply.
        assertEquals(emptyList<Any>(), LlmReceiptExtractor.parseResponse("[]"))
    }

    @Test
    fun parseResponse_validatesEveryFieldInsteadOfTrustingTheModel() {
        val lines = LlmReceiptExtractor.parseResponse(
            """
            [
              {"raw": "A", "name": "  ", "quantity": 1},
              {"raw": "B", "name": "butter", "quantity": 0, "unit": "sticks"},
              {"raw": "C", "name": "eggs", "quantity": 500, "unit": "PIECE"},
              {"raw": "", "name": "flour", "quantity": 1, "unit": "kg"}
            ]
            """.trimIndent()
        )!!

        assertEquals(listOf("butter", "eggs", "flour"), lines.map { it.query })  // blank name dropped
        assertEquals(1, lines[0].quantity)          // 0 clamped up
        assertNull(lines[0].unitHint)               // unknown unit discarded
        assertEquals(99, lines[1].quantity)         // runaway count clamped down
        assertEquals("piece", lines[1].unitHint)    // unit casing normalized
        assertEquals("flour", lines[2].raw)         // blank raw falls back to the name
    }

    @Test
    fun userPrompt_joinsTheOcrLines() {
        val prompt = LlmReceiptExtractor.userPrompt(listOf("LINE ONE", "LINE TWO"))
        assertEquals("LINE ONE\nLINE TWO", prompt)
    }

    @Test
    fun userPrompt_capsPathologicalInput() {
        val prompt = LlmReceiptExtractor.userPrompt(List(1_000) { "x".repeat(100) })
        assertTrue(prompt.length <= 8_000)
    }
}
