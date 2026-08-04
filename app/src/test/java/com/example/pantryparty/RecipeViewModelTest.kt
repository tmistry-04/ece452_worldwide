package com.example.pantryparty

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.fakes.FakePantryDao
import com.example.pantryparty.fakes.FakeSpoonacularRepository
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.IngredientSubstitutes
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.RecipeIngredientBrief
import com.example.pantryparty.network.SimilarRecipe
import com.example.pantryparty.recipe.IngredientStatus
import com.example.pantryparty.recipe.RecipeFilters
import com.example.pantryparty.viewmodel.RecipeViewModel
import com.example.pantryparty.viewmodel.SubstituteState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val dao = FakePantryDao()
    private val repo = FakeSpoonacularRepository()

    /**
     * Creates the ViewModel and subscribes its pantry StateFlow (it is
     * WhileSubscribed, so without a collector `pantry.value` would stay empty).
     */
    private fun TestScope.startViewModel(): RecipeViewModel {
        val vm = RecipeViewModel(dao, repo)
        backgroundScope.launch { vm.pantry.collect {} }
        advanceUntilIdle()
        return vm
    }

    /** Catalogs the ingredient and, when [stock] > 0, buys one lot of it. */
    private fun seedStock(name: String, stock: Int, unit: String, spoonacularId: Int): CatalogItem {
        val item = dao.seedItems(
            CatalogItem(name = name, desiredAmount = stock, spoonacularId = spoonacularId, sortOrder = 0, unit = unit)
        ).single()
        if (stock > 0) {
            dao.seedTransactions(PantryTransaction(catalogItemId = item.id, date = 100, amountBought = stock))
        }
        return item
    }

    private fun brief(id: Int) =
        RecipeIngredientBrief(id = id, name = "i$id", amount = 1.0, unit = "g")

    private fun searchResult(id: Int, missed: Int) = RecipeByIngredient(
        id = id, title = "R$id",
        missedIngredients = List(missed) { brief(id * 100 + it) }
    )

    // --- the pantry snapshot -------------------------------------------------

    @Test
    fun pantry_reflectsLedgerStock_andOmitsZeroStockItems() = runTest {
        val egg = seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        dao.seedTransactions(
            PantryTransaction(catalogItemId = egg.id, date = 101, amountBought = 2, amountUsed = 2)
        )
        seedStock("milk", stock = 0, unit = "l", spoonacularId = 2)   // catalogued, none on hand
        val vm = startViewModel()

        val snapshot = vm.pantry.value
        assertEquals(listOf("egg"), snapshot.map { it.name })
        assertEquals(6, snapshot.single().quantity)   // 6 + (2 bought − 2 used)
    }

    // --- searching -----------------------------------------------------------

    @Test
    fun search_bucketsSortsAndDropsBeyondMaxMissing() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.success(
            listOf(searchResult(1, missed = 2), searchResult(2, missed = 4), searchResult(3, missed = 0))
        )
        val vm = startViewModel()

        vm.search()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertEquals(listOf(3, 1), ui.recipes.map { it.id })  // ready first, missed=4 dropped
        assertTrue(ui.hasSearched)
        assertTrue(ui.ingredientSearch)
        assertFalse(ui.loading)
        assertNull(ui.error)
        // The whole pantry is the default selection, with the matching sort applied.
        assertEquals(listOf("egg"), repo.lastRecipeQuery)
        assertEquals("min-missing-ingredients", repo.lastRecipeFilters!!["sort"])
    }

    @Test
    fun filterOnlySearch_keepsResultsAndSkipsMissingBucketing() = runTest {
        // Empty pantry: the search runs purely on filters. fillIngredients then
        // reports every recipe ingredient as "missed", so nothing may be bucketed
        // or dropped by missing count.
        repo.recipesResult = Result.success(listOf(searchResult(1, missed = 16)))
        val vm = startViewModel()

        vm.setFilters(RecipeFilters(query = "pasta"))
        vm.search()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertEquals(listOf(1), ui.recipes.map { it.id })   // kept despite 16 "missed"
        assertFalse(ui.ingredientSearch)
        assertEquals(emptyList<String>(), repo.lastRecipeQuery)
        assertEquals("pasta", repo.lastRecipeFilters!!["query"])
        assertNull(repo.lastRecipeFilters!!["sort"])         // no ingredient sort forced
    }

    @Test
    fun search_withNoIngredientsAndNoFilters_doesNothing() = runTest {
        val vm = startViewModel()   // empty pantry, default filters

        vm.search()
        advanceUntilIdle()

        assertEquals(0, repo.recipesCalls)
        assertFalse(vm.uiState.value.hasSearched)
    }

    @Test
    fun toggleSelected_narrowsTheSearchToThePickedItems() = runTest {
        val egg = seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        seedStock("milk", stock = 1, unit = "l", spoonacularId = 2)
        val vm = startViewModel()

        vm.toggleSelected(egg.id)   // deselect egg out of the implicit "whole pantry"
        vm.search()
        advanceUntilIdle()

        assertEquals(listOf("milk"), repo.lastRecipeQuery)
    }

    @Test
    fun quotaFailure_showsTheFriendlyMessage() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = startViewModel()

        vm.search()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertTrue("expected quota hint, got: ${ui.error}", ui.error!!.contains("quota"))
        assertFalse(ui.loading)
    }

    /** Regression: a new search must cancel the in-flight one so stale responses can't land. */
    @Test
    fun aNewSearch_cancelsTheInFlightOne() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.hangRecipes = true
        val vm = startViewModel()

        vm.search()
        assertTrue(vm.uiState.value.loading)   // request is in flight (hanging)

        repo.hangRecipes = false
        repo.recipesResult = Result.success(listOf(searchResult(1, missed = 0)))
        vm.search()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertEquals(listOf(1), ui.recipes.map { it.id })   // second search's answer
        assertFalse(ui.loading)                             // no stuck spinner
        assertNull(ui.error)                                // cancellation is not an error
        assertEquals(2, repo.recipesCalls)
    }

    /** Regression: a one-off details fetch is cached for the next check on the same card. */
    @Test
    fun recipeDetails_oneOffFetchIsCachedForLaterChecks() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(
            listOf(
                RecipeInformation(
                    id = 10, title = "Omelette",
                    extendedIngredients = listOf(
                        ExtendedIngredient(id = 1, name = "egg", amount = 2.0, unit = "piece")
                    )
                )
            )
        )
        val vm = startViewModel()

        vm.checkAmounts(10)
        advanceUntilIdle()
        assertEquals(1, repo.detailsCalls)

        vm.prepareConsume(10)
        advanceUntilIdle()
        assertEquals(1, repo.detailsCalls)   // second check served from the cache
    }

    // --- the follow-up staples fetch ----------------------------------------

    @Test
    fun staplesFetchFailure_isReported_butTheRecipesStayOnScreen() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.success(listOf(searchResult(10, missed = 0)))
        repo.detailsResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = startViewModel()

        vm.search()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertTrue(ui.staplesError!!.contains("quota"))
        // `error` blanks the results list, so the partial failure must not set it.
        assertNull(ui.error)
        assertEquals(1, ui.recipes.size)
    }

    @Test
    fun aSucceedingSearch_clearsAnEarlierStaplesError() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.success(listOf(searchResult(10, missed = 0)))
        repo.detailsResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = startViewModel()
        vm.search()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.staplesError)

        repo.detailsResult = Result.success(listOf(omelette()))
        vm.search()
        advanceUntilIdle()

        assertNull(vm.uiState.value.staplesError)
    }

    @Test
    fun aCancelledSearch_doesNotReportAStaplesError() = runTest {
        // The repository rethrows CancellationException rather than folding it into
        // a failed Result, so replacing an in-flight search must stay silent.
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.success(listOf(searchResult(10, missed = 0)))
        repo.hangDetails = true
        val vm = startViewModel()
        vm.search()
        advanceUntilIdle()

        repo.hangDetails = false
        repo.detailsResult = Result.success(listOf(omelette()))
        vm.search()
        advanceUntilIdle()

        assertNull(vm.uiState.value.staplesError)
    }

    // --- the recipe detail page ---------------------------------------------

    /** An omelette needing 2 eggs and 3 tbsp butter. */
    private fun omelette(id: Int = 10) = RecipeInformation(
        id = id, title = "Omelette", readyInMinutes = 15, servings = 2,
        glutenFree = true,
        instructions = "<p>Beat the eggs.</p>",
        extendedIngredients = listOf(
            ExtendedIngredient(id = 1, name = "egg", amount = 2.0, unit = "piece"),
            ExtendedIngredient(id = 2, name = "butter", amount = 3.0, unit = "tbsp")
        )
    )

    @Test
    fun openRecipeDetail_populatesInfoAndClassifiedRows() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)   // enough
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertEquals(10, detail.recipeId)
        assertEquals("Omelette", detail.title)
        assertEquals(15, detail.info?.readyInMinutes)
        assertFalse(detail.loading)
        assertNull(detail.error)

        // Rows follow the recipe's own ingredient order, not the matcher's buckets.
        assertEquals(listOf("egg", "butter"), detail.rows.map { it.required.name })
        assertEquals(IngredientStatus.HAVE, detail.rows[0].status)
        assertEquals(IngredientStatus.MISSING, detail.rows[1].status)
    }

    /**
     * The whole cost argument for the detail page: the search already paid for the
     * bulk details fetch, so opening a recipe must not hit the API again.
     */
    @Test
    fun openRecipeDetail_reusesTheSearchCache_withoutAnotherApiCall() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.success(listOf(searchResult(10, missed = 0)))
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()

        vm.search()
        advanceUntilIdle()
        assertEquals(1, repo.detailsCalls)   // the search's own staples fetch

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        assertEquals(1, repo.detailsCalls)   // served from the cache
        assertEquals("Omelette", vm.detail.value?.info?.title)
    }

    @Test
    fun openRecipeDetail_showsTheTappedRowsTitleWhileLoading() = runTest {
        repo.hangDetails = true
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertTrue(detail.loading)
        assertEquals("R10", detail.title)    // seeded from the search row, not blank
        assertNull(detail.info)
    }

    @Test
    fun openRecipeDetail_quotaFailure_showsTheFriendlyMessage() = runTest {
        repo.detailsResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertTrue(detail.error!!.contains("quota"))
        assertFalse(detail.loading)
        assertEquals("R10", detail.title)    // still renders what was tapped
    }

    @Test
    fun openRecipeDetail_whenTheApiHasNothingForThatId_isAnError() = runTest {
        repo.detailsResult = Result.success(emptyList())
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        assertNotNull(vm.detail.value!!.error)
        assertFalse(vm.detail.value!!.loading)
    }

    @Test
    fun closeRecipeDetail_clearsTheState() = runTest {
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        vm.closeRecipeDetail()

        assertNull(vm.detail.value)
    }

    @Test
    fun openingASecondRecipe_cancelsTheFirstsInFlightLoad() = runTest {
        repo.hangDetails = true
        val vm = startViewModel()
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        repo.hangDetails = false
        repo.detailsResult = Result.success(listOf(omelette(id = 20).copy(title = "Second")))
        vm.openRecipeDetail(searchResult(20, missed = 0))
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertEquals(20, detail.recipeId)
        assertEquals("Second", detail.title)
        assertFalse(detail.loading)
    }

    @Test
    fun aNewSearch_closesTheOpenDetail() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        assertNotNull(vm.detail.value)

        vm.search()
        advanceUntilIdle()

        // The open page belonged to results that have just been replaced.
        assertNull(vm.detail.value)
    }

    @Test
    fun openRecipeDetail_forARecipeNotInTheResults_checksEveryIngredient() = runTest {
        // No search has run, so nonStapleIds() returns null and nothing can be
        // assumed to be a staple — every ingredient gets amount-checked.
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        val rows = vm.detail.value!!.rows
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.status == IngredientStatus.STAPLE })
    }

    // --- "more like this" and the detail back stack ---------------------------

    private fun similar(id: Int) = SimilarRecipe(id = id, title = "S$id", image = "S$id.jpg")

    @Test
    fun openingARecipe_loadsItsSuggestions() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        repo.similarResult = Result.success(listOf(similar(20), similar(21)))
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        assertEquals(listOf(20, 21), vm.detail.value!!.similar.map { it.id })
    }

    @Test
    fun openingASuggestion_pushesTheBackStack_andCloseReturnsToIt() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        repo.similarResult = Result.success(listOf(similar(20)))
        val vm = startViewModel()
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        repo.detailsResult = Result.success(listOf(omelette(id = 20).copy(title = "Second")))
        vm.openSimilarRecipe(similar(20))
        advanceUntilIdle()
        assertEquals(20, vm.detail.value!!.recipeId)

        // Back goes to the recipe you came from, not out of the page.
        vm.closeRecipeDetail()
        assertEquals(10, vm.detail.value!!.recipeId)

        // And once more backs out entirely.
        vm.closeRecipeDetail()
        assertNull(vm.detail.value)
    }

    @Test
    fun aRecipeNotInTheResults_reportsThatStaplesAreUnknown() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.success(listOf(searchResult(10, missed = 0)))
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()
        vm.search()
        advanceUntilIdle()

        // In the results: Spoonacular's staple guess is available.
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        assertTrue(vm.detail.value!!.staplesKnown)

        // A suggestion is not in the results, so it isn't.
        repo.detailsResult = Result.success(listOf(omelette(id = 20)))
        vm.openSimilarRecipe(similar(20))
        advanceUntilIdle()
        assertFalse(vm.detail.value!!.staplesKnown)
    }

    @Test
    fun aNewSearch_clearsTheBackStack() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        vm.openSimilarRecipe(similar(20))
        advanceUntilIdle()

        vm.search()
        advanceUntilIdle()

        // Both pages belonged to results that no longer exist — close must not pop
        // one of them back into view.
        assertNull(vm.detail.value)
    }

    @Test
    fun reopeningARecipe_servesSuggestionsFromCache() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        repo.similarResult = Result.success(listOf(similar(20)))
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        assertEquals(1, repo.similarCalls)

        vm.closeRecipeDetail()
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        assertEquals(1, repo.similarCalls)
        assertEquals(listOf(20), vm.detail.value!!.similar.map { it.id })
    }

    @Test
    fun aSuggestionsFailure_leavesThePageUsable() = runTest {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        repo.similarResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = startViewModel()

        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertTrue(detail.similar.isEmpty())   // the row simply doesn't appear
        assertNull(detail.error)
        assertEquals("Omelette", detail.info?.title)
    }

    // --- ingredient substitutes ----------------------------------------------

    /** Opens the detail page for the omelette, whose butter is missing. */
    private suspend fun TestScope.detailWithMissingButter(): RecipeViewModel {
        seedStock("egg", stock = 6, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(listOf(omelette()))
        val vm = startViewModel()
        vm.openRecipeDetail(searchResult(10, missed = 0))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun toggleSubstitutes_loadsAndExpandsThePanel() = runTest {
        repo.substitutesResult = Result.success(
            IngredientSubstitutes(
                status = "success",
                ingredient = "butter",
                substitutes = listOf("1 cup = 1 cup margarine")
            )
        )
        val vm = detailWithMissingButter()

        vm.toggleSubstitutes("butter")
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertTrue("butter" in detail.expandedSubstitutes)
        assertEquals(
            listOf("1 cup = 1 cup margarine"),
            (detail.substitutes["butter"] as SubstituteState.Loaded).options
        )
        assertEquals("butter", repo.lastSubstitutesName)
    }

    @Test
    fun statusFailure_isEmptyNotFailed() = runTest {
        // HTTP 200 with status:"failure" — a miss, not an error worth retrying.
        repo.substitutesResult = Result.success(
            IngredientSubstitutes(status = "failure", message = "Could not find any substitutes.")
        )
        val vm = detailWithMissingButter()

        vm.toggleSubstitutes("butter")
        advanceUntilIdle()

        val state = vm.detail.value!!.substitutes["butter"]
        assertTrue(state is SubstituteState.Empty)
        assertEquals("Could not find any substitutes.", (state as SubstituteState.Empty).message)
    }

    @Test
    fun collapsingAndReopeningAPanel_doesNotRefetch() = runTest {
        repo.substitutesResult = Result.success(
            IngredientSubstitutes(status = "success", substitutes = listOf("margarine"))
        )
        val vm = detailWithMissingButter()

        vm.toggleSubstitutes("butter")
        advanceUntilIdle()
        assertEquals(1, repo.substitutesCalls)

        vm.toggleSubstitutes("butter")   // collapse
        vm.toggleSubstitutes("butter")   // reopen
        advanceUntilIdle()

        assertEquals(1, repo.substitutesCalls)
        // The loaded result survived the collapse.
        assertTrue(vm.detail.value!!.substitutes["butter"] is SubstituteState.Loaded)
    }

    @Test
    fun aFailedLookup_isRetriedOnReopen() = runTest {
        repo.substitutesResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = detailWithMissingButter()

        vm.toggleSubstitutes("butter")
        advanceUntilIdle()
        val failed = vm.detail.value!!.substitutes["butter"]
        assertTrue(failed is SubstituteState.Failed)
        assertTrue((failed as SubstituteState.Failed).message.contains("quota"))

        repo.substitutesResult = Result.success(
            IngredientSubstitutes(status = "success", substitutes = listOf("margarine"))
        )
        vm.toggleSubstitutes("butter")   // collapse
        vm.toggleSubstitutes("butter")   // reopen -> retried, unlike a success
        advanceUntilIdle()

        assertEquals(2, repo.substitutesCalls)
        assertTrue(vm.detail.value!!.substitutes["butter"] is SubstituteState.Loaded)
    }

    @Test
    fun aSubstituteFailure_leavesTheRestOfThePageAlone() = runTest {
        repo.substitutesResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = detailWithMissingButter()

        vm.toggleSubstitutes("butter")
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertNull(detail.error)                       // the page itself is fine
        assertEquals(2, detail.rows.size)
        assertEquals("Omelette", detail.info?.title)
    }

    @Test
    fun substitutesAreNotFetchedUntilTapped() = runTest {
        val vm = detailWithMissingButter()
        // A page with missing ingredients must not spend a point per row on open.
        assertEquals(0, repo.substitutesCalls)
        assertTrue(vm.detail.value!!.substitutes.isEmpty())
    }

    // --- "I made this" deduction --------------------------------------------

    @Test
    fun confirmConsume_recordsUseInTheLedger_andEmptiedItemsLeaveTheSnapshot() = runTest {
        val egg = seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        seedStock("butter", stock = 5, unit = "tbsp", spoonacularId = 2)
        repo.detailsResult = Result.success(
            listOf(
                RecipeInformation(
                    id = 10, title = "Omelette",
                    extendedIngredients = listOf(
                        ExtendedIngredient(id = 1, name = "egg", amount = 2.0, unit = "piece"),
                        ExtendedIngredient(id = 2, name = "butter", amount = 1.0, unit = "tbsp")
                    )
                )
            )
        )
        val vm = startViewModel()

        vm.prepareConsume(10)
        advanceUntilIdle()
        val draft = vm.cardStates.value[10]?.consume!!
        assertEquals(listOf(2, 1), draft.amounts)   // prefilled from the recipe

        vm.confirmConsume(10)
        advanceUntilIdle()

        // The catalog keeps both items; only the ledger changed.
        assertEquals(2, dao.itemsSnapshot().size)
        val eggLot = dao.transactionsSnapshot().single { it.catalogItemId == egg.id }
        assertEquals(2, eggLot.amountUsed)
        // Emptied egg drops out of the cookable snapshot; butter is down to 4.
        assertEquals(listOf("butter"), vm.pantry.value.map { it.name })
        assertEquals(4, vm.pantry.value.single().quantity)
        assertNull(vm.cardStates.value[10]?.consume)               // dialog closed
    }

    @Test
    fun confirmConsume_splitsAcrossLotsOldestFirst() = runTest {
        val egg = seedStock("egg", stock = 3, unit = "piece", spoonacularId = 1)   // lot at date 100
        val newer = dao.seedTransactions(
            PantryTransaction(catalogItemId = egg.id, date = 200, amountBought = 3)
        ).single()
        repo.detailsResult = Result.success(
            listOf(
                RecipeInformation(
                    id = 10, title = "Big omelette",
                    extendedIngredients = listOf(
                        ExtendedIngredient(id = 1, name = "egg", amount = 4.0, unit = "piece")
                    )
                )
            )
        )
        val vm = startViewModel()

        vm.prepareConsume(10)
        advanceUntilIdle()
        vm.confirmConsume(10)
        advanceUntilIdle()

        val lots = dao.transactionsSnapshot().sortedBy { it.date }
        assertEquals(3, lots.first().amountUsed)             // oldest lot drained first
        assertEquals(1, lots.single { it.id == newer.id }.amountUsed)
    }

    /** Regression: a unit change while the dialog is open must not be deducted against. */
    @Test
    fun confirmConsume_skipsItemsWhoseUnitChangedMeanwhile() = runTest {
        val butter = seedStock("butter", stock = 5, unit = "tbsp", spoonacularId = 2)
        repo.detailsResult = Result.success(
            listOf(
                RecipeInformation(
                    id = 10, title = "Toast",
                    extendedIngredients = listOf(
                        ExtendedIngredient(id = 2, name = "butter", amount = 1.0, unit = "tbsp")
                    )
                )
            )
        )
        val vm = startViewModel()
        vm.prepareConsume(10)
        advanceUntilIdle()

        // While the dialog is open the item is re-saved in a different unit.
        dao.updateItem(butter.copy(unit = "g"))

        vm.confirmConsume(10)
        advanceUntilIdle()

        // Untouched: a tbsp deduction can't apply to grams.
        assertEquals(0, dao.transactionsSnapshot().single().amountUsed)
    }

    @Test
    fun adjustConsume_clampsToWhatIsOnHand() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.detailsResult = Result.success(
            listOf(
                RecipeInformation(
                    id = 10, title = "Omelette",
                    extendedIngredients = listOf(
                        ExtendedIngredient(id = 1, name = "egg", amount = 2.0, unit = "piece")
                    )
                )
            )
        )
        val vm = startViewModel()
        vm.prepareConsume(10)
        advanceUntilIdle()

        vm.adjustConsume(10, lineIndex = 0, delta = +5)
        assertEquals(2, vm.cardStates.value[10]?.consume!!.amounts[0])  // clamped to on-hand

        repeat(5) { vm.adjustConsume(10, lineIndex = 0, delta = -1) }
        assertEquals(0, vm.cardStates.value[10]?.consume!!.amounts[0])  // floor at zero
    }
}
