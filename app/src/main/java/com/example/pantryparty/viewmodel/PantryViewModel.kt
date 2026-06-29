package com.example.pantryparty.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.SpoonacularRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the guided "add ingredient" flow. While [selected] is null the search
 * step is shown; once an ingredient is picked it becomes the unit/amount step.
 */
data class AddIngredientUiState(
    val query: String = "",
    val suggestions: List<IngredientAutocomplete> = emptyList(),
    val selected: IngredientAutocomplete? = null,
    val selectedUnit: String? = null,
    val amount: String = "1",
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** Parsed amount; 0 when the field is empty/invalid (disables Save). */
    val quantity: Int get() = amount.toIntOrNull() ?: 0
}

/**
 * Owns the Pantry tab's state and pantry mutations. The UI observes [pantry] and
 * [addState] and forwards events here; no composable touches the DAO directly.
 */
class PantryViewModel(private val dao: PantryDao) : ViewModel() {

    /** The pantry contents, kept hot while the UI is subscribed. */
    val pantry: StateFlow<List<PantryItem>> =
        dao.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _addState = MutableStateFlow(AddIngredientUiState())
    val addState: StateFlow<AddIngredientUiState> = _addState.asStateFlow()

    // In-flight autocomplete request; cancelled on each keystroke for debouncing.
    private var autocompleteJob: Job? = null

    /** Debounced autocomplete: re-runs on each keystroke, cancelling the prior delay. */
    fun onQueryChange(query: String) {
        _addState.update { it.copy(query = query) }
        autocompleteJob?.cancel()
        // No lookups once an ingredient is chosen, or for very short queries.
        if (_addState.value.selected != null || query.trim().length < 2) {
            _addState.update { it.copy(suggestions = emptyList()) }
            return
        }
        autocompleteJob = viewModelScope.launch {
            delay(300)
            _addState.update { it.copy(loading = true, error = null) }
            SpoonacularRepository.autocompleteIngredients(query.trim())
                .onSuccess { result -> _addState.update { it.copy(suggestions = result, loading = false) } }
                .onFailure { e -> _addState.update { it.copy(error = e.message, loading = false) } }
        }
    }

    fun selectSuggestion(suggestion: IngredientAutocomplete) {
        autocompleteJob?.cancel()
        _addState.update {
            it.copy(
                selected = suggestion,
                selectedUnit = suggestion.possibleUnits.firstOrNull(),
                suggestions = emptyList()
            )
        }
    }

    fun selectUnit(unit: String) = _addState.update { it.copy(selectedUnit = unit) }

    /** Keeps only digits so the amount field stays a valid count. */
    fun onAmountChange(input: String) =
        _addState.update { it.copy(amount = input.filter { c -> c.isDigit() }) }

    /** Returns to a blank search step, discarding the in-progress entry. */
    fun resetAdd() {
        autocompleteJob?.cancel()
        _addState.value = AddIngredientUiState()
    }

    /**
     * Persists the chosen ingredient, merging into the existing row instead of
     * inserting a duplicate for the same ingredient.
     */
    fun save() {
        val state = _addState.value
        val ingredient = state.selected ?: return
        val qty = state.quantity
        if (qty <= 0) return
        val unit = state.selectedUnit ?: "piece"
        viewModelScope.launch {
            val existing = dao.findBySpoonacularId(ingredient.id)
            val toSave = when {
                existing == null -> PantryItem(
                    name = ingredient.name,
                    quantity = qty,
                    unit = unit,
                    spoonacularId = ingredient.id,
                    imageUrl = ingredient.image   // persist for thumbnails
                )
                // Same unit -> add to what's already on hand.
                existing.unit == unit ->
                    existing.copy(quantity = existing.quantity + qty)
                // Different unit -> adopt the newly entered unit/amount.
                else -> existing.copy(quantity = qty, unit = unit, imageUrl = ingredient.image)
            }
            dao.upsert(toSave)
            resetAdd()
        }
    }

    /** Bump the count by one. */
    fun increment(item: PantryItem) {
        viewModelScope.launch { dao.upsert(item.copy(quantity = item.quantity + 1)) }
    }

    /** Decrement; using the last one removes the row entirely. */
    fun decrement(item: PantryItem) {
        viewModelScope.launch {
            if (item.quantity <= 1) dao.delete(item)
            else dao.upsert(item.copy(quantity = item.quantity - 1))
        }
    }

    fun delete(item: PantryItem) {
        viewModelScope.launch { dao.delete(item) }
    }

    companion object {
        /** Factory that injects the [dao] (no DI framework in the project). */
        fun factory(dao: PantryDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { PantryViewModel(dao) }
        }
    }
}
