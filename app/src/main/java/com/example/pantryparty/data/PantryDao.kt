package com.example.pantryparty.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Single DAO for both pantry tables. The catalog and the transaction ledger are
 * almost always needed together (stock = catalog x transactions), so keeping
 * them behind one interface means every screen gets one dependency.
 */
@Dao
interface PantryDao {

    // ---- catalog ----------------------------------------------------------

    /** The catalog in user-arranged order; re-emits whenever either the rows or their order change. */
    @Query("SELECT * FROM catalog_items ORDER BY sortOrder, id")
    fun observeCatalog(): Flow<List<CatalogItem>>

    /** One-shot snapshot of the catalog (used by the recipe recommender). */
    @Query("SELECT * FROM catalog_items ORDER BY sortOrder, id")
    suspend fun getCatalog(): List<CatalogItem>

    /** The existing row for an ingredient, if any — used to merge on add. */
    @Query("SELECT * FROM catalog_items WHERE spoonacularId = :spoonacularId LIMIT 1")
    suspend fun findBySpoonacularId(spoonacularId: Int): CatalogItem?

    /** The next free sortOrder, so new items append to the end of the list. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM catalog_items")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insertItem(item: CatalogItem): Long

    @Update
    suspend fun updateItem(item: CatalogItem)

    /** Batch update used when rearranging (rewrites the affected sortOrders atomically). */
    @Update
    suspend fun updateItems(items: List<CatalogItem>)

    /** Cascades: the item's transactions are deleted with it. */
    @Delete
    suspend fun deleteItem(item: CatalogItem)

    // ---- transaction ledger -----------------------------------------------

    /** Every transaction, oldest first; grouped per item by the ViewModel. */
    @Query("SELECT * FROM pantry_transactions ORDER BY date, id")
    fun observeTransactions(): Flow<List<PantryTransaction>>

    /** One-shot snapshot of the whole ledger (used by the recipe recommender). */
    @Query("SELECT * FROM pantry_transactions ORDER BY date, id")
    suspend fun getTransactions(): List<PantryTransaction>

    /** One item's history, oldest first (the "use" dialog's lot picker order). */
    @Query("SELECT * FROM pantry_transactions WHERE catalogItemId = :itemId ORDER BY date, id")
    suspend fun transactionsFor(itemId: Long): List<PantryTransaction>

    @Insert
    suspend fun insertTransaction(txn: PantryTransaction): Long

    @Update
    suspend fun updateTransaction(txn: PantryTransaction)

    @Delete
    suspend fun deleteTransaction(txn: PantryTransaction)

    /** Clears one item's entire history (its stock drops to zero). */
    @Query("DELETE FROM pantry_transactions WHERE catalogItemId = :itemId")
    suspend fun clearTransactions(itemId: Long)

    /**
     * Records a purchase for an ingredient, creating its catalog row if this is the
     * first time it's been bought. Returns the catalog row id the lot was filed under.
     *
     * Receipt scanning adds many items at once, and a catalog row without its lot (or
     * a lot without its row) is a broken pantry entry — so the find-or-create and the
     * ledger insert are one transaction rather than two independent writes.
     *
     * Merging on [spoonacularId] rather than inserting blindly is required, not just
     * tidy: `catalog_items` has a unique index on that column, so a second purchase of
     * the same ingredient would otherwise fail outright.
     *
     * Has a body rather than being abstract so the in-memory test fake inherits the
     * same find-or-create semantics for free.
     */
    @Transaction
    suspend fun recordPurchase(
        spoonacularId: Int,
        name: String,
        unit: String,
        quantity: Int,
        dateEpochDay: Long,
        imageUrl: String? = null,
        aisle: String? = null,
        expiry: Long? = null
    ): Long {
        val existing = findBySpoonacularId(spoonacularId)
        val itemId = existing?.id ?: insertItem(
            CatalogItem(
                name = name,
                // The first purchase sets the target: "keep as much as I just bought".
                desiredAmount = quantity,
                spoonacularId = spoonacularId,
                imageUrl = imageUrl,
                sortOrder = nextSortOrder(),
                unit = unit,
                aisle = aisle
            )
        )
        insertTransaction(
            PantryTransaction(
                catalogItemId = itemId,
                date = dateEpochDay,
                amountBought = quantity,
                expiry = expiry
            )
        )
        return itemId
    }

    // ---- staples ("always have") -------------------------------------------

    /** The user's staples, alphabetical; re-emits as they're added or removed. */
    @Query("SELECT * FROM staple_ingredients ORDER BY name")
    fun observeStaples(): Flow<List<StapleIngredient>>

    /** One-shot snapshot of the staples (used by the recipe checks). */
    @Query("SELECT * FROM staple_ingredients ORDER BY name")
    suspend fun getStaples(): List<StapleIngredient>

    /** Re-adding the same ingredient replaces its row rather than failing. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaple(staple: StapleIngredient)

    @Delete
    suspend fun deleteStaple(staple: StapleIngredient)
}
