package com.example.pantryparty

import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.fakes.FakePantryDao
import com.example.pantryparty.fakes.FakeSpoonacularRepository
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.viewmodel.PantryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val dao = FakePantryDao()
    private val repo = FakeSpoonacularRepository()

    private val apple = IngredientAutocomplete(
        id = 1, name = "apple", possibleUnits = listOf("piece", "kg")
    )

    private fun viewModel() = PantryViewModel(dao, repo)

    // Just past the debounce window, so the lookup actually fires.
    private val pastDebounce = PantryViewModel.DEBOUNCE_MS + 1

    // --- add flow ------------------------------------------------------------

    @Test
    fun saveNewIngredient_insertsRowWithChosenUnitAndAmount() = runTest {
        val vm = viewModel()
        vm.selectSuggestion(apple)
        vm.onAmountChange("3")
        vm.save()
        advanceUntilIdle()

        val row = dao.snapshot().single()
        assertEquals(1, row.spoonacularId)
        assertEquals(3, row.quantity)
        assertEquals("piece", row.unit)   // first possibleUnit is auto-selected
        // Flow resets to the search step after a save.
        assertNull(vm.addState.value.selected)
    }

    @Test
    fun saveSameUnit_mergesIntoExistingRow() = runTest {
        dao.seed(PantryItem(name = "apple", quantity = 2, unit = "piece", spoonacularId = 1))
        val vm = viewModel()
        vm.selectSuggestion(apple)
        vm.onAmountChange("3")
        vm.save()
        advanceUntilIdle()

        val row = dao.snapshot().single()
        assertEquals(5, row.quantity)
        assertEquals("piece", row.unit)
    }

    @Test
    fun saveDifferentUnit_adoptsNewUnitAndAmount() = runTest {
        dao.seed(PantryItem(name = "apple", quantity = 2, unit = "piece", spoonacularId = 1))
        val vm = viewModel()
        vm.selectSuggestion(apple)
        vm.selectUnit("kg")
        vm.onAmountChange("4")
        vm.save()
        advanceUntilIdle()

        val row = dao.snapshot().single()
        assertEquals(4, row.quantity)
        assertEquals("kg", row.unit)
    }

    // --- autocomplete --------------------------------------------------------

    @Test
    fun autocomplete_waitsForTheDebounceWindow() = runTest {
        repo.autocompleteResult = Result.success(listOf(apple))
        val vm = viewModel()

        vm.onQueryChange("ap")
        assertEquals(0, repo.autocompleteCalls)

        advanceTimeBy(pastDebounce)
        assertEquals(1, repo.autocompleteCalls)
        assertEquals(listOf(apple), vm.addState.value.suggestions)
    }

    @Test
    fun retypingWithinTheDebounceWindow_makesOnlyOneCall() = runTest {
        val vm = viewModel()
        vm.onQueryChange("ap")
        advanceTimeBy(PantryViewModel.DEBOUNCE_MS / 2)
        vm.onQueryChange("app")
        advanceTimeBy(pastDebounce)

        assertEquals(1, repo.autocompleteCalls)
    }

    /** Regression: a cancelled in-flight lookup must not surface as an error. */
    @Test
    fun cancellingAnInFlightLookup_leavesNoErrorOrSpinner() = runTest {
        repo.hangAutocomplete = true
        val vm = viewModel()

        vm.onQueryChange("appl")
        advanceTimeBy(pastDebounce)          // request is now in flight (hanging)
        assertEquals(1, repo.autocompleteCalls)

        vm.onQueryChange("a")                // short query cancels the lookup
        advanceUntilIdle()

        with(vm.addState.value) {
            assertNull(error)
            assertFalse(loading)
            assertTrue(suggestions.isEmpty())
        }
    }

    // --- steppers ------------------------------------------------------------

    @Test
    fun decrementAtQuantityOne_deletesTheRow() = runTest {
        dao.seed(PantryItem(name = "egg", quantity = 1, unit = "piece", spoonacularId = 7))
        val vm = viewModel()
        vm.decrement(dao.snapshot().single())
        advanceUntilIdle()

        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun incrementAndDecrement_adjustQuantityInPlace() = runTest {
        dao.seed(PantryItem(name = "egg", quantity = 2, unit = "piece", spoonacularId = 7))
        val vm = viewModel()

        vm.increment(dao.snapshot().single())
        advanceUntilIdle()
        assertEquals(3, dao.snapshot().single().quantity)

        vm.decrement(dao.snapshot().single())
        advanceUntilIdle()
        assertEquals(2, dao.snapshot().single().quantity)
    }
}
