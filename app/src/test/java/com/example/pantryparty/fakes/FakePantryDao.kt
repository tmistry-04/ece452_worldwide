package com.example.pantryparty.fakes

import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory PantryDao mirroring the Room semantics the app relies on: rows are
 * kept sorted by name, ids auto-generate from 1, and upsert REPLACEs any row
 * with the same id or the same (unique-indexed) spoonacularId.
 */
class FakePantryDao : PantryDao {

    private val items = MutableStateFlow<List<PantryItem>>(emptyList())
    private var nextId = 1L

    /** Seeds the table, assigning ids to rows that don't have one. */
    fun seed(vararg rows: PantryItem) {
        items.value = rows
            .map { if (it.id == 0L) it.copy(id = nextId++) else it }
            .sortedBy { it.name }
    }

    /** Current table contents, for assertions. */
    fun snapshot(): List<PantryItem> = items.value

    override fun observeAll(): Flow<List<PantryItem>> = items

    override suspend fun getAll(): List<PantryItem> = items.value

    override suspend fun findBySpoonacularId(spoonacularId: Int): PantryItem? =
        items.value.firstOrNull { it.spoonacularId == spoonacularId }

    override suspend fun upsert(item: PantryItem) {
        val row = if (item.id == 0L) item.copy(id = nextId++) else item
        items.update { list ->
            (list.filterNot { it.id == row.id || it.spoonacularId == row.spoonacularId } + row)
                .sortedBy { it.name }
        }
    }

    override suspend fun update(item: PantryItem) =
        items.update { list -> list.map { if (it.id == item.id) item else it }.sortedBy { it.name } }

    override suspend fun delete(item: PantryItem) =
        items.update { list -> list.filterNot { it.id == item.id } }
}
