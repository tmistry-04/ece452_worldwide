package com.example.pantryparty.receipt

/**
 * One candidate grocery item recovered from a receipt.
 *
 * [raw] keeps the printed text the item came from, so the review screen can always
 * show what was actually scanned; the cleaned [query] is the model's best effort
 * and is sometimes wrong — which is why every line is confirmed by the user before
 * anything is written to the pantry.
 */
data class ReceiptLine(
    val raw: String,
    val query: String,
    val quantity: Int,
    val unitHint: String?
)
