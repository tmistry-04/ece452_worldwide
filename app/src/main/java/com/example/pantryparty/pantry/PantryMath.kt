package com.example.pantryparty.pantry

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryTransaction
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How urgently an item's remaining stock needs attention:
 * [expiredCount] units are already past their expiry, [expiringSoonCount] units
 * expire within [PantryMath.EXPIRY_WARN_DAYS], and [daysToNext] is the days
 * until the soonest not-yet-expired lot runs out (null when nothing is close).
 * Counts only cover what is still on hand — a fully used lot can't spoil.
 */
data class ExpirySummary(
    val expiredCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val daysToNext: Long? = null
) {
    val hasWarning: Boolean get() = expiredCount > 0 || expiringSoonCount > 0
}

/**
 * Pure, Android-free ledger arithmetic (testable like RecipeMatcher). Every
 * number the pantry screen shows — stock, expiry warnings, the waste score —
 * comes from these functions, so the UI and the recipe recommender can never
 * disagree about what's on hand.
 */
object PantryMath {

    /** Expiries within this many days count as "expiring soon". */
    const val EXPIRY_WARN_DAYS = 3L

    /** Units on hand across all of an item's lots. */
    fun stockOf(txns: List<PantryTransaction>): Int = txns.sumOf { it.remaining }

    /** Lots that still have stock, oldest first — the order "use" defaults to. */
    fun openLots(txns: List<PantryTransaction>): List<PantryTransaction> =
        txns.filter { it.remaining > 0 }.sortedWith(compareBy({ it.date }, { it.id }))

    /**
     * Splits a deduction of [amount] units across [lots] first-in-first-out.
     * Returns (lot, take) pairs; takes at most what the lots hold, so callers
     * apply exactly the returned pairs and never overdraw a lot.
     */
    fun fifoPlan(lots: List<PantryTransaction>, amount: Int): List<Pair<PantryTransaction, Int>> {
        var left = amount
        val plan = mutableListOf<Pair<PantryTransaction, Int>>()
        for (lot in openLots(lots)) {
            if (left <= 0) break
            val take = min(left, lot.remaining)
            plan += lot to take
            left -= take
        }
        return plan
    }

    /** Expiry state of an item's remaining stock as of [todayEpochDay]. */
    fun expirySummary(txns: List<PantryTransaction>, todayEpochDay: Long): ExpirySummary {
        var expired = 0
        var soon = 0
        var next: Long? = null
        for (lot in txns) {
            if (lot.remaining <= 0) continue
            val expiry = lot.expiry ?: continue
            val daysLeft = expiry - todayEpochDay
            when {
                daysLeft < 0 -> expired += lot.remaining
                daysLeft <= EXPIRY_WARN_DAYS -> {
                    soon += lot.remaining
                    next = next?.let { min(it, daysLeft) } ?: daysLeft
                }
            }
        }
        return ExpirySummary(expired, soon, next)
    }

    /**
     * Waste score in 0..100: the share of consumed units that were actually
     * used rather than thrown away (100 = nothing wasted). Null until something
     * has been used or tossed — no history is not the same as a perfect record.
     */
    fun score(txns: List<PantryTransaction>): Int? {
        val used = txns.sumOf { it.amountUsed }
        val thrown = txns.sumOf { it.amountThrown }
        val consumed = used + thrown
        return if (consumed == 0) null else (100.0 * used / consumed).roundToInt()
    }

    /**
     * Why a transaction (as entered or edited) is illegal, or null if it's fine.
     * The rules keep every derived number meaningful: a lot must have been
     * bought, can't have consumed more than it held, and can't expire before it
     * was purchased.
     */
    fun validate(txn: PantryTransaction): String? = when {
        txn.amountBought < 1 -> "Amount bought must be at least 1."
        txn.amountUsed < 0 || txn.amountThrown < 0 -> "Amounts can't be negative."
        txn.amountUsed + txn.amountThrown > txn.amountBought ->
            "Used + thrown away can't exceed the amount bought."
        txn.expiry != null && txn.expiry < txn.date ->
            "Expiry can't be before the purchase date."
        else -> null
    }

    /** The recipe features' view of the pantry: each catalog item with its stock, zero-stock items omitted. */
    fun stockSnapshot(
        catalog: List<CatalogItem>,
        txns: List<PantryTransaction>
    ): List<StockItem> {
        val byItem = txns.groupBy { it.catalogItemId }
        return catalog.mapNotNull { item ->
            val stock = stockOf(byItem[item.id].orEmpty())
            if (stock <= 0) null
            else StockItem(item.id, item.spoonacularId, item.name, item.unit, stock)
        }
    }
}
