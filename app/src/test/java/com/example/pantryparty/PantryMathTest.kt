package com.example.pantryparty

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.pantry.PantryMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PantryMathTest {

    private val today = 20_000L   // arbitrary epoch day; only differences matter

    private fun txn(
        id: Long = 0,
        date: Long = today - 10,
        bought: Int,
        used: Int = 0,
        thrown: Int = 0,
        expiry: Long? = null,
        itemId: Long = 1
    ) = PantryTransaction(
        id = id, catalogItemId = itemId, date = date,
        amountBought = bought, amountUsed = used, amountThrown = thrown, expiry = expiry
    )

    // --- stock ---------------------------------------------------------------

    @Test
    fun stock_sumsWhatRemainsAcrossLots() {
        val lots = listOf(
            txn(bought = 6, used = 2, thrown = 1),   // 3 left
            txn(bought = 2),                          // 2 left
            txn(bought = 4, used = 4)                 // emptied
        )
        assertEquals(5, PantryMath.stockOf(lots))
    }

    @Test
    fun openLots_dropEmptiedLots_andSortOldestFirst() {
        val newer = txn(id = 1, date = today - 1, bought = 2)
        val older = txn(id = 2, date = today - 9, bought = 2)
        val emptied = txn(id = 3, date = today - 20, bought = 3, used = 3)

        val open = PantryMath.openLots(listOf(newer, older, emptied))
        assertEquals(listOf(2L, 1L), open.map { it.id })
    }

    // --- FIFO ----------------------------------------------------------------

    @Test
    fun fifoPlan_splitsAcrossLotsOldestFirst() {
        val older = txn(id = 1, date = today - 9, bought = 3)
        val newer = txn(id = 2, date = today - 1, bought = 5)

        val plan = PantryMath.fifoPlan(listOf(newer, older), amount = 4)
        assertEquals(listOf(1L to 3, 2L to 1), plan.map { (lot, take) -> lot.id to take })
    }

    @Test
    fun fifoPlan_neverTakesMoreThanTheLotsHold() {
        val only = txn(id = 1, bought = 2)
        val plan = PantryMath.fifoPlan(listOf(only), amount = 10)
        assertEquals(listOf(1L to 2), plan.map { (lot, take) -> lot.id to take })
    }

    // --- expiry --------------------------------------------------------------

    @Test
    fun expirySummary_bucketsRemainingUnitsByUrgency() {
        val lots = listOf(
            txn(bought = 2, expiry = today - 1),               // 2 expired
            txn(bought = 5, used = 2, expiry = today + 2),     // 3 expiring soon
            txn(bought = 4, expiry = today + 30)               // fine
        )
        val summary = PantryMath.expirySummary(lots, today)
        assertEquals(2, summary.expiredCount)
        assertEquals(3, summary.expiringSoonCount)
        assertEquals(2L, summary.daysToNext)
    }

    @Test
    fun expirySummary_ignoresEmptiedLots_andUntrackedExpiries() {
        val lots = listOf(
            txn(bought = 3, used = 3, expiry = today - 5),   // emptied: can't spoil
            txn(bought = 4)                                   // no expiry tracked
        )
        val summary = PantryMath.expirySummary(lots, today)
        assertEquals(0, summary.expiredCount)
        assertEquals(0, summary.expiringSoonCount)
        assertNull(summary.daysToNext)
    }

    @Test
    fun expirySummary_daysToNext_isTheSoonestAmongSoonLots() {
        val lots = listOf(
            txn(bought = 1, expiry = today + 3),
            txn(bought = 1, expiry = today)      // expiring today wins
        )
        assertEquals(0L, PantryMath.expirySummary(lots, today).daysToNext)
    }

    // --- score ---------------------------------------------------------------

    @Test
    fun score_isTheUsedShareOfEverythingConsumed() {
        // 3 used, 1 tossed -> 75.
        val lots = listOf(txn(bought = 6, used = 3, thrown = 1))
        assertEquals(75, PantryMath.score(lots))
    }

    @Test
    fun score_perfectWhenNothingWasTossed_zeroWhenEverythingWas() {
        assertEquals(100, PantryMath.score(listOf(txn(bought = 4, used = 4))))
        assertEquals(0, PantryMath.score(listOf(txn(bought = 4, thrown = 4))))
    }

    @Test
    fun score_nullUntilSomethingWasConsumed() {
        assertNull(PantryMath.score(emptyList()))
        assertNull(PantryMath.score(listOf(txn(bought = 5))))   // bought-only history
    }

    // --- edit legality -------------------------------------------------------

    @Test
    fun validate_acceptsALegalRow() {
        assertNull(PantryMath.validate(txn(bought = 5, used = 3, thrown = 2, expiry = today)))
    }

    @Test
    fun validate_rejectsIllegalRows() {
        // Nothing bought.
        assertNotNull(PantryMath.validate(txn(bought = 0)))
        // Negative amounts.
        assertNotNull(PantryMath.validate(txn(bought = 5, used = -1)))
        // Consumed more than the lot held.
        assertNotNull(PantryMath.validate(txn(bought = 5, used = 3, thrown = 3)))
        // Expired before it was bought.
        assertNotNull(PantryMath.validate(txn(date = today, bought = 5, expiry = today - 1)))
    }

    // --- recipe snapshot -----------------------------------------------------

    @Test
    fun stockSnapshot_carriesStockPerItem_andOmitsZeroStockItems() {
        val catalog = listOf(
            CatalogItem(id = 1, name = "egg", desiredAmount = 12, spoonacularId = 11, sortOrder = 0, unit = "piece"),
            CatalogItem(id = 2, name = "milk", desiredAmount = 2, spoonacularId = 22, sortOrder = 1, unit = "l")
        )
        val txns = listOf(
            txn(itemId = 1, bought = 6, used = 2),   // 4 eggs
            txn(itemId = 2, bought = 1, used = 1)    // milk emptied
        )
        val snapshot = PantryMath.stockSnapshot(catalog, txns)
        assertEquals(1, snapshot.size)
        with(snapshot.single()) {
            assertEquals("egg", name)
            assertEquals(11, spoonacularId)
            assertEquals(4, quantity)
            assertEquals("piece", unit)
        }
    }
}
