package com.example.pantryparty.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.network.SpoonacularRepositoryImpl
import com.example.pantryparty.network.friendlyApiError
import com.example.pantryparty.pantry.PantryMath
import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.recipe.ConsumePlan
import com.example.pantryparty.recipe.DetailIngredientRow
import com.example.pantryparty.recipe.PantryConsumer
import com.example.pantryparty.recipe.RecipeDetailRows
import com.example.pantryparty.recipe.RecipeFilters
import com.example.pantryparty.recipe.RecipeMatch
import com.example.pantryparty.recipe.RecipeMatcher
import com.example.pantryparty.recipe.StapleSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen-level state for the recipe finder (filter panel + search results).
 *
 * [selectedIds] is the pantry-ingredient selection feeding `includeIngredients`:
 * null means "the whole pantry" (the default, resolved against the live pantry at
 * search time), an explicit set means the user has customized it.
 *
 * [ingredientSearch] records whether the *last* search included any ingredients.
 * Without ingredients, fillIngredients marks every recipe ingredient as "missed"
 * (verified against the live API), so those results must skip the missing-count
 * bucketing and the have/need pills.
 */
data class RecipeUiState(
    val selectedIds: Set<Long>? = null,
    val filters: RecipeFilters = RecipeFilters(),
    val recipes: List<RecipeByIngredient> = emptyList(),
    val staplesByRecipe: Map<Int, List<String>> = emptyMap(),   // recipe id -> staple names
    val ingredientSearch: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    /**
     * The follow-up details fetch failed, so staple lists are missing.
     *
     * Kept separate from [error] on purpose: the search itself succeeded and the
     * recipes are worth showing. [error] blanks the whole results list, which
     * would turn a partial degradation into a total one.
     */
    val staplesError: String? = null,
    val hasSearched: Boolean = false,
)

/**
 * The open recipe detail page, or null when closed.
 *
 * [title] and [image] are seeded from the tapped search row so the header paints
 * immediately; the fetched [info] overwrites them. That also means an error page
 * still shows the recipe you tapped rather than going blank.
 */
data class RecipeDetailUi(
    val recipeId: Int,
    val title: String,
    val image: String?,
    val info: RecipeInformation? = null,
    val rows: List<DetailIngredientRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
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
 * Owns the Recipes tab. Every search is a single complexSearch call — the user's
 * filters plus the selected pantry ingredients — whose fillIngredients response
 * already carries the have/missing split with staples ignored; the per-recipe
 * amount/consume checks fetch full details on demand.
 */
class RecipeViewModel(
    private val dao: PantryDao,
    private val repository: SpoonacularRepository
) : ViewModel() {

    /**
     * The pantry as the recipe finder sees it: catalog items with their stock
     * computed from the transaction ledger; items with nothing on hand are
     * omitted (you can't cook with them). Kept hot while subscribed.
     */
    val pantry: StateFlow<List<StockItem>> =
        combine(dao.observeCatalog(), dao.observeTransactions()) { catalog, txns ->
            PantryMath.stockSnapshot(catalog, txns)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** One-shot equivalent of [pantry] for the on-demand checks. */
    private suspend fun stockSnapshot(): List<StockItem> =
        PantryMath.stockSnapshot(dao.getCatalog(), dao.getTransactions())

    /**
     * The user's "always have" list as the matcher's lookup set. Read fresh per check
     * so edits made in the Pantry tab take effect without re-running the search.
     */
    private suspend fun alwaysHave(): StapleSet =
        StapleSet.of(dao.getStaples().map { it.spoonacularId to it.name })

    private val _uiState = MutableStateFlow(RecipeUiState())
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    // Per-recipe state keyed by recipe id, so cards survive recomposition/scrolling.
    private val _cardStates = MutableStateFlow<Map<Int, RecipeCardState>>(emptyMap())
    val cardStates: StateFlow<Map<Int, RecipeCardState>> = _cardStates.asStateFlow()

    // Full recipe details fetched once per search (for the card's staples list);
    // reused by the amount/consume checks to avoid re-fetching. Main-thread only.
    private var detailsById: Map<Int, RecipeInformation> = emptyMap()

    // The active search (and its follow-up staples fetch); cancelled whenever a
    // new search starts, so stale responses can't land.
    private var searchJob: Job? = null

    // The open recipe detail page, or null when closed. Deliberately its own flow
    // rather than fields on RecipeCardState: detail is one-at-a-time, screen-scoped
    // state, and folding it into the per-card map would recompose every result card
    // on each loading tick. Mirrors PantryViewModel's `detail` flow.
    private val _detail = MutableStateFlow<RecipeDetailUi?>(null)
    val detail: StateFlow<RecipeDetailUi?> = _detail.asStateFlow()

    private var detailJob: Job? = null

    // ---- Filter panel state ----

    fun setFilters(filters: RecipeFilters) = _uiState.update { it.copy(filters = filters) }

    fun resetFilters() = _uiState.update { it.copy(filters = RecipeFilters()) }

    fun toggleSelected(id: Long) = _uiState.update { ui ->
        // A tap on any chip materializes the implicit "whole pantry" default
        // into an explicit selection first.
        val current = ui.selectedIds ?: pantry.value.mapTo(mutableSetOf()) { it.id }
        val next = if (id in current) current - id else current + id
        ui.copy(selectedIds = next)
    }

    /** Back to the default: search with whatever the pantry holds at search time. */
    fun selectAllIngredients() = _uiState.update { it.copy(selectedIds = null) }

    fun clearSelectedIngredients() = _uiState.update { it.copy(selectedIds = emptySet()) }

    // ---- Searching ----

    /** The first search auto-runs once the pantry has anything in it. */
    fun autoSearchIfNeeded() {
        if (!_uiState.value.hasSearched && pantry.value.isNotEmpty()) search()
    }

    /**
     * Runs one complexSearch from the current selection + filters. Ingredient
     * searches keep the "fewest missing first" ordering unless the user picked a
     * sort themselves; the follow-up bulk details fetch fills in each card's
     * staples list (and primes the details cache).
     */
    fun search() {
        val names = selectedIngredientNames()
        val filters = _uiState.value.filters
        // Nothing to search on: no ingredients and no criteria set.
        if (names.isEmpty() && filters.activeFilterCount == 0) return
        val params = filters.toQueryMap().toMutableMap()
        if (names.isNotEmpty() && !params.containsKey("sort")) {
            params["sort"] = "min-missing-ingredients"
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true, error = null, staplesError = null,
                    hasSearched = true, staplesByRecipe = emptyMap()
                )
            }
            _cardStates.value = emptyMap()
            // An open detail belongs to results that are about to be replaced.
            closeRecipeDetail()
            detailsById = emptyMap()
            repository.searchRecipes(names, params, RECIPE_COUNT)
                .onSuccess { result ->
                    // Only ingredient-aware results are bucketed/filtered by missing
                    // count — see RecipeUiState.ingredientSearch.
                    val recipes = if (names.isNotEmpty()) RecipeMatcher.bucketByMissed(result) else result
                    _uiState.update {
                        it.copy(recipes = recipes, ingredientSearch = names.isNotEmpty(), loading = false)
                    }
                    loadStaples(recipes)
                }
                .onFailure { e -> _uiState.update { it.copy(error = friendlyApiError(e), loading = false) } }
        }
    }

    // ---- Recipe detail page ----

    /**
     * Opens the full detail page for a tapped search result.
     *
     * Usually free: [recipeDetails] serves out of the cache the search's bulk
     * fetch already populated, so no request is made. Same loader shape as
     * [checkAmounts] — snapshot the pantry and staples first, so the have/missing
     * split reflects the pantry as it is right now.
     */
    fun openRecipeDetail(recipe: RecipeByIngredient) {
        detailJob?.cancel()
        _detail.value = RecipeDetailUi(
            recipeId = recipe.id,
            title = recipe.title,
            image = recipe.image,
            loading = true
        )
        detailJob = viewModelScope.launch {
            val pantrySnapshot = stockSnapshot()
            val nonStaple = nonStapleIds(recipe.id)
            val alwaysHave = alwaysHave()
            recipeDetails(recipe.id)
                .onSuccess { info ->
                    val rows = info?.let {
                        RecipeDetailRows.build(
                            info = it,
                            match = RecipeMatcher.match(pantrySnapshot, it, nonStaple, alwaysHave),
                            staples = RecipeMatcher.staplesOf(pantrySnapshot, it, nonStaple, alwaysHave)
                        )
                    }.orEmpty()
                    updateDetail(recipe.id) {
                        it.copy(
                            info = info,
                            rows = rows,
                            title = info?.title ?: it.title,
                            image = info?.image ?: it.image,
                            loading = false,
                            // A call that succeeds but returns nothing for this id is
                            // still a failure as far as the reader is concerned.
                            error = if (info == null) "Couldn't load this recipe." else null
                        )
                    }
                }
                .onFailure { e ->
                    updateDetail(recipe.id) { it.copy(error = friendlyApiError(e), loading = false) }
                }
        }
    }

    fun closeRecipeDetail() {
        detailJob?.cancel()
        _detail.value = null
    }

    /**
     * Applies [transform] only while [recipeId] is still the open recipe, so a
     * response that lands after the user backed out — or opened something else —
     * can't overwrite the current page.
     */
    private fun updateDetail(recipeId: Int, transform: (RecipeDetailUi) -> RecipeDetailUi) {
        _detail.update { current -> if (current?.recipeId == recipeId) transform(current) else current }
    }

    /** The ingredient names the next search will pass as includeIngredients. */
    private fun selectedIngredientNames(): List<String> {
        val stock = pantry.value
        val selected = _uiState.value.selectedIds ?: return stock.map { it.name }
        return stock.filter { it.id in selected }.map { it.name }
    }

    /**
     * Fetches full details for the shown recipes once, caches them, and computes
     * each recipe's staple list (ingredients the search ignored). Runs inside the
     * search job so it is cancelled along with the search it belongs to.
     *
     * Best-effort: the recipes are already on screen and stay there if this fails.
     * The failure is reported through [RecipeUiState.staplesError] rather than
     * `error` — the latter blanks the results list — so the user learns why the
     * staple lists are missing instead of finding out by tapping into a card.
     * Cancellation never lands here: the repository rethrows it rather than
     * folding it into a failed Result.
     */
    private suspend fun loadStaples(recipes: List<RecipeByIngredient>) {
        if (recipes.isEmpty()) return
        // Same inputs the amount check uses, so the card's staple list matches exactly
        // what gets skipped.
        val pantrySnapshot = stockSnapshot()
        val alwaysHave = alwaysHave()
        repository.getRecipeInformationBulk(recipes.map { it.id })
            .onSuccess { infos ->
                detailsById = infos.associateBy { it.id }
                val staples = recipes.associate { r ->
                    val info = detailsById[r.id]
                    val nonStaple = (r.usedIngredients + r.missedIngredients).map { it.id }.toSet()
                    r.id to (info?.let {
                        RecipeMatcher.staplesOf(pantrySnapshot, it, nonStaple, alwaysHave)
                            .map { ing -> ing.name }
                    } ?: emptyList())
                }
                _uiState.update { it.copy(staplesByRecipe = staples, staplesError = null) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(staplesError = friendlyApiError(e)) }
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
            val pantrySnapshot = stockSnapshot()
            val nonStaple = nonStapleIds(recipeId)
            val alwaysHave = alwaysHave()
            recipeDetails(recipeId)
                .onSuccess { info ->
                    val match = info?.let {
                        RecipeMatcher.match(pantrySnapshot, it, nonStaple, alwaysHave)
                    }
                    updateCard(recipeId) { it.copy(amountCheck = match, checking = false) }
                }
                .onFailure { e -> updateCard(recipeId) { it.copy(checkError = friendlyApiError(e), checking = false) } }
        }
    }

    /** Fetches required amounts and opens the editable "I made this" dialog. */
    fun prepareConsume(recipeId: Int) {
        viewModelScope.launch {
            updateCard(recipeId) { it.copy(checking = true, checkError = null) }
            val pantrySnapshot = stockSnapshot()
            val nonStaple = nonStapleIds(recipeId)
            val alwaysHave = alwaysHave()
            recipeDetails(recipeId)
                .onSuccess { info ->
                    val draft = info
                        ?.let { PantryConsumer.plan(pantrySnapshot, it, nonStaple, alwaysHave) }
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

    /** Records the user-chosen deductions as "used" in the ledger, then closes the dialog. */
    fun confirmConsume(recipeId: Int) {
        val draft = _cardStates.value[recipeId]?.consume ?: return
        viewModelScope.launch {
            draft.plan.lines.forEachIndexed { i, line ->
                val deduct = draft.amounts.getOrElse(i) { 0 }
                if (deduct <= 0) return@forEachIndexed
                // Re-read the item so a catalog edit made after the dialog opened
                // (its plan holds a snapshot) can't be silently misapplied.
                val current = dao.findBySpoonacularId(line.item.spoonacularId) ?: return@forEachIndexed
                // If the unit changed too, the planned deduction is in the wrong
                // unit and can't be converted — leave the item alone.
                if (!RecipeMatcher.unitsMatch(current.unit, line.item.unit)) return@forEachIndexed
                // Cooking consumes the oldest stock first.
                for ((lot, take) in PantryMath.fifoPlan(dao.transactionsFor(current.id), deduct)) {
                    dao.updateTransaction(lot.copy(amountUsed = lot.amountUsed + take))
                }
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
        /** How many candidate recipes to request per search. */
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
