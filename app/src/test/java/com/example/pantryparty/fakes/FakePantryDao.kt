package com.example.pantryparty.fakes

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.data.StapleIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory PantryDao mirroring the Room semantics the app relies on: catalog
 * rows come back ordered by (sortOrder, id), transactions by (date, id), ids
 * auto-generate from 1, and deleting an item cascades to its transactions
 * (like the schema's onDelete = CASCADE).
 */
class FakePantryDao : PantryDao {

    private val items = MutableStateFlow<List<CatalogItem>>(emptyList())
    private val txns = MutableStateFlow<List<PantryTransaction>>(emptyList())
    private val staples = MutableStateFlow<List<StapleIngredient>>(emptyList())
    private var nextItemId = 1L
    private var nextTxnId = 1L

    private val catalogOrder = compareBy<CatalogItem>({ it.sortOrder }, { it.id })
    private val txnOrder = compareBy<PantryTransaction>({ it.date }, { it.id })

    /** Seeds catalog rows, assigning ids to rows that don't have one; returns them as stored. */
    fun seedItems(vararg rows: CatalogItem): List<CatalogItem> {
        val assigned = rows.map { if (it.id == 0L) it.copy(id = nextItemId++) else it }
        items.update { (it + assigned).sortedWith(catalogOrder) }
        return assigned
    }

    /** Seeds ledger rows, assigning ids to rows that don't have one; returns them as stored. */
    fun seedTransactions(vararg rows: PantryTransaction): List<PantryTransaction> {
        val assigned = rows.map { if (it.id == 0L) it.copy(id = nextTxnId++) else it }
        txns.update { (it + assigned).sortedWith(txnOrder) }
        return assigned
    }

    /** Current catalog contents, for assertions. */
    fun itemsSnapshot(): List<CatalogItem> = items.value

    /** Current ledger contents, for assertions. */
    fun transactionsSnapshot(): List<PantryTransaction> = txns.value

    // ---- catalog ----------------------------------------------------------

    override fun observeCatalog(): Flow<List<CatalogItem>> = items

    override suspend fun getCatalog(): List<CatalogItem> = items.value

    override suspend fun findBySpoonacularId(spoonacularId: Int): CatalogItem? =
        items.value.firstOrNull { it.spoonacularId == spoonacularId }

    override suspend fun getItem(id: Long): CatalogItem? =
        items.value.firstOrNull { it.id == id }

    override suspend fun nextSortOrder(): Int =
        (items.value.maxOfOrNull { it.sortOrder } ?: -1) + 1

    override suspend fun insertItem(item: CatalogItem): Long {
        val row = if (item.id == 0L) item.copy(id = nextItemId++) else item
        items.update { (it + row).sortedWith(catalogOrder) }
        return row.id
    }

    override suspend fun updateItem(item: CatalogItem) = updateItems(listOf(item))

    override suspend fun updateItems(items: List<CatalogItem>) {
        val byId = items.associateBy { it.id }
        this.items.update { list -> list.map { byId[it.id] ?: it }.sortedWith(catalogOrder) }
    }

    override suspend fun deleteItem(item: CatalogItem) {
        items.update { list -> list.filterNot { it.id == item.id } }
        txns.update { list -> list.filterNot { it.catalogItemId == item.id } }   // FK cascade
    }

    // ---- transaction ledger -----------------------------------------------

    override fun observeTransactions(): Flow<List<PantryTransaction>> = txns

    override suspend fun getTransactions(): List<PantryTransaction> = txns.value

    override suspend fun transactionsFor(itemId: Long): List<PantryTransaction> =
        txns.value.filter { it.catalogItemId == itemId }

    override suspend fun insertTransaction(txn: PantryTransaction): Long {
        val row = if (txn.id == 0L) txn.copy(id = nextTxnId++) else txn
        txns.update { (it + row).sortedWith(txnOrder) }
        return row.id
    }

    override suspend fun updateTransaction(txn: PantryTransaction) =
        txns.update { list -> list.map { if (it.id == txn.id) txn else it }.sortedWith(txnOrder) }

    override suspend fun deleteTransaction(txn: PantryTransaction) =
        txns.update { list -> list.filterNot { it.id == txn.id } }

    override suspend fun clearTransactions(itemId: Long) =
        txns.update { list -> list.filterNot { it.catalogItemId == itemId } }

    // ---- staples ("always have") -------------------------------------------

    override fun observeStaples(): Flow<List<StapleIngredient>> = staples

    override suspend fun getStaples(): List<StapleIngredient> = staples.value

    /** Mirrors OnConflictStrategy.REPLACE on the spoonacularId primary key. */
    override suspend fun insertStaple(staple: StapleIngredient) =
        staples.update { list ->
            (list.filterNot { it.spoonacularId == staple.spoonacularId } + staple)
                .sortedBy { it.name }
        }

    override suspend fun deleteStaple(staple: StapleIngredient) =
        staples.update { list -> list.filterNot { it.spoonacularId == staple.spoonacularId } }
}
