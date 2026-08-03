package com.example.pantryparty.receipt

/**
 * One candidate grocery item recovered from a receipt.
 *
 * [raw] keeps the printed text — trimmed, with runs of spaces collapsed so the receipt's
 * column padding doesn't render as a gap in a UI row, but with no words removed. The
 * review screen shows it so the user can always see what was actually printed; the
 * cleaned [query] is a best effort and is sometimes wrong.
 */
data class ReceiptLine(
    val raw: String,
    val query: String,
    val quantity: Int,
    val unitHint: String?,
    /**
     * The product code printed on the line, when the receipt prints one (Walmart does,
     * most Canadian chains don't). Exact, so it is the reliable key for merging the
     * repeated lines a multi-unit purchase produces — and the natural lookup key if the
     * Spoonacular UPC endpoint is ever wired up.
     */
    val upc: String? = null
)

/**
 * Turns OCR'd receipt text into searchable item candidates. Pure, with no Android
 * dependencies, so it is unit-testable (same convention as [com.example.pantryparty.recipe.RecipeMatcher]).
 *
 * Receipts are hostile input: item names are truncated to fit a narrow printer, prices
 * and tax codes share the line, and half the page is bookkeeping. Each line goes
 * through the same pipeline — reject bookkeeping, pull out the quantity, pull out the
 * size, drop prices and codes, then expand shorthand via [RECEIPT_ABBREVIATIONS].
 *
 * This is deliberately lossy and best-effort: it optimizes for producing a *searchable*
 * query, not a correct one. Every result is confirmed by the user before anything is
 * written to the pantry, so a wrong guess costs a tap and a missed line costs nothing.
 */
fun parseReceipt(lines: List<String>): List<ReceiptLine> {
    // Scope to the product region before looking at any line individually. A receipt has
    // three parts: letterhead, products, totals. Only products carry a price column, and
    // the totals block always opens with a subtotal — so the region is findable
    // structurally.
    //
    // This is what a keyword blacklist can't do. Store slogans, street addresses, and
    // manager names are open-ended free text; no list enumerates them, and letting one
    // through means searching Spoonacular for "n florida ave" and getting back agave.
    // Testing every line rather than only the leading run matters: letterhead is not
    // always contiguous. A manager name, street address, or "ST# / OP# / TE# / TR#" line
    // can sit below the first thing that looks priced, and skipping only a prefix lets
    // all of it through.
    //
    // The cost is that a product row whose price OCR misreads is dropped rather than
    // mis-parsed — a visible gap the user can correct in review, instead of a junk row
    // that quietly reaches the pantry.
    val end = lines.indexOfFirst { isTotalsLine(it) }.let { if (it < 0) lines.size else it }
    return lines.take(end)
        .filter { hasPrice(it) }
        .mapNotNull(::parseReceiptLine)
}

/** A price column marks a line as a product row rather than letterhead. */
private fun hasPrice(line: String): Boolean = PRICE.containsMatchIn(line)

/** The opening of the totals block; everything from here down is bookkeeping. */
private fun isTotalsLine(line: String): Boolean =
    line.lowercase().split(NON_WORD).any { it == "subtotal" || it == "sous" }

// A line shorter than this, or with fewer letters than this, is a rule, a barcode, or
// a date — never an item name.
private const val MIN_LINE_LENGTH = 3
private const val MIN_LETTERS = 3

private val WHITESPACE = Regex("\\s+")
private val NON_WORD = Regex("[^a-z0-9%]+")

/**
 * A unit-priced segment: "2 @ $3.99" or "@ 1.52/KG". The leading count is optional
 * because weight-priced produce ("1.2KG @ $1.52/KG") has none.
 */
private val AT_SEGMENT = Regex("(?:(\\d{1,3})\\s*)?@\\s*\\$?\\d+[.,]\\d+\\s*(?:/\\s*[A-Za-z]+)?")
private val LEADING_QTY = Regex("^(\\d{1,3})\\s*[xX]\\b")
/**
 * Trailing multiplier: "AVOC X3". Not anchored to end-of-line because the price
 * usually follows, but the digits must end at a token boundary — otherwise a case of
 * pop ("12X355ML") would read as a quantity of 355.
 */
private val TRAILING_QTY = Regex("\\b[xX]\\s?(\\d{1,3})(?=\\s|$)")

/**
 * A bare leading count: "2 MILK". Capped at two digits and required to be followed by
 * a letter so an item code ("0060383 BANANAS") is never mistaken for a quantity.
 */
private val LEADING_COUNT = Regex("^(\\d{1,2})\\s+(?=[A-Za-z])")

/** Any bare number: item codes, counts, and prices alike. Never part of a name. */
private val NUMBER = Regex("^\\d+(?:[.,]\\d+)?$")

/** A size token, joined ("1.2KG") or split ("1.2 KG"). */
private val SIZE = Regex("^(\\d+(?:[.,]\\d+)?)\\s*([A-Za-z]+)$")

/**
 * Trailing tax/category flags: "H", "M", "HM", "FT". Only meaningful at line end.
 *
 * The alphabet stays deliberately narrow. Widening it to all letters would strip real
 * three-letter foods — OIL, HAM, TEA, JAM, PIE — that can end a line legitimately.
 * Receipts that print flags *mid*-line (Walmart) put them after the product code, so
 * the UPC truncation below removes those without needing a looser pattern here.
 */
private val TAX_CODE = Regex("^[HMFTRJW]{1,3}$")

/**
 * A product code: an unbroken run of 8+ digits. Long enough that no price, count, or
 * size can collide with it, and it marks where the printed description ends — everything
 * after it on the line is columns, not name.
 */
private val UPC = Regex("^\\d{8,}$")

/** A price column — the tell that a line is a product row rather than store letterhead. */
private val PRICE = Regex("\\d+[.,]\\d{2}(?:\\s|$)")

/** A clock time — the reliable tell for a date/time stamp line. */
private val TIME = Regex("\\d{1,2}:\\d{2}")

/** Receipt size suffixes mapped onto units Spoonacular actually offers. */
private val UNIT_HINTS: Map<String, String> = mapOf(
    "kg" to "kg", "g" to "g", "gr" to "g", "gm" to "g",
    "lb" to "lb", "lbs" to "lb", "oz" to "oz",
    "ml" to "ml", "l" to "l", "lt" to "l", "ltr" to "l",
    "pk" to "package", "pkg" to "package",
    "pc" to "piece", "pcs" to "piece", "ct" to "piece", "ea" to "piece"
)

/** Month abbreviations, so a bare date line doesn't survive as the "item" `aug`. */
private val MONTH_TOKENS = setOf(
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept",
    "oct", "nov", "dec", "am", "pm"
)

/** Punctuation that clings to receipt tokens without ever being part of a name. */
private const val TRIM_CHARS = "*#:;,.()[]{}-/\\|'\"$"

/**
 * Parses one line, or returns null when it isn't an item.
 *
 * Internal rather than private so [parseReceipt]'s per-line behaviour can be asserted
 * directly in tests.
 */
internal fun parseReceiptLine(line: String): ReceiptLine? {
    val raw = line.trim().replace(WHITESPACE, " ")
    if (raw.length < MIN_LINE_LENGTH || isBookkeeping(raw)) return null

    // Whether this line carries a price column, which is what licenses the aggressive
    // lone-letter stripping below.
    val priced = hasPrice(raw)

    var work = raw
    var quantity: Int? = null

    // "2 @ $3.99" — take the count and drop the whole pricing segment, including the
    // "/KG" tail that would otherwise survive tokenization as a fake word.
    AT_SEGMENT.find(work)?.let { match ->
        quantity = match.groupValues[1].toIntOrNull()
        work = work.replaceRange(match.range, " ").trim()
    }
    if (quantity == null) {
        val explicit = LEADING_QTY.find(work) ?: TRAILING_QTY.find(work)
        if (explicit != null) {
            quantity = explicit.groupValues[1].toIntOrNull()
            work = work.replaceRange(explicit.range, " ").trim()
        } else {
            LEADING_COUNT.find(work)?.let { match ->
                // A bare leading number is only a count when a unit word doesn't follow
                // it: "12 CT NITRIL" is one twelve-count box, not twelve boxes, and
                // "6 PK" is one six-pack. Left unguarded this multiplies the pantry by
                // the package size. Leaving it in place lets the size token below read
                // it as a unit hint instead.
                val next = work.substring(match.range.last + 1)
                    .trim().split(WHITESPACE).firstOrNull()?.lowercase()
                if (next == null || next !in UNIT_HINTS) {
                    quantity = match.groupValues[1].toIntOrNull()
                    work = work.replaceRange(match.range, " ").trim()
                }
            }
        }
    }

    val allTokens = work.split(WHITESPACE).map { it.trim(*TRIM_CHARS.toCharArray()) }.filter { it.isNotEmpty() }

    // On a receipt that prints product codes, the printed description is everything
    // *before* the code — the rest of the line is columns (flags, price, more flags).
    // Cutting there is what stops "BREAD <upc> F 2.88 N" from being searched as
    // "bread f n", which Spoonacular happily fuzzy-matches to something unrelated.
    val upcIndex = allTokens.indexOfFirst { UPC.matches(it) }
    val upc = allTokens.getOrNull(upcIndex.takeIf { it >= 0 } ?: -1)
    val tokens = if (upcIndex > 0) allTokens.take(upcIndex) else allTokens

    val kept = mutableListOf<String>()
    var unitHint: String? = null
    var index = 0

    while (index < tokens.size) {
        val token = tokens[index]
        val lower = token.lowercase()

        // A size split across two tokens ("1.2 KG"); consume both.
        val splitUnit = tokens.getOrNull(index + 1)?.lowercase()?.let(UNIT_HINTS::get)
        if (splitUnit != null && NUMBER.matches(token)) {
            unitHint = unitHint ?: splitUnit
            index += 2
            continue
        }

        val joinedUnit = SIZE.matchEntire(token)?.let { UNIT_HINTS[it.groupValues[2].lowercase()] }
        when {
            joinedUnit != null -> unitHint = unitHint ?: joinedUnit
            NUMBER.matches(token) -> Unit                                  // code, count, or price
            // A lone letter on a priced line is a flag column. No ingredient name
            // contains a standalone one-letter word, so this is safe — and unlike the
            // UPC cut above it does not care how OCR tokenized the product code, which
            // it often splits ("007225 003712"). That split is what let "F" and "N"
            // survive into the search query as "bread f n".
            priced && token.length == 1 && token[0].isLetter() -> Unit
            index == tokens.lastIndex && TAX_CODE.matches(token) -> Unit   // trailing multi-letter flag
            lower in RECEIPT_BRAND_TOKENS -> Unit
            else -> kept += RECEIPT_ABBREVIATIONS[lower] ?: lower
        }
        index++
    }

    val query = kept.joinToString(" ").trim()
    if (query.isEmpty()) return null
    return ReceiptLine(
        raw = raw,
        query = query,
        quantity = quantity ?: 1,
        unitHint = unitHint,
        upc = upc
    )
}

/**
 * True for the half of a receipt that isn't shopping: totals, payment, loyalty,
 * store details, barcodes, and timestamps.
 *
 * Keyword matching is per token rather than by substring so an ingredient can never be
 * caught by an unlucky overlap — "salt" must not trip the "tax" rule.
 */
private fun isBookkeeping(line: String): Boolean {
    if (line.count { it.isLetter() } < MIN_LETTERS) return true
    if (TIME.containsMatchIn(line)) return true
    val tokens = line.lowercase().split(NON_WORD).filter { it.isNotEmpty() }
    return tokens.any { it in RECEIPT_NOISE_TOKENS || it in MONTH_TOKENS }
}
