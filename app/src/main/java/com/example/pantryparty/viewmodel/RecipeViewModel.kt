package com.example.pantryparty.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.network.SpoonacularRepositoryImpl
import com.example.pantryparty.network.friendlyApiError
import com.example.pantryparty.recipe.ConsumePlan
import com.example.pantryparty.recipe.PantryConsumer
import com.example.pantryparty.recipe.RecipeMatch
import com.example.pantryparty.recipe.RecipeMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which recipe-finding mode the screen is showing. */
enum class RecipeMode { FROM_PANTRY, PICK_INGREDIENTS }

/** Screen-level state for the recipe finder (mode toggle + search results). */
data class RecipeUiState(
    val mode: RecipeMode = RecipeMode.FROM_PANTRY,
    val selectedIds: Set<Long> = emptySet(),
    val recipes: List<RecipeByIngredient> = emptyList(),
    val staplesByRecipe: Map<Int, List<String>> = emptyMap(),   // recipe id -> staple names
    val loading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

/** Per-recipe state for the on-demand amount check and "I made this" flow. */
data class RecipeCardState(
    val checking: Boolean = false,
    val amountCheck: RecipeMatch? = null,   // null = amounts not checked yet
    val checkError: String? = null,
    val consume: ConsumeDraft? = null,      // non-null = "I made this" dialog open
)

/**
 * Open "I made this" dialog state: the suggested [plan] plus the user's current
 * per-line deduction [amounts] (parallel to `plan.lines`, each clamped to what's
 * on hand). Nothing is written to the pantry until the user confirms.
 */
data class ConsumeDraft(
    val plan: ConsumePlan,
    val amounts: List<Int>
)

/**
 * Owns the Recipes tab. Both modes use a single cheap findByIngredients call
 * (1 point), which already returns the have/missing split with staples ignored;
 * the per-recipe amount/consume checks fetch full details on demand.
 */
class RecipeViewModel(
    private val dao: PantryDao,
    private val repository: SpoonacularRepository
) : ViewModel() {

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

    // Full recipe details fetched once per search (for the card's staples list);
    // reused by the amount/consume checks to avoid re-fetching. Main-thread only.
    private var detailsById: Map<Int, RecipeInformation> = emptyMap()

    // Guards Mode A so it auto-runs only once per entry, not on every pantry edit.
    private var pantryLoaded = false

    // The active search (and its follow-up staples fetch); cancelled whenever a
    // new search starts or the mode changes, so stale responses can't land.
    private var searchJob: Job? = null

    fun showFromPantry() {
        // Drop any picked-ingredient results and let Mode A re-run fresh.
        switchMode(RecipeMode.FROM_PANTRY)
        pantryLoaded = false
    }

    fun showPickIngredients() = switchMode(RecipeMode.PICK_INGREDIENTS)

    private fun switchMode(mode: RecipeMode) {
        searchJob?.cancel()
        // selectedIds is deliberately kept, so picks survive a round-trip through
        // the other mode. Ids of since-deleted rows are harmless: searchSelected
        // re-filters against the live pantry.
        _uiState.update {
            it.copy(
                mode = mode,
                recipes = emptyList(),
                staplesByRecipe = emptyMap(),
                hasSearched = false,
                loading = false,
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

    // findByIngredients returns have/missing already bucketed; a follow-up bulk
    // details fetch fills in each card's staples list (and primes the cache).
    private fun findRecipes(names: List<String>) {
        if (names.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, hasSearched = true, staplesByRecipe = emptyMap()) }
            _cardStates.value = emptyMap()
            detailsById = emptyMap()
            repository.findRecipesByIngredients(names, number = RECIPE_COUNT)
                .onSuccess { result ->
                    val recipes = RecipeMatcher.bucketByMissed(result)
                    _uiState.update { it.copy(recipes = recipes, loading = false) }
                    loadStaples(recipes)
                }
                .onFailure { e -> _uiState.update { it.copy(error = friendlyApiError(e), loading = false) } }
        }
    }

    /**
     * Fetches full details for the shown recipes once, caches them, and computes
     * each recipe's staple list (ingredients the search ignored). Runs inside the
     * search job so it is cancelled along with the search it belongs to.
     * Best-effort: if this call fails (e.g. quota), the results still show, just
     * without staples.
     */
    private suspend fun loadStaples(recipes: List<RecipeByIngredient>) {
        if (recipes.isEmpty()) return
        repository.getRecipeInformationBulk(recipes.map { it.id })
            .onSuccess { infos ->
                detailsById = infos.associateBy { it.id }
                val staples = recipes.associate { r ->
                    val info = detailsById[r.id]
                    val nonStaple = (r.usedIngredients + r.missedIngredients).map { it.id }.toSet()
                    r.id to (info?.let { RecipeMatcher.staplesOf(it, nonStaple).map { ing -> ing.name } } ?: emptyList())
                }
                _uiState.update { it.copy(staplesByRecipe = staples) }
            }
    }

    /** Recipe details, reusing the per-search batch fetch when it's available. */
    private suspend fun recipeDetails(recipeId: Int): Result<RecipeInformation?> =
        detailsById[recipeId]?.let { Result.success(it) }
            ?: repository.getRecipeInformationBulk(listOf(recipeId)).map { infos ->
                // Cache the one-off fetch too, so a second check on the same card
                // (amounts, then "I made this") doesn't hit the API again.
                infos.firstOrNull()?.also { detailsById = detailsById + (it.id to it) }
            }

    // ---- Per-recipe actions (on-demand details fetch) ----

    /**
     * The recipe's non-staple ingredient ids, taken from the search result's
     * used∪missed sets (staples were excluded there by `ignorePantry`). Lets the
     * amount/consume checks treat staples separately. Null if the recipe isn't in
     * the current results (falls back to checking every ingredient).
     */
    private fun nonStapleIds(recipeId: Int): Set<Int>? =
        _uiState.value.recipes.firstOrNull { it.id == recipeId }
            ?.let { r -> (r.usedIngredients + r.missedIngredients).map { it.id }.toSet() }

    /** Fetches required amounts and computes the "do I have enough?" breakdown. */
    fun checkAmounts(recipeId: Int) {
        viewModelScope.launch {
            updateCard(recipeId) { it.copy(checking = true, checkError = null) }
            val pantrySnapshot = dao.getAll()
            val nonStaple = nonStapleIds(recipeId)
            recipeDetails(recipeId)
                .onSuccess { info ->
                    val match = info?.let { RecipeMatcher.match(pantrySnapshot, it, nonStaple) }
                    updateCard(recipeId) { it.copy(amountCheck = match, checking = false) }
                }
                .onFailure { e -> updateCard(recipeId) { it.copy(checkError = friendlyApiError(e), checking = false) } }
        }
    }

    /** Fetches required amounts and opens the editable "I made this" dialog. */
    fun prepareConsume(recipeId: Int) {
        viewModelScope.launch {
            updateCard(recipeId) { it.copy(checking = true, checkError = null) }
            val pantrySnapshot = dao.getAll()
            val nonStaple = nonStapleIds(recipeId)
            recipeDetails(recipeId)
                .onSuccess { info ->
                    val draft = info
                        ?.let { PantryConsumer.plan(pantrySnapshot, it, nonStaple) }
                        ?.let { plan -> ConsumeDraft(plan, plan.lines.map { it.suggested }) }
                    updateCard(recipeId) { it.copy(consume = draft, checking = false) }
                }
                .onFailure { e -> updateCard(recipeId) { it.copy(checkError = friendlyApiError(e), checking = false) } }
        }
    }

    /** Bumps one dialog line's deduction amount, clamped to [0, quantity on hand]. */
    fun adjustConsume(recipeId: Int, lineIndex: Int, delta: Int) = updateCard(recipeId) { card ->
        val draft = card.consume ?: return@updateCard card
        val line = draft.plan.lines.getOrNull(lineIndex) ?: return@updateCard card
        val amounts = draft.amounts.toMutableList().also {
            it[lineIndex] = (it[lineIndex] + delta).coerceIn(0, line.item.quantity)
        }
        card.copy(consume = draft.copy(amounts = amounts))
    }

    /** Applies the user-chosen deductions to the pantry, then closes the dialog. */
    fun confirmConsume(recipeId: Int) {
        val draft = _cardStates.value[recipeId]?.consume ?: return
        viewModelScope.launch {
            draft.plan.lines.forEachIndexed { i, line ->
                val deduct = draft.amounts.getOrElse(i) { 0 }
                if (deduct <= 0) return@forEachIndexed
                // Re-read the row so a pantry edit made after the dialog opened
                // (its plan holds a snapshot) can't be silently overwritten.
                val current = dao.findBySpoonacularId(line.item.spoonacularId) ?: return@forEachIndexed
                // If the unit changed too, the planned deduction is in the wrong
                // unit and can't be converted — leave the row alone.
                if (!RecipeMatcher.unitsMatch(current.unit, line.item.unit)) return@forEachIndexed
                val remaining = current.quantity - deduct
                if (remaining <= 0) dao.delete(current)
                else dao.update(current.copy(quantity = remaining))
            }
        }
        updateCard(recipeId) { it.copy(consume = null) }
    }

    fun dismissConsume(recipeId: Int) = updateCard(recipeId) { it.copy(consume = null) }

    private fun updateCard(recipeId: Int, transform: (RecipeCardState) -> RecipeCardState) {
        _cardStates.update { map ->
            map + (recipeId to transform(map[recipeId] ?: RecipeCardState()))
        }
    }

    companion object {
        /** How many candidate recipes to request per search (keeps API point cost ~1). */
        const val RECIPE_COUNT = 10

        /** Factory that injects the dependencies (no DI framework in the project). */
        fun factory(
            dao: PantryDao,
            repository: SpoonacularRepository = SpoonacularRepositoryImpl
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeViewModel(dao, repository) }
        }
    }
}
