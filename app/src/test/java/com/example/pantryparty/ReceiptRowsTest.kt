package com.example.pantryparty

import com.example.pantryparty.receipt.OcrTextRun
import com.example.pantryparty.receipt.rowsFromTextRuns
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading-order reconstruction. OCR hands back loose text runs in block order, which on
 * a receipt separates each item name from its price and does not reliably run top to
 * bottom — so these cases are what stands between the parser and garbage input.
 */
class ReceiptRowsTest {

    private fun run(text: String, left: Int, top: Int, height: Int = 20) =
        OcrTextRun(text = text, left = left, top = top, bottom = top + height)

    @Test
    fun runsOnTheSameBaseline_mergeIntoOneRow() {
        // The classic receipt split: name on the left, price on the right.
        val rows = rowsFromTextRuns(
            listOf(
                run("4.99", left = 400, top = 100),
                run("WHOLE MILK 2L", left = 20, top = 102)
            )
        )
        assertEquals(listOf("WHOLE MILK 2L 4.99"), rows)
    }

    @Test
    fun rowsAreOrderedTopToBottom_regardlessOfBlockOrder() {
        val rows = rowsFromTextRuns(
            listOf(
                run("TOTAL", left = 20, top = 300),
                run("BANANAS", left = 20, top = 100),
                run("MILK", left = 20, top = 200)
            )
        )
        assertEquals(listOf("BANANAS", "MILK", "TOTAL"), rows)
    }

    @Test
    fun runsWithinARow_areOrderedLeftToRight() {
        val rows = rowsFromTextRuns(
            listOf(
                run("H", left = 500, top = 100),
                run("2.49", left = 380, top = 100),
                run("GRN GIANT SWT CRN", left = 20, top = 100)
            )
        )
        assertEquals(listOf("GRN GIANT SWT CRN 2.49 H"), rows)
    }

    @Test
    fun separateLines_staySeparate() {
        // A full line-height apart is a new row, not baseline wobble.
        val rows = rowsFromTextRuns(
            listOf(
                run("MILK", left = 20, top = 100),
                run("BREAD", left = 20, top = 130)
            )
        )
        assertEquals(listOf("MILK", "BREAD"), rows)
    }

    @Test
    fun toleranceScalesWithTextHeight() {
        // Large text photographed up close wobbles more in absolute pixels, but the
        // two runs are still visually on one line.
        val rows = rowsFromTextRuns(
            listOf(
                run("CHEESE", left = 20, top = 100, height = 60),
                run("8.49", left = 400, top = 118, height = 60)
            )
        )
        assertEquals(listOf("CHEESE 8.49"), rows)
    }

    @Test
    fun blankRuns_areIgnored() {
        val rows = rowsFromTextRuns(
            listOf(
                run("   ", left = 20, top = 100),
                run("EGGS", left = 20, top = 140)
            )
        )
        assertEquals(listOf("EGGS"), rows)
    }

    @Test
    fun emptyInput_yieldsNoRows() {
        assertEquals(emptyList<String>(), rowsFromTextRuns(emptyList()))
    }
}
