package com.example.pantryparty.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
