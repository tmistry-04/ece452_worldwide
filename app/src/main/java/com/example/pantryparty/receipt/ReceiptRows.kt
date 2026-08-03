package com.example.pantryparty.receipt

import kotlin.math.abs

/**
 * One run of recognized text with just enough geometry to rebuild reading order.
 *
 * Deliberately not an ML Kit type: keeping the geometry in plain ints lets
 * [rowsFromTextRuns] be unit-tested without a device or a vision library.
 */
internal data class OcrTextRun(
    val text: String,
    val left: Int,
    val top: Int,
    val bottom: Int
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * Rebuilds receipt rows from loose OCR text runs.
 *
 * OCR does not hand back receipt rows. A wide gap between an item name and its price
 * usually splits them into separate text blocks, and block order does not reliably
 * follow reading order down a long receipt. Left unfixed, "MILK 2L" and "4.99" arrive
 * as unrelated entries and the parser loses the quantity/size association entirely.
 *
 * Runs whose vertical centres line up are therefore regrouped into a single row and
 * ordered left-to-right. The tolerance scales with text height rather than being a
 * fixed pixel count, so it holds regardless of how close the camera was.
 */
internal fun rowsFromTextRuns(runs: List<OcrTextRun>): List<String> {
    val sorted = runs.filter { it.text.isNotBlank() }.sortedBy { it.centerY }
    val rows = mutableListOf<MutableList<OcrTextRun>>()

    for (run in sorted) {
        // Compare against the row's first run, not its last, so a row of slightly
        // drifting baselines can't creep into the row below it.
        val anchor = rows.lastOrNull()?.firstOrNull()
        if (anchor != null && abs(run.centerY - anchor.centerY) <= sameRowTolerance(anchor, run)) {
            rows.last().add(run)
        } else {
            rows.add(mutableListOf(run))
        }
    }

    return rows.map { row ->
        row.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
    }
}

/** Half the shorter run's height — tall text tolerates more baseline wobble. */
private fun sameRowTolerance(a: OcrTextRun, b: OcrTextRun): Int =
    (minOf(a.height, b.height) / 2).coerceAtLeast(1)
