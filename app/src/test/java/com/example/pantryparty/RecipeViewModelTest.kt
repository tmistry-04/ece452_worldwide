package com.example.pantryparty

import com.example.pantryparty.data.PantryItem
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

    private fun brief(id: Int) =
        RecipeIngredientBrief(id = id, name = "i$id", amount = 1.0, unit = "g")

    private fun searchResult(id: Int, missed: Int) = RecipeByIngredient(
        id = id, title = "R$id",
        missedIngredients = List(missed) { brief(id * 100 + it) }
    )

    // --- searching -----------------------------------------------------------

    @Test
    fun search_bucketsSortsAndDropsBeyondMaxMissing() = runTest {
        dao.seed(PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 1))
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
        dao.seed(PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 1))
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
        dao.seed(PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 1))
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
        dao.seed(PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 1))
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
    fun confirmConsume_deductsAndRemovesEmptiedRows() = runTest {
        dao.seed(
            PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 1),
            PantryItem(name = "butter", quantity = 5, unit = "tbsp", spoonacularId = 2)
        )
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

        val remaining = dao.snapshot()
        assertEquals(listOf("butter"), remaining.map { it.name })  // egg used up -> deleted
        assertEquals(4, remaining.single().quantity)
        assertNull(vm.cardStates.value[10]?.consume)               // dialog closed
    }

    /** Regression: a unit change while the dialog is open must not be deducted against. */
    @Test
    fun confirmConsume_skipsRowsWhoseUnitChangedMeanwhile() = runTest {
        dao.seed(PantryItem(name = "butter", quantity = 5, unit = "tbsp", spoonacularId = 2))
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

        // While the dialog is open the row is re-saved in a different unit.
        dao.upsert(dao.snapshot().single().copy(quantity = 500, unit = "g"))

        vm.confirmConsume(10)
        advanceUntilIdle()

        val row = dao.snapshot().single()
        assertEquals(500, row.quantity)   // untouched: a tbsp deduction can't apply to grams
        assertEquals("g", row.unit)
    }

    @Test
    fun adjustConsume_clampsToWhatIsOnHand() = runTest {
        dao.seed(PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 1))
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
