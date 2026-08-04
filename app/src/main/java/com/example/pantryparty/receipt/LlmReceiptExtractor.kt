package com.example.pantryparty.receipt

import com.example.pantryparty.recipe.normalizeIngredientName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Prompt and response handling for LLM receipt parsing — the app's only receipt
 * parser. Pure, with no Android or network dependencies, so it is unit-testable.
 *
 * The model turns OCR text into [ReceiptLine]s: expanding shorthand, rejecting
 * bookkeeping lines, and merging repeats. Its output is validated here, never
 * trusted: names are normalized, quantities clamped, units checked against the
 * set the rest of the pipeline understands. Everything downstream (Spoonacular
 * resolution, review, commit) stays deterministic, and the user still confirms
 * every row before anything is written to the pantry.
 */
object LlmReceiptExtractor {

    /** The units the rest of the pipeline understands (a subset of Spoonacular's unit vocabulary). */
    private val ALLOWED_UNITS = setOf("g", "kg", "lb", "oz", "ml", "l", "package", "piece")

    // Model output can drown a response in prose or fences; parsing is anchored on
    // the outermost JSON array instead of trusting the instruction was followed.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /** What the model is asked to produce for each purchased product. */
    @Serializable
    private data class LlmItem(
        val raw: String = "",
        val name: String = "",
        val quantity: Int = 1,
        val unit: String? = null
    )

    val SYSTEM_PROMPT = """
        You extract grocery purchases from the OCR text of a store receipt.

        Reply with ONLY a JSON array — no prose, no markdown fences. One element per purchased product:
        {"raw": "<the receipt line the product appears on>", "name": "<clean product name>", "quantity": <whole units purchased>, "unit": "<g|kg|lb|oz|ml|l|package|piece>"}

        Rules:
        - "name": lowercase generic food name as sold. Expand receipt shorthand (WHL MILK -> whole milk, TOM ROMA -> roma tomato). Drop brand names, sizes, prices, tax flags, and product codes.
        - "quantity": how many the shopper bought. Repeated identical lines are separate units of one product: output ONE element with the summed quantity. A pack size like 12x355ml is ONE package, not 12.
        - "unit": the size unit printed on the line if any, else "package" or "piece" for countable goods. Only the listed values, or null.
        - Skip everything that is not a purchased product: store name and address, dates, cashier and register codes, subtotal, tax, total, payment, change, loyalty points, coupons, deposit fees.
        - OCR is noisy: fix obvious character confusions when the intended word is clear. Skip unreadable lines.
        - If the text contains no purchased products, reply with [].
    """.trimIndent()

    // A receipt is short; these caps only guard against OCR of something that isn't
    // a receipt at all blowing up the request.
    private const val MAX_LINES = 200
    private const val MAX_CHARS = 8_000

    fun userPrompt(lines: List<String>): String =
        lines.take(MAX_LINES).joinToString("\n").take(MAX_CHARS)

    /**
     * The model's reply as validated [ReceiptLine]s, or null when no usable JSON
     * array can be recovered — which the caller reports as a failed scan.
     */
    fun parseResponse(content: String): List<ReceiptLine>? {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val items = try {
            json.decodeFromString<List<LlmItem>>(content.substring(start, end + 1))
        } catch (_: Exception) {
            return null
        }
        return items.mapNotNull { item ->
            val query = normalizeIngredientName(item.name)
            if (query.isEmpty()) return@mapNotNull null
            ReceiptLine(
                raw = item.raw.trim().ifEmpty { item.name.trim() },
                query = query,
                quantity = item.quantity.coerceIn(1, 99),
                unitHint = item.unit?.trim()?.lowercase()?.takeIf { it in ALLOWED_UNITS }
            )
        }
    }
}
