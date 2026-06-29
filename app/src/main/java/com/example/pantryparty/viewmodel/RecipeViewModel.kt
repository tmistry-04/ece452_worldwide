package com.example.pantryparty.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.recipe.ConsumeResult
import com.example.pantryparty.recipe.PantryConsumer
import com.example.pantryparty.recipe.RecipeMatch
import com.example.pantryparty.recipe.RecipeMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Which recipe-finding mode the screen is showing. */
enum class RecipeMode { FROM_PANTRY, PICK_INGREDIENTS }

/** Screen-level state for the recipe finder (mode toggle + search results). */
data class RecipeUiState(
    val mode: RecipeMode = RecipeMode.FROM_PANTRY,
    val selectedIds: Set<Long> = emptySet(),
    val recipes: List<RecipeByIngredient> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

/** Per-recipe state for the on-demand amount check and "I made this" flow. */
data class RecipeCardState(
    val checking: Boolean = false,
    val amountCheck: RecipeMatch? = null,   // null = amounts not checked yet
    val checkError: String? = null,
    val pendingConsume: ConsumeResult? = null,  // non-null = confirmation dialog open
)

/**
 * Owns the Recipes tab. Both modes use a single cheap findByIngredients call
 * (1 point), which already returns the have/missing split with staples ignored;
 * the per-recipe amount/consume checks fetch full details on demand.
 */
class RecipeViewModel(private val dao: PantryDao) : ViewModel() {

    /** Pantry snapshot driving the mode controls; kept hot while subscribed. */
    val pantry: StateFlow<List<PantryItem>> =
        dao.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(RecipeUiState())
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    // Per-recipe state keyed by recipe id, so cards survive recomposition/scrolling.
    private val _cardStates = MutableStateFlow<Map<Int, RecipeCardState>>(emptyMap())
    val cardStates: StateFlow<Map<Int, RecipeCardState>> = _cardStates.asStateFlow()

    // Guards Mode A so it auto-runs only once per entry, not on every pantry edit.
    private var pantryLoaded = false

    fun showFromPantry() {
        // Drop any picked-ingredient results and let Mode A re-run fresh.
        _uiState.update {
            it.copy(
                mode = RecipeMode.FROM_PANTRY,
                recipes = emptyList(),
                hasSearched = false,
                error = null
            )
        }
        _cardStates.value = emptyMap()
        pantryLoaded = false
    }

    fun showPickIngredients() {
        _uiState.update {
            it.copy(
                mode = RecipeMode.PICK_INGREDIENTS,
                recipes = emptyList(),
                hasSearched = false,
                error = null
            )
        }
        _cardStates.value = emptyMap()
    }

    fun toggleSelected(id: Long) = _uiState.update {
        val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
        it.copy(selectedIds = next)
    }

    /** Mode A auto-runs once when the screen opens (or re-enters) with a non-empty pantry. */
    fun autoLoadFromPantryIfNeeded() {
        val names = pantry.value.map { it.name }
        if (_uiState.value.mode == RecipeMode.FROM_PANTRY && names.isNotEmpty() && !pantryLoaded) {
            pantryLoaded = true
            findRecipes(names)
        }
    }

    fun refreshFromPantry() = findRecipes(pantry.value.map { it.name })

    fun searchSelected() {
        val names = pantry.value.filter { it.id in _uiState.value.selectedIds }.map { it.name }
        findRecipes(names)
    }

    // One cheap call: findByIngredients returns have/missing already bucketed.
    private fun findRecipes(names: List<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, hasSearched = true) }
            _cardStates.value = emptyMap()
            SpoonacularRepository.findRecipesByIngredients(names, number = RECIPE_COUNT)
                .onSuccess { result ->
                    _uiState.update { it.copy(recipes = RecipeMatcher.bucketByMissed(result), loading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = friendlyError(e), loading = false) } }
        }
    }

    // ---- Per-recipe actions (on-demand details fetch) ----

    /** Fetches required amounts and computes the "do I have enough?" breakdown. */
    fun checkAmounts(recipeId: Int) {
        viewModelScope.launch {
            updateCard(recipeId) { it.copy(checking = true, checkError = null) }
            val pantrySnapshot = dao.getAll()
            SpoonacularRepository.getRecipeInformationBulk(listOf(recipeId))
                .onSuccess { infos ->
                    val match = infos.firstOrNull()?.let { RecipeMatcher.match(pantrySnapshot, it) }
                    updateCard(recipeId) { it.copy(amountCheck = match, checking = false) }
                }
                .onFailure { e -> updateCard(recipeId) { it.copy(checkError = friendlyError(e), checking = false) } }
        }
    }

    /** Fetches required amounts and previews the deduction (the "I made this" flow). */
    fun prepareConsume(recipeId: Int) {
        viewModelScope.launch {
            updateCard(recipeId) { it.copy(checking = true, checkError = null) }
            val pantrySnapshot = dao.getAll()
            SpoonacularRepository.getRecipeInformationBulk(listOf(recipeId))
                .onSuccess { infos ->
                    val consume = infos.firstOrNull()?.let { PantryConsumer.consume(pantrySnapshot, it) }
                    updateCard(recipeId) { it.copy(pendingConsume = consume, checking = false) }
                }
                .onFailure { e -> updateCard(recipeId) { it.copy(checkError = friendlyError(e), checking = false) } }
        }
    }

    /** Applies the previewed deductions to the pantry, then closes the dialog. */
    fun confirmConsume(recipeId: Int) {
        val result = _cardStates.value[recipeId]?.pendingConsume ?: return
        viewModelScope.launch {
            result.toUpdate.forEach { dao.update(it) }
            result.toDelete.forEach { dao.delete(it) }
        }
        updateCard(recipeId) { it.copy(pendingConsume = null) }
    }

    fun dismissConsume(recipeId: Int) = updateCard(recipeId) { it.copy(pendingConsume = null) }

    private fun updateCard(recipeId: Int, transform: (RecipeCardState) -> RecipeCardState) {
        _cardStates.update { map ->
            map + (recipeId to transform(map[recipeId] ?: RecipeCardState()))
        }
    }

    companion object {
        /** How many candidate recipes to request per search (keeps API point cost ~1). */
        const val RECIPE_COUNT = 10

        /** Factory that injects the [dao] (no DI framework in the project). */
        fun factory(dao: PantryDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeViewModel(dao) }
        }
    }
}

/** Maps API failures to user-readable text (quota 402 gets a clear hint). */
private fun friendlyError(t: Throwable): String =
    if (t is HttpException && t.code() == 402) {
        "Daily Spoonacular quota reached — try again after the daily reset or add a new API key."
    } else {
        "Error: ${t.message}"
    }
