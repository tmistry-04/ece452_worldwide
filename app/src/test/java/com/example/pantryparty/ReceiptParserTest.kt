package com.example.pantryparty

import com.example.pantryparty.receipt.ReceiptLine
import com.example.pantryparty.receipt.parseReceipt
import com.example.pantryparty.receipt.parseReceiptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Receipt line shapes from the chains NFR 8.1 names — Costco, Loblaw banners
 * (Zehrs/RCSS/YIG/T&T), Metro, and Sobeys. Each chain formats prices, quantities, and
 * tax flags differently, so they get their own test rather than a shared fixture.
 */
class ReceiptParserTest {

    // --- helpers --------------------------------------------------------------

    private fun query(line: String): String? = parseReceiptLine(line)?.query
    private fun parsed(line: String): ReceiptLine =
        requireNotNull(parseReceiptLine(line)) { "expected '$line' to parse as an item" }

    // --- bookkeeping rejection ------------------------------------------------

    @Test
    fun totalsAndPaymentLines_areDropped() {
        val noise = listOf(
            "SUBTOTAL              24.51",
            "TOTAL                 27.69",
            "HST 13%                3.18",
            "GST                    0.00",
            "DEBIT TEND            27.69",
            "VISA **** 4242        27.69",
            "CHANGE                 0.00",
            "BALANCE DUE            0.00",
            "TOTAL SAVINGS          4.20",
            "PC OPTIMUM POINTS      2500",
            "THANK YOU FOR SHOPPING",
            "CUSTOMER COPY",
            "APPROVED - AUTH 004123"
        )
        assertEquals(emptyList<ReceiptLine>(), parseReceipt(noise))
    }

    @Test
    fun barcodesRulesAndTimestamps_areDropped() {
        assertNull(query("0060383119416"))
        assertNull(query("------------------------"))
        assertNull(query("2026-08-03  14:32"))
        assertNull(query("AUG 03 2026 14:32:07"))
        assertNull(query("519-555-0134"))
        assertNull(query("$$"))
    }

    @Test
    fun saltIsNotMistakenForTax() {
        // Token matching, not substring: "salt" must survive the "tax" noise rule.
        assertEquals("sea salt", query("SEA SALT              3.49 H"))
    }

    // --- price, tax-flag, and code stripping ----------------------------------

    @Test
    fun trailingPriceAndTaxFlag_areStripped() {
        assertEquals("whole milk", query("WHOLE MILK            4.99 H"))
        assertEquals("cheddar cheese", query("CHEDDAR CHEESE        8.49 HM"))
    }

    @Test
    fun commaDecimalPrices_areStripped() {
        assertEquals("butter", query("BUTTER                6,99"))
    }

    @Test
    fun leadingItemCode_isDropped() {
        assertEquals("bananas", query("0060383 BANANAS       1.82"))
    }

    // --- quantity extraction --------------------------------------------------

    @Test
    fun atPricedLine_yieldsCountAndDropsPricingSegment() {
        val line = parsed("2 @ $3.99   CANNED TOMATOES    7.98")
        assertEquals(2, line.quantity)
        assertEquals("canned tomatoes", line.query)
    }

    @Test
    fun weightPricedLine_dropsPerUnitPriceAndKeepsUnit() {
        // No leading count — produce is priced by weight, so quantity stays 1.
        val line = parsed("BANANAS 1.2KG @ $1.52/KG     1.82")
        assertEquals(1, line.quantity)
        assertEquals("kg", line.unitHint)
        assertEquals("bananas", line.query)
    }

    @Test
    fun multiplierNotation_isReadAsQuantity() {
        assertEquals(3, parsed("3X YOGURT             5.97").quantity)
        assertEquals(2, parsed("GREEK YOGURT X2       7.98").quantity)
    }

    @Test
    fun bareLeadingCount_isReadAsQuantity() {
        val line = parsed("2 AVOCADO             3.98")
        assertEquals(2, line.quantity)
        assertEquals("avocado", line.query)
    }

    @Test
    fun percentageInName_isNotMistakenForACount() {
        val line = parsed("2% MILK 2L            5.49")
        assertEquals(1, line.quantity)
        assertEquals("l", line.unitHint)
        assertEquals("2% milk", line.query)
    }

    // --- size / unit hints ----------------------------------------------------

    @Test
    fun joinedAndSplitSizeTokens_bothYieldUnitHints() {
        assertEquals("g", parsed("SHREDDED CHEESE 320G   6.99").unitHint)
        assertEquals("kg", parsed("GROUND BEEF 1.2 KG    14.99").unitHint)
        assertEquals("ml", parsed("OLIVE OIL 750ML       12.99").unitHint)
        assertEquals("package", parsed("EGGS 12PK              4.29").unitHint)
    }

    @Test
    fun sizeToken_isRemovedFromTheQuery() {
        assertEquals("olive oil", query("OLIVE OIL 750ML       12.99"))
    }

    @Test
    fun lineWithNoSize_hasNullUnitHint() {
        assertNull(parsed("CUCUMBER               1.49").unitHint)
    }

    // --- abbreviation expansion -----------------------------------------------

    @Test
    fun crypticShorthand_isExpanded() {
        assertEquals("green giant sweet corn", query("GRN GIANT SWT CRN     2.49"))
        assertEquals("boneless skinless chicken breast", query("BNLS SKNLS CHKN BRST 12.47"))
        assertEquals("organic baby spinach", query("ORG BABY SPIN         4.99"))
        assertEquals("sour cream", query("SR CRM                3.29"))
    }

    @Test
    fun storeBrandPrefix_isDropped() {
        assertEquals("whole milk", query("PC WHL MILK           5.29"))
        assertEquals("frozen peas", query("NN FRZ PEAS           2.99"))
    }

    @Test
    fun lineThatReducesToNothing_isDropped() {
        // Brand + size + price with no actual name left behind.
        assertNull(query("PC 500G               4.99"))
    }

    // --- per-chain receipt bodies (NFR 8.1) -----------------------------------

    @Test
    fun costcoReceipt_yieldsOnlyItems() {
        val lines = listOf(
            "COSTCO WHOLESALE",
            "WAREHOUSE #1234",
            "1234567 KS ORG EGGS 24CT      9.99",
            "9876543 BNLS CHKN BRST       24.86",
            "2468013 ORG SPIN 1LB          6.49",
            "SUBTOTAL                     41.34",
            "TOTAL                        46.71"
        )
        val items = parseReceipt(lines)
        assertEquals(
            listOf("organic eggs", "boneless chicken breast", "organic spinach"),
            items.map { it.query }
        )
        assertEquals(listOf("piece", null, "lb"), items.map { it.unitHint })
    }

    @Test
    fun zehrsReceipt_yieldsOnlyItems() {
        val lines = listOf(
            "ZEHRS MARKETS",
            "PC WHL MILK 4L                6.49 M",
            "BANANAS 1.4KG @ $1.52/KG      2.13",
            "2 @ $2.49  GRN PPR            4.98",
            "NN SHRD CHDR 320G             5.99 H",
            "SUBTOTAL                     19.59",
            "PC OPTIMUM POINTS             1200"
        )
        val items = parseReceipt(lines)
        assertEquals(
            listOf("whole milk", "bananas", "green pepper", "shredded cheddar"),
            items.map { it.query }
        )
        assertEquals(listOf(1, 1, 2, 1), items.map { it.quantity })
    }

    @Test
    fun metroReceipt_yieldsOnlyItems() {
        val lines = listOf(
            "METRO PLUS",
            "TOM ROMA               3,49",
            "FRSH SLMN FILET       15,99",
            "PSTA SPAG 900G         2,99",
            "SOUS-TOTAL            22,47",
            "TPS/TVQ                1,12"
        )
        val items = parseReceipt(lines)
        assertEquals(
            listOf("tomato roma", "fresh salmon filet", "pasta spaghetti"),
            items.map { it.query }
        )
    }

    @Test
    fun sobeysReceipt_yieldsOnlyItems() {
        val lines = listOf(
            "SOBEYS",
            "COMP GRND BF LEAN 1KG        11.99",
            "RD ONN                        1.29",
            "AVOC X3                       5.97",
            "TOTAL                        19.25",
            "INTERAC                      19.25"
        )
        val items = parseReceipt(lines)
        assertEquals(
            listOf("ground beef lean", "red onion", "avocado"),
            items.map { it.query }
        )
        assertEquals(listOf(1, 1, 3), items.map { it.quantity })
        assertEquals(listOf("kg", null, null), items.map { it.unitHint })
    }

    // --- Walmart: product codes, flag columns, letterhead ---------------------

    /**
     * A real Walmart receipt, verbatim. Walmart is structurally unlike the Canadian
     * chains above: it prints a UPC and *two* flag columns per line, and its letterhead
     * carries free text (slogan, manager, street address) that no keyword list can
     * anticipate.
     */
    private val walmartReceipt = listOf(
        "Walmart",
        "Save money. Live better.",
        "(813) 932-0562",
        "Manager COLLEEN BRICKEY",
        "8885 N FLORIDA AVE",
        "TAMPA FL 33604",
        "ST# 5221 OP# 00001061 TE# 06 TR# 05332",
        "BREAD          007225003712  F   2.88 N",
        "BREAD          007225003712  F   2.88 N",
        "GV PNT BUTTR   007874237003  F   3.84 N",
        "GV PNT BUTTR   007874237003  F   3.84 N",
        "GV PNT BUTTR   007874237003  F   3.84 N",
        "GV PNT BUTTR   007874237003  F   3.84 N",
        "GV PORK 16OZ   007874201510  F   4.98 O",
        "GV CHNK CHKN   007874206784  F   1.98 N",
        "GV CHNK CHKN   007874206784  F   1.98 N",
        "12 CT NITRIL   073191913822      2.78 X",
        "FOLGERS        002550000377  F  10.48 N",
        "SC TWIST UP    007874222682  F   0.84 X",
        "EGGS           060538871459  F   1.88 O",
        "SUBTOTAL                            46.04",
        "TAX 1                7.000%          0.26",
        "TOTAL                               46.30",
        "# ITEMS SOLD 13"
    )

    @Test
    fun productCodeTruncatesTheLine_soFlagColumnsNeverReachTheQuery() {
        // Regression: the flag columns used to survive as words, so this line was
        // searched as "bread f n" — which fuzzy-matched to an unrelated pasta.
        val line = parsed("BREAD          007225003712  F   2.88 N")
        assertEquals("bread", line.query)
        assertEquals("007225003712", line.upc)
    }

    @Test
    fun letterheadNeverBecomesAProduct() {
        val raws = parseReceipt(walmartReceipt).map { it.raw }
        // The address line is the dangerous one: it survived as "n florida ave", whose
        // last token then fell back to "ave" and matched agave.
        assertTrue(raws.none { it.contains("FLORIDA") })
        assertTrue(raws.none { it.contains("COLLEEN") })
        assertTrue(raws.none { it.contains("Save money") })
        assertTrue(raws.none { it.contains("Walmart") })
        assertTrue(raws.none { it.contains("TAMPA") })
        // Regression: letterhead is not contiguous. The register/transaction line sits
        // below other header rows, so skipping only a leading run let it through.
        assertTrue(raws.none { it.contains("ST#") })
    }

    @Test
    fun aProductCodeSplitByOcr_stillYieldsACleanQuery() {
        // ML Kit routinely breaks long digit runs into separate tokens, which defeats
        // the UPC cut. The flag columns must still fall out, or the query goes back to
        // "bread f n" — the exact failure this whole pass is about.
        assertEquals("bread", query("BREAD          007225 003712  F   2.88 N"))
        assertEquals("peanut butter", query("GV PNT BUTTR   007874 237003  F   3.84 N"))
    }

    @Test
    fun aLoneLetterIsOnlyStrippedWhenTheLineIsPriced() {
        // Without a price column there is no flag column to strip, so a short token
        // stays part of the name.
        assertEquals("vitamin d milk", query("VITAMIN D MILK"))
    }

    @Test
    fun nothingFromTheTotalsBlockSurvives() {
        val raws = parseReceipt(walmartReceipt).map { it.raw }
        assertTrue(raws.none { it.contains("SUBTOTAL") })
        assertTrue(raws.none { it.contains("TAX") })
        assertTrue(raws.none { it.contains("TOTAL") })
        assertTrue(raws.none { it.contains("ITEMS SOLD") })
    }

    @Test
    fun everyParsedLineIsAnActualProductRow() {
        // 13 printed product lines; grouping into 8 distinct items happens downstream.
        assertEquals(13, parseReceipt(walmartReceipt).size)
    }

    @Test
    fun walmartShorthandExpands() {
        assertEquals("peanut butter", query("GV PNT BUTTR   007874237003  F   3.84 N"))
        assertEquals("chunk chicken", query("GV CHNK CHKN   007874206784  F   1.98 N"))
        assertEquals("eggs", query("EGGS           060538871459  F   1.88 O"))
    }

    @Test
    fun aCountFollowedByAUnitWord_isAPackageSizeNotAQuantity() {
        // "12 CT NITRIL" is one twelve-count box. Read as a quantity it would add
        // twelve boxes to the pantry.
        val line = parsed("12 CT NITRIL   073191913822      2.78 X")
        assertEquals(1, line.quantity)
        assertEquals("piece", line.unitHint)
        assertEquals("nitril", line.query)
    }

    @Test
    fun groupedQuantitiesMatchTheReceiptsOwnItemCount() {
        // The receipt footer says "# ITEMS SOLD 13" — the parsed quantities must agree,
        // which is the end-to-end check that no line is dropped, duplicated, or
        // multiplied by its package size.
        val lines = parseReceipt(walmartReceipt)
        val grouped = lines.groupBy { it.upc ?: it.query }
            .map { (_, g) -> g.sumOf { it.quantity } }
        assertEquals(8, grouped.size)
        assertEquals(13, grouped.sum())
    }

    @Test
    fun packageSizeBeforeTheProductCode_isStillReadAsAUnit() {
        val line = parsed("GV PORK 16OZ   007874201510  F   4.98 O")
        assertEquals("pork", line.query)
        assertEquals("oz", line.unitHint)
    }

    // --- raw text is always preserved -----------------------------------------

    @Test
    fun rawLineIsPreservedForTheReviewScreen() {
        // Column padding collapses so the review row reads cleanly, but no word is lost —
        // the user must be able to see the price and shorthand the guess came from.
        assertEquals("GRN GIANT SWT CRN 2.49", parsed("  GRN GIANT SWT CRN     2.49 ").raw)
    }
}
