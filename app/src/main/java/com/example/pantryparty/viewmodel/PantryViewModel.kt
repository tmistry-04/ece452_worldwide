package com.example.pantryparty.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.network.SpoonacularRepositoryImpl
import com.example.pantryparty.network.friendlyApiError
import com.example.pantryparty.pantry.ExpirySummary
import com.example.pantryparty.pantry.PantryMath
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One catalog row as the list shows it: the item plus everything derived from its ledger. */
data class PantryRowUi(
    val item: CatalogItem,
    val stock: Int,
    val expiry: ExpirySummary,
    val score: Int?           // null until something has been used or tossed
)

/** The details modal: the row plus its full history, newest purchase first. */
data class ItemDetailUi(
    val row: PantryRowUi,
    val transactions: List<PantryTransaction>
)

/** Draft behind the swipe-right "buy" dialog. */
data class BuyDraft(
    val item: CatalogItem,
    val amount: String = "1",
    val dateBought: LocalDate,     // defaults to today
    val expiry: LocalDate? = null  // optional
) {
    val quantity: Int get() = amount.toIntOrNull() ?: 0
    val error: String?
        get() = if (expiry != null && expiry < dateBought)
            "Expiry can't be before the purchase date." else null
    val canConfirm: Boolean get() = quantity > 0 && error == null
}

/** What the swipe-left dialog records: eaten/cooked vs. thrown away. */
enum class UseKind { USED, TOSSED }

/** Draft behind the swipe-left "use" dialog. [lots] are open lots, oldest first. */
data class UseDraft(
    val item: CatalogItem,
    val lots: List<PantryTransaction>,
    val selectedLotId: Long?,      // defaults to the oldest open lot
    val amount: String = "1",
    val kind: UseKind = UseKind.USED
) {
    val quantity: Int get() = amount.toIntOrNull() ?: 0
    val selectedLot: PantryTransaction? get() = lots.firstOrNull { it.id == selectedLotId }
    val canConfirm: Boolean get() = quantity in 1..(selectedLot?.remaining ?: 0)
}

/** Draft for editing one ledger row inside the details modal. */
data class TransactionDraft(
    val original: PantryTransaction,
    val date: LocalDate,
    val bought: String,
    val used: String,
    val thrown: String,
    val expiry: LocalDate?
) {
    /** The row as edited; blank amount fields become -1 so they read as illegal, not as zero. */
    val edited: PantryTransaction
        get() = original.copy(
            date = date.toEpochDay(),
            amountBought = bought.toIntOrNull() ?: -1,
            amountUsed = used.toIntOrNull() ?: -1,
            amountThrown = thrown.toIntOrNull() ?: -1,
            expiry = expiry?.toEpochDay()
        )
    val error: String? get() = PantryMath.validate(edited)
    val canConfirm: Boolean get() = error == null
}

/**
 * State of the item editor (edit mode's add/modify dialog). Adding starts at
 * the search step ([searching]); picking a suggestion — or opening an existing
 * item — shows the details step (name, desired amount, unit).
 */
data class ItemEditorState(
    val editing: CatalogItem? = null,                            // null = adding
    val query: String = "",
    val suggestions: List<IngredientAutocomplete> = emptyList(),
    val selected: IngredientAutocomplete? = null,
    val name: String = "",
    val desiredAmount: String = "1",
    val unitOptions: List<String> = emptyList(),
    val unit: String = "",
    val customUnit: Boolean = false,   // "Custom" picked → unit comes from [customUnitText]
    val customUnitText: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    val searching: Boolean get() = editing == null && selected == null
    val desired: Int get() = desiredAmount.toIntOrNull() ?: 0
    val resolvedUnit: String get() = if (customUnit) customUnitText.trim() else unit
    val canSave: Boolean get() = !searching && desired > 0 && name.isNotBlank() && resolvedUnit.isNotEmpty()
}

/**
 * Owns the Pantry tab: the catalog rows with their derived stock/expiry/score,
 * edit mode, and the drafts behind every dialog (buy, use, item editor,
 * transaction editor). All writes go through here; no composable touches the
 * DAO directly. [today] is injectable so tests can pin the clock.
 */
class PantryViewModel(
    private val dao: PantryDao,
    private val repository: SpoonacularRepository,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    /** The catalog in user order, each row carrying its ledger-derived numbers. */
    val rows: StateFlow<List<PantryRowUi>> =
        combine(dao.observeCatalog(), dao.observeTransactions()) { catalog, txns ->
            val todayEpoch = today().toEpochDay()
            val byItem = txns.groupBy { it.catalogItemId }
            catalog.map { item ->
                val history = byItem[item.id].orEmpty()
                PantryRowUi(
                    item = item,
                    stock = PantryMath.stockOf(history),
                    expiry = PantryMath.expirySummary(history, todayEpoch),
                    score = PantryMath.score(history)
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ---- edit mode ---------------------------------------------------------

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun toggleEditMode() {
        _editMode.update { !it }
        // Leaving edit mode abandons any half-finished item entry.
        if (!_editMode.value) dismissItemEditor()
    }

    /** Moves the item one position up or down; positions are rewritten 0..n-1, which also self-heals gaps. */
    private fun move(item: CatalogItem, delta: Int) {
        viewModelScope.launch {
            val catalog = dao.getCatalog().toMutableList()
            val from = catalog.indexOfFirst { it.id == item.id }
            val to = from + delta
            if (from < 0 || to < 0 || to >= catalog.size) return@launch
            catalog.add(to, catalog.removeAt(from))
            dao.updateItems(
                catalog.mapIndexedNotNull { i, row -> if (row.sortOrder == i) null else row.copy(sortOrder = i) }
            )
        }
    }

    fun moveUp(item: CatalogItem) = move(item, -1)
    fun moveDown(item: CatalogItem) = move(item, +1)

    /** Deletes the item and (via the FK cascade) its whole transaction history. */
    fun deleteItem(item: CatalogItem) {
        viewModelScope.launch {
            dao.deleteItem(item)
            if (_detailItemId.value == item.id) closeDetail()
        }
    }

    // ---- item editor (add / modify) ---------------------------------------

    private val _itemEditor = MutableStateFlow<ItemEditorState?>(null)
    val itemEditor: StateFlow<ItemEditorState?> = _itemEditor.asStateFlow()

    // In-flight autocomplete request; cancelled on each keystroke for debouncing.
    private var autocompleteJob: Job? = null

    fun openAddItem() {
        _itemEditor.value = ItemEditorState()
    }

    /**
     * Opens an existing item for modification. Spoonacular's possibleUnits
     * aren't stored on the row, so the unit chips fall back to a common set
     * (plus the item's current unit); anything else is a keystroke away via
     * the custom option.
     */
    fun openEditItem(item: CatalogItem) {
        autocompleteJob?.cancel()
        _itemEditor.value = ItemEditorState(
            editing = item,
            name = item.name,
            desiredAmount = item.desiredAmount.toString(),
            unitOptions = (listOf(item.unit) + COMMON_UNITS).distinct(),
            unit = item.unit
        )
    }

    fun dismissItemEditor() {
        autocompleteJob?.cancel()
        _itemEditor.value = null
    }

    /** Debounced autocomplete: re-runs on each keystroke, cancelling the prior delay. */
    fun onQueryChange(query: String) {
        autocompleteJob?.cancel()
        val editor = _itemEditor.value ?: return
        // Lookups only make sense on the search step and for non-trivial queries;
        // the else branch also clears loading/error left by a just-cancelled lookup.
        val searchable = editor.searching && query.trim().length >= 2
        _itemEditor.update {
            if (searchable) it?.copy(query = query)
            else it?.copy(query = query, suggestions = emptyList(), loading = false, error = null)
        }
        if (!searchable) return
        autocompleteJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _itemEditor.update { it?.copy(loading = true, error = null) }
            repository.autocompleteIngredients(query.trim())
                .onSuccess { result -> _itemEditor.update { it?.copy(suggestions = result, loading = false) } }
                .onFailure { e -> _itemEditor.update { it?.copy(error = friendlyApiError(e), loading = false) } }
        }
    }

    fun selectSuggestion(suggestion: IngredientAutocomplete) {
        autocompleteJob?.cancel()
        val units = suggestion.possibleUnits.ifEmpty { listOf(DEFAULT_UNIT) }
        _itemEditor.update {
            it?.copy(
                selected = suggestion,
                suggestions = emptyList(),
                name = suggestion.name,
                unitOptions = units,
                unit = units.first(),
                customUnit = false,
                loading = false,
                error = null
            )
        }
    }

    fun selectUnit(unit: String) = _itemEditor.update { it?.copy(unit = unit, customUnit = false) }
    fun selectCustomUnit() = _itemEditor.update { it?.copy(customUnit = true) }
    fun onCustomUnitChange(text: String) = _itemEditor.update { it?.copy(customUnitText = text) }
    fun onNameChange(name: String) = _itemEditor.update { it?.copy(name = name) }

    /** Keeps only digits so the desired amount stays a valid count. */
    fun onDesiredAmountChange(input: String) =
        _itemEditor.update { it?.copy(desiredAmount = input.filter { c -> c.isDigit() }) }

    /**
     * Persists the editor. Modifications update the row in place; adds merge
     * into any existing row for the same ingredient instead of duplicating it
     * (the DB's unique index would reject the duplicate anyway).
     */
    fun saveItem() {
        val editor = _itemEditor.value ?: return
        if (!editor.canSave) return
        viewModelScope.launch {
            val name = editor.name.trim()
            val editing = editor.editing
            if (editing != null) {
                dao.updateItem(
                    editing.copy(name = name, desiredAmount = editor.desired, unit = editor.resolvedUnit)
                )
            } else {
                val picked = editor.selected ?: return@launch
                val existing = dao.findBySpoonacularId(picked.id)
                if (existing != null) {
                    dao.updateItem(
                        existing.copy(
                            name = name,
                            desiredAmount = editor.desired,
                            unit = editor.resolvedUnit,
                            imageUrl = picked.image ?: existing.imageUrl,
                            aisle = picked.aisle ?: existing.aisle
                        )
                    )
                } else {
                    dao.insertItem(
                        CatalogItem(
                            name = name,
                            desiredAmount = editor.desired,
                            spoonacularId = picked.id,
                            imageUrl = picked.image,
                            sortOrder = dao.nextSortOrder(),
                            unit = editor.resolvedUnit,
                            aisle = picked.aisle
                        )
                    )
                }
            }
            dismissItemEditor()
        }
    }

    // ---- buy (swipe right) -------------------------------------------------

    private val _buyDraft = MutableStateFlow<BuyDraft?>(null)
    val buyDraft: StateFlow<BuyDraft?> = _buyDraft.asStateFlow()

    fun openBuy(item: CatalogItem) {
        _buyDraft.value = BuyDraft(item = item, dateBought = today())
    }

    fun setBuyAmount(input: String) =
        _buyDraft.update { it?.copy(amount = input.filter { c -> c.isDigit() }) }

    fun setBuyDate(date: LocalDate) = _buyDraft.update { it?.copy(dateBought = date) }
    fun setBuyExpiry(date: LocalDate?) = _buyDraft.update { it?.copy(expiry = date) }
    fun dismissBuy() { _buyDraft.value = null }

    /** Records the purchase as a new lot in the ledger. */
    fun confirmBuy() {
        val draft = _buyDraft.value ?: return
        if (!draft.canConfirm) return
        viewModelScope.launch {
            dao.insertTransaction(
                PantryTransaction(
                    catalogItemId = draft.item.id,
                    date = draft.dateBought.toEpochDay(),
                    amountBought = draft.quantity,
                    expiry = draft.expiry?.toEpochDay()
                )
            )
            _buyDraft.value = null
        }
    }

    // ---- use / toss (swipe left) ------------------------------------------

    private val _useDraft = MutableStateFlow<UseDraft?>(null)
    val useDraft: StateFlow<UseDraft?> = _useDraft.asStateFlow()

    fun openUse(item: CatalogItem) {
        viewModelScope.launch {
            val lots = PantryMath.openLots(dao.transactionsFor(item.id))
            _useDraft.value = UseDraft(
                item = item,
                lots = lots,
                selectedLotId = lots.firstOrNull()?.id   // oldest first = FIFO default
            )
        }
    }

    fun setUseAmount(input: String) =
        _useDraft.update { it?.copy(amount = input.filter { c -> c.isDigit() }) }

    fun selectUseLot(lotId: Long) = _useDraft.update { it?.copy(selectedLotId = lotId) }
    fun setUseKind(kind: UseKind) = _useDraft.update { it?.copy(kind = kind) }
    fun dismissUse() { _useDraft.value = null }

    /**
     * Books the amount against the chosen lot. Amounts beyond what the lot
     * still holds can't confirm (the dialog's button is disabled for the same
     * reason), keeping every lot legal.
     */
    fun confirmUse() {
        val draft = _useDraft.value ?: return
        if (!draft.canConfirm) return
        val lot = draft.selectedLot ?: return
        viewModelScope.launch {
            dao.updateTransaction(
                when (draft.kind) {
                    UseKind.USED -> lot.copy(amountUsed = lot.amountUsed + draft.quantity)
                    UseKind.TOSSED -> lot.copy(amountThrown = lot.amountThrown + draft.quantity)
                }
            )
            _useDraft.value = null
        }
    }

    // ---- details modal -----------------------------------------------------

    private val _detailItemId = MutableStateFlow<Long?>(null)

    /** The open details modal, or null. Follows live DB changes; goes null if the item is deleted. */
    val detail: StateFlow<ItemDetailUi?> =
        combine(rows, dao.observeTransactions(), _detailItemId) { rows, txns, id ->
            if (id == null) null
            else rows.firstOrNull { it.item.id == id }?.let { row ->
                ItemDetailUi(
                    row = row,
                    transactions = txns.filter { it.catalogItemId == id }
                        .sortedWith(compareByDescending<PantryTransaction> { it.date }.thenByDescending { it.id })
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun openDetail(item: CatalogItem) { _detailItemId.value = item.id }

    fun closeDetail() {
        _detailItemId.value = null
        _txnDraft.value = null
    }

    fun deleteTransaction(txn: PantryTransaction) {
        viewModelScope.launch { dao.deleteTransaction(txn) }
    }

    fun clearHistory(item: CatalogItem) {
        viewModelScope.launch { dao.clearTransactions(item.id) }
    }

    // ---- transaction editor ------------------------------------------------

    private val _txnDraft = MutableStateFlow<TransactionDraft?>(null)
    val txnDraft: StateFlow<TransactionDraft?> = _txnDraft.asStateFlow()

    fun openTransactionEditor(txn: PantryTransaction) {
        _txnDraft.value = TransactionDraft(
            original = txn,
            date = LocalDate.ofEpochDay(txn.date),
            bought = txn.amountBought.toString(),
            used = txn.amountUsed.toString(),
            thrown = txn.amountThrown.toString(),
            expiry = txn.expiry?.let(LocalDate::ofEpochDay)
        )
    }

    fun setTxnDate(date: LocalDate) = _txnDraft.update { it?.copy(date = date) }
    fun setTxnBought(input: String) = _txnDraft.update { it?.copy(bought = input.filter { c -> c.isDigit() }) }
    fun setTxnUsed(input: String) = _txnDraft.update { it?.copy(used = input.filter { c -> c.isDigit() }) }
    fun setTxnThrown(input: String) = _txnDraft.update { it?.copy(thrown = input.filter { c -> c.isDigit() }) }
    fun setTxnExpiry(date: LocalDate?) = _txnDraft.update { it?.copy(expiry = date) }
    fun dismissTransactionEditor() { _txnDraft.value = null }

    /** Writes the edit if it's legal (see PantryMath.validate); illegal drafts can't confirm. */
    fun confirmTransactionEdit() {
        val draft = _txnDraft.value ?: return
        if (!draft.canConfirm) return
        viewModelScope.launch {
            dao.updateTransaction(draft.edited)
            _txnDraft.value = null
        }
    }

    companion object {
        /** Debounce window between the last keystroke and the autocomplete call. */
        const val DEBOUNCE_MS = 300L

        /** Fallback when Spoonacular returns no units for an ingredient. */
        const val DEFAULT_UNIT = "piece"

        /** Unit chips offered when modifying an item (possibleUnits aren't stored). */
        val COMMON_UNITS = listOf("piece", "g", "kg", "oz", "lb", "cup", "tbsp", "tsp", "ml", "l")

        /** Factory that injects the dependencies (no DI framework in the project). */
        fun factory(
            dao: PantryDao,
            repository: SpoonacularRepository = SpoonacularRepositoryImpl
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PantryViewModel(dao, repository) }
        }
    }
}
