package com.example.pantryparty

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.fakes.FakePantryDao
import com.example.pantryparty.fakes.FakeSpoonacularRepository
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.RecipeIngredientBrief
import com.example.pantryparty.viewmodel.RecipeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        vm.refreshFromPantry()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertEquals(listOf(3, 1), ui.recipes.map { it.id })  // ready first, missed=4 dropped
        assertTrue(ui.hasSearched)
        assertFalse(ui.loading)
        assertNull(ui.error)
        assertEquals(listOf("egg"), repo.lastRecipeQuery)
    }

    @Test
    fun quotaFailure_showsTheFriendlyMessage() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.recipesResult = Result.failure(
            HttpException(Response.error<Any>(402, "".toResponseBody()))
        )
        val vm = startViewModel()

        vm.refreshFromPantry()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertTrue("expected quota hint, got: ${ui.error}", ui.error!!.contains("quota"))
        assertFalse(ui.loading)
    }

    /** Regression: switching modes must cancel an in-flight search. */
    @Test
    fun switchingMode_cancelsAnInFlightSearch() = runTest {
        seedStock("egg", stock = 2, unit = "piece", spoonacularId = 1)
        repo.hangRecipes = true
        val vm = startViewModel()

        vm.refreshFromPantry()
        assertTrue(vm.uiState.value.loading)   // request is in flight (hanging)

        vm.showPickIngredients()
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertFalse(ui.loading)                // no stuck spinner
        assertFalse(ui.hasSearched)
        assertTrue(ui.recipes.isEmpty())
        assertNull(ui.error)                   // cancellation is not an error
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
