package com.example.pantryparty.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One purchase ("lot") of a catalog item, plus what has happened to it since.
 * Swiping right to buy inserts a row; swiping left to use or toss bumps the
 * chosen row's [amountUsed]/[amountThrown] counters. What's still on hand from
 * the lot is `amountBought - amountUsed - amountThrown`, so summing that across
 * rows yields the item's stock, and each lot's [expiry] can be matched against
 * exactly the units that remain from it.
 *
 * Every edit must keep a row "legal": all amounts non-negative and
 * `amountUsed + amountThrown <= amountBought` (see PantryMath.validate).
 *
 * Dates are stored as epoch days (LocalDate.toEpochDay) — no time-of-day or
 * timezone to get wrong.
 */
@Entity(
    tableName = "pantry_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CatalogItem::class,
            parentColumns = ["id"],
            childColumns = ["catalogItemId"],
            onDelete = ForeignKey.CASCADE   // deleting an item drops its history
        )
    ],
    indices = [Index(value = ["catalogItemId"])]
)
data class PantryTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogItemId: Long,
    val date: Long,                    // purchase date, as epoch day
    val amountBought: Int,
    val amountUsed: Int = 0,
    val amountThrown: Int = 0,
    val expiry: Long? = null           // epoch day; null = no expiry tracked
) {
    /** Units from this lot still on hand. */
    val remaining: Int get() = amountBought - amountUsed - amountThrown
}
