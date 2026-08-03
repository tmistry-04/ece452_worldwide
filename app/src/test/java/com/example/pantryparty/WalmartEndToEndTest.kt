package com.example.pantryparty

import com.example.pantryparty.receipt.parseReceipt
import org.junit.Test

/**
 * Prints the full parsed result for the Walmart receipt that exposed the matching bugs.
 *
 * Not an assertion test — the behaviour is pinned by [ReceiptParserTest]. This exists so
 * the end-to-end output can be eyeballed against the printed receipt in one place, which
 * is how the "24 items" discrepancy was spotted in the first place.
 */
class WalmartEndToEndTest {

    @Test
    fun printParsedWalmartReceipt() {
        val receipt = listOf(
            "Walmart",
            "Save money. Live better.",
            "(813) 932-0562",
            "Manager COLLEEN BRICKEY",
            "8885 N FLORIDA AVE",
            "TAMPA FL 33604",
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

        val lines = parseReceipt(receipt)
        // Mirrors ReceiptScanViewModel.groupDuplicates().
        val grouped = lines.groupBy { it.upc ?: it.query }
            .map { (_, g) -> g.first().copy(quantity = g.sumOf { it.quantity }) }

        println("parsed lines: ${lines.size}   grouped items: ${grouped.size}")
        grouped.forEach { line ->
            println("  qty=${line.quantity}  unit=${line.unitHint ?: "-"}  query='${line.query}'  upc=${line.upc}")
        }
        println("total units: ${grouped.sumOf { it.quantity }}")
    }
}
