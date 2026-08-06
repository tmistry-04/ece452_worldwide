package com.example.pantryparty

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.fakes.FakePantryDao
import com.example.pantryparty.fakes.FakeSpoonacularRepository
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.viewmodel.PantryViewModel
import com.example.pantryparty.viewmodel.UseKind
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    /** Pinned clock so "today", expiry buckets, and defaults are deterministic. */
    private val today: LocalDate = LocalDate.of(2026, 8, 2)
    private val todayEpoch = today.toEpochDay()

    private val apple = IngredientAutocomplete(
        id = 1, name = "apple", image = "apple.jpg", aisle = "Produce",
        possibleUnits = listOf("piece", "kg")
    )

    // Just past the debounce window, so the lookup actually fires.
    private val pastDebounce = PantryViewModel.DEBOUNCE_MS + 1

    /**
     * Creates the ViewModel and subscribes its hot flows (they are
     * WhileSubscribed, so without collectors their .value would stay stale).
     */
    private fun TestScope.startViewModel(): PantryViewModel {
        val vm = PantryViewModel(dao, repo) { today }
        backgroundScope.launch { vm.rows.collect {} }
        backgroundScope.launch { vm.detail.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun item(
        name: String = "apple",
        spoonacularId: Int = 1,
        desired: Int = 5,
        unit: String = "piece",
        sortOrder: Int = 0
    ) = CatalogItem(
        name = name, desiredAmount = desired, spoonacularId = spoonacularId,
        sortOrder = sortOrder, unit = unit
    )

    private fun txn(
        itemId: Long,
        date: Long = todayEpoch - 10,
        bought: Int,
        used: Int = 0,
        thrown: Int = 0,
        expiry: Long? = null
    ) = PantryTransaction(
        catalogItemId = itemId, date = date,
        amountBought = bought, amountUsed = used, amountThrown = thrown, expiry = expiry
    )

    // --- derived rows --------------------------------------------------------

    @Test
    fun rows_deriveStockScoreAndExpiryFromTheLedger() = runTest {
        val stored = dao.seedItems(item()).single()
        dao.seedTransactions(
            // 3 left, expiring in 2 days; 2 used + 1 tossed -> score 67.
            txn(stored.id, bought = 6, used = 2, thrown = 1, expiry = todayEpoch + 2),
            // 2 left, already expired.
            txn(stored.id, date = todayEpoch - 20, bought = 2, expiry = todayEpoch - 1)
        )
        val vm = startViewModel()

        val row = vm.rows.value.single()
        assertEquals(5, row.stock)
        assertEquals(67, row.score)
        assertEquals(2, row.expiry.expiredCount)
        assertEquals(3, row.expiry.expiringSoonCount)
        assertEquals(2L, row.expiry.daysToNext)
    }

    @Test
    fun rows_withNoHistory_haveZeroStockAndNoScore() = runTest {
        dao.seedItems(item())
        val vm = startViewModel()

        val row = vm.rows.value.single()
        assertEquals(0, row.stock)
        assertNull(row.score)
        assertFalse(row.expiry.hasWarning)
    }

    // --- rearranging ---------------------------------------------------------

    @Test
    fun moveDown_swapsWithTheNextItem_andPersistsPositions() = runTest {
        dao.seedItems(
            item(name = "apple", spoonacularId = 1, sortOrder = 0),
            item(name = "bread", spoonacularId = 2, sortOrder = 1),
            item(name = "milk", spoonacularId = 3, sortOrder = 2)
        )
        val vm = startViewModel()

        vm.moveDown(vm.rows.value.first().item)   // apple below bread
        advanceUntilIdle()

        assertEquals(listOf("bread", "apple", "milk"), vm.rows.value.map { it.item.name })
        // Positions were rewritten 0..n-1, not just swapped in memory.
        assertEquals(listOf(0, 1, 2), dao.itemsSnapshot().map { it.sortOrder })
    }

    @Test
    fun moveUp_onTheFirstItem_isANoOp() = runTest {
        dao.seedItems(
            item(name = "apple", spoonacularId = 1, sortOrder = 0),
            item(name = "bread", spoonacularId = 2, sortOrder = 1)
        )
        val vm = startViewModel()

        vm.moveUp(vm.rows.value.first().item)
        advanceUntilIdle()

        assertEquals(listOf("apple", "bread"), vm.rows.value.map { it.item.name })
    }

    // --- buying (swipe right) ------------------------------------------------

    @Test
    fun buyDialog_defaultsToOneUnitTodayNoExpiry() = runTest {
        val stored = dao.seedItems(item()).single()
        val vm = startViewModel()

        vm.openBuy(stored)

        with(vm.buyDraft.value!!) {
            assertEquals("1", amount)
            assertEquals(today, dateBought)
            assertNull(expiry)
        }
    }

    @Test
    fun confirmBuy_recordsANewLot() = runTest {
        val stored = dao.seedItems(item()).single()
        val vm = startViewModel()

        vm.openBuy(stored)
        vm.setBuyAmount("3")
        vm.setBuyExpiry(today.plusDays(7))
        vm.confirmBuy()
        advanceUntilIdle()

        val lot = dao.transactionsSnapshot().single()
        assertEquals(stored.id, lot.catalogItemId)
        assertEquals(3, lot.amountBought)
        assertEquals(todayEpoch, lot.date)
        assertEquals(todayEpoch + 7, lot.expiry)
        assertEquals(0, lot.amountUsed)
        assertEquals(0, lot.amountThrown)
        assertNull(vm.buyDraft.value)   // dialog closed
    }

    @Test
    fun confirmBuy_withExpiryBeforePurchase_isBlocked() = runTest {
        val stored = dao.seedItems(item()).single()
        val vm = startViewModel()

        vm.openBuy(stored)
        vm.setBuyExpiry(today.minusDays(1))
        assertFalse(vm.buyDraft.value!!.canConfirm)

        vm.confirmBuy()
        advanceUntilIdle()

        assertTrue(dao.transactionsSnapshot().isEmpty())
        assertNotNull(vm.buyDraft.value)   // dialog stays open, showing the error
    }

    // --- using / tossing (swipe left) ---------------------------------------

    @Test
    fun useDialog_defaultsToTheOldestOpenLot() = runTest {
        val stored = dao.seedItems(item()).single()
        val (older, newer) = dao.seedTransactions(
            txn(stored.id, date = todayEpoch - 5, bought = 2),
            txn(stored.id, date = todayEpoch - 1, bought = 4)
        )
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()

        with(vm.useDraft.value!!) {
            assertEquals(listOf(older.id, newer.id), lots.map { it.id })
            assertEquals(older.id, selectedLotId)
            assertEquals(UseKind.USED, kind)
        }
    }

    @Test
    fun confirmUse_booksTheAmountAgainstTheChosenLot() = runTest {
        val stored = dao.seedItems(item()).single()
        val (older, newer) = dao.seedTransactions(
            txn(stored.id, date = todayEpoch - 5, bought = 2),
            txn(stored.id, date = todayEpoch - 1, bought = 4)
        )
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()
        vm.selectUseLot(newer.id)
        vm.setUseAmount("3")
        vm.confirmUse()
        advanceUntilIdle()

        val lots = dao.transactionsSnapshot().associateBy { it.id }
        assertEquals(3, lots.getValue(newer.id).amountUsed)
        assertEquals(0, lots.getValue(older.id).amountUsed)
        assertNull(vm.useDraft.value)   // dialog closed
    }

    @Test
    fun confirmUse_asTossed_recordsThrownAwayInstead() = runTest {
        val stored = dao.seedItems(item()).single()
        val lot = dao.seedTransactions(txn(stored.id, bought = 4)).single()
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()
        vm.setUseKind(UseKind.TOSSED)
        vm.setUseAmount("2")
        vm.confirmUse()
        advanceUntilIdle()

        val updated = dao.transactionsSnapshot().single { it.id == lot.id }
        assertEquals(2, updated.amountThrown)
        assertEquals(0, updated.amountUsed)
    }

    @Test
    fun confirmUse_beyondTheLotsRemainder_isBlocked() = runTest {
        val stored = dao.seedItems(item()).single()
        dao.seedTransactions(txn(stored.id, bought = 4, used = 2))   // 2 left
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()
        vm.setUseAmount("3")
        assertFalse(vm.useDraft.value!!.canConfirm)

        vm.confirmUse()
        advanceUntilIdle()

        assertEquals(2, dao.transactionsSnapshot().single().amountUsed)   // unchanged
        assertNotNull(vm.useDraft.value)   // dialog stays open
    }

    @Test
    fun usingTheLastUnit_ofAnItemWithNoDesiredAmount_removesTheItem() = runTest {
        // Receipt-added items land with desired 0: nothing says to restock them,
        // so using up the last unit retires the item instead of leaving clutter.
        val stored = dao.seedItems(item(desired = 0)).single()
        dao.seedTransactions(txn(stored.id, bought = 2, used = 1))   // 1 left
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()
        vm.setUseAmount("1")
        vm.confirmUse()
        advanceUntilIdle()

        assertTrue(dao.itemsSnapshot().isEmpty())
        assertTrue("history goes with the item", dao.transactionsSnapshot().isEmpty())
    }

    @Test
    fun usingTheLastUnit_ofAnItemWithATarget_keepsItAsARestockReminder() = runTest {
        val stored = dao.seedItems(item(desired = 5)).single()
        dao.seedTransactions(txn(stored.id, bought = 1)).single()
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()
        vm.setUseAmount("1")
        vm.confirmUse()
        advanceUntilIdle()

        assertEquals(1, dao.itemsSnapshot().size)
        assertEquals(0, vm.rows.value.single().stock)
    }

    @Test
    fun usingSomeButNotAll_ofAnItemWithNoDesiredAmount_keepsIt() = runTest {
        val stored = dao.seedItems(item(desired = 0)).single()
        dao.seedTransactions(txn(stored.id, bought = 3))
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()
        vm.setUseAmount("2")
        vm.confirmUse()
        advanceUntilIdle()

        assertEquals(1, dao.itemsSnapshot().size)
        assertEquals(1, vm.rows.value.single().stock)
    }

    @Test
    fun useDialog_withNothingInStock_hasNoLotsToPick() = runTest {
        val stored = dao.seedItems(item()).single()
        dao.seedTransactions(txn(stored.id, bought = 2, used = 2))   // all consumed
        val vm = startViewModel()

        vm.openUse(stored)
        advanceUntilIdle()

        with(vm.useDraft.value!!) {
            assertTrue(lots.isEmpty())
            assertFalse(canConfirm)
        }
    }

    // --- details modal + transaction edits -----------------------------------

    @Test
    fun detail_showsTheItemsHistoryNewestFirst() = runTest {
        val stored = dao.seedItems(item()).single()
        val other = dao.seedItems(item(name = "bread", spoonacularId = 2, sortOrder = 1)).single()
        val (old, new) = dao.seedTransactions(
            txn(stored.id, date = todayEpoch - 9, bought = 1),
            txn(stored.id, date = todayEpoch - 1, bought = 2)
        )
        dao.seedTransactions(txn(other.id, bought = 7))
        val vm = startViewModel()

        vm.openDetail(stored)
        advanceUntilIdle()

        val detail = vm.detail.value!!
        assertEquals(stored.id, detail.row.item.id)
        assertEquals(listOf(new.id, old.id), detail.transactions.map { it.id })   // newest first, other item's rows excluded
    }

    @Test
    fun editingATransaction_appliesALegalEdit() = runTest {
        val stored = dao.seedItems(item()).single()
        val lot = dao.seedTransactions(txn(stored.id, bought = 6)).single()
        val vm = startViewModel()

        vm.openTransactionEditor(lot)
        vm.setTxnBought("4")
        vm.setTxnUsed("2")
        vm.setTxnThrown("1")
        vm.setTxnExpiry(today.plusDays(3))
        vm.confirmTransactionEdit()
        advanceUntilIdle()

        val updated = dao.transactionsSnapshot().single()
        assertEquals(4, updated.amountBought)
        assertEquals(2, updated.amountUsed)
        assertEquals(1, updated.amountThrown)
        assertEquals(todayEpoch + 3, updated.expiry)
        assertNull(vm.txnDraft.value)   // editor closed
    }

    @Test
    fun editingATransaction_rejectsOverconsumption() = runTest {
        val stored = dao.seedItems(item()).single()
        val lot = dao.seedTransactions(txn(stored.id, bought = 2)).single()
        val vm = startViewModel()

        vm.openTransactionEditor(lot)
        vm.setTxnUsed("2")
        vm.setTxnThrown("1")   // 3 consumed out of 2 bought
        assertNotNull(vm.txnDraft.value!!.error)

        vm.confirmTransactionEdit()
        advanceUntilIdle()

        assertEquals(0, dao.transactionsSnapshot().single().amountUsed)   // unchanged
        assertNotNull(vm.txnDraft.value)   // editor stays open
    }

    @Test
    fun deleteTransaction_removesJustThatRow() = runTest {
        val stored = dao.seedItems(item()).single()
        val (a, b) = dao.seedTransactions(
            txn(stored.id, bought = 1),
            txn(stored.id, date = todayEpoch - 1, bought = 2)
        )
        val vm = startViewModel()

        vm.deleteTransaction(a)
        advanceUntilIdle()

        assertEquals(listOf(b.id), dao.transactionsSnapshot().map { it.id })
    }

    @Test
    fun clearHistory_onlyTouchesThatItem() = runTest {
        val stored = dao.seedItems(item()).single()
        val other = dao.seedItems(item(name = "bread", spoonacularId = 2, sortOrder = 1)).single()
        dao.seedTransactions(
            txn(stored.id, bought = 3),
            txn(stored.id, date = todayEpoch - 2, bought = 1),
            txn(other.id, bought = 7)
        )
        val vm = startViewModel()

        vm.clearHistory(stored)
        advanceUntilIdle()

        assertEquals(listOf(other.id), dao.transactionsSnapshot().map { it.catalogItemId })
    }

    @Test
    fun deleteItem_cascadesToItsHistory_andClosesItsDetail() = runTest {
        val stored = dao.seedItems(item()).single()
        dao.seedTransactions(txn(stored.id, bought = 3))
        val vm = startViewModel()

        vm.openDetail(stored)
        advanceUntilIdle()
        assertNotNull(vm.detail.value)

        vm.deleteItem(stored)
        advanceUntilIdle()

        assertTrue(dao.itemsSnapshot().isEmpty())
        assertTrue(dao.transactionsSnapshot().isEmpty())
        assertNull(vm.detail.value)
    }

    // --- item editor: add ----------------------------------------------------

    @Test
    fun autocomplete_waitsForTheDebounceWindow() = runTest {
        repo.autocompleteResult = Result.success(listOf(apple))
        val vm = startViewModel()

        vm.openAddItem()
        vm.onQueryChange("ap")
        assertEquals(0, repo.autocompleteCalls)

        advanceTimeBy(pastDebounce)
        assertEquals(1, repo.autocompleteCalls)
        assertEquals(listOf(apple), vm.itemEditor.value!!.suggestions)
    }

    @Test
    fun retypingWithinTheDebounceWindow_makesOnlyOneCall() = runTest {
        val vm = startViewModel()
        vm.openAddItem()
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
        val vm = startViewModel()

        vm.openAddItem()
        vm.onQueryChange("appl")
        advanceTimeBy(pastDebounce)          // request is now in flight (hanging)
        assertEquals(1, repo.autocompleteCalls)

        vm.onQueryChange("a")                // short query cancels the lookup
        advanceUntilIdle()

        with(vm.itemEditor.value!!) {
            assertNull(error)
            assertFalse(loading)
            assertTrue(suggestions.isEmpty())
        }
    }

    @Test
    fun savingANewItem_storesTheCatalogDetails() = runTest {
        val vm = startViewModel()

        vm.openAddItem()
        vm.selectSuggestion(apple)
        vm.onDesiredAmountChange("4")
        vm.saveItem()
        advanceUntilIdle()

        val row = dao.itemsSnapshot().single()
        assertEquals("apple", row.name)
        assertEquals(4, row.desiredAmount)
        assertEquals("piece", row.unit)          // first possibleUnit auto-selected
        assertEquals(1, row.spoonacularId)
        assertEquals("apple.jpg", row.imageUrl)
        assertEquals("Produce", row.aisle)
        assertEquals(0, row.sortOrder)
        assertNull(vm.itemEditor.value)          // editor closed
    }

    @Test
    fun savingANewItem_appendsToTheEndOfTheList() = runTest {
        dao.seedItems(item(name = "bread", spoonacularId = 2, sortOrder = 0))
        val vm = startViewModel()

        vm.openAddItem()
        vm.selectSuggestion(apple)
        vm.saveItem()
        advanceUntilIdle()

        assertEquals(listOf("bread", "apple"), vm.rows.value.map { it.item.name })
    }

    @Test
    fun savingAnAlreadyTrackedIngredient_updatesInsteadOfDuplicating() = runTest {
        val stored = dao.seedItems(item(desired = 2)).single()
        val vm = startViewModel()

        vm.openAddItem()
        vm.selectSuggestion(apple)       // same spoonacular id as the stored row
        vm.onDesiredAmountChange("7")
        vm.selectUnit("kg")
        vm.saveItem()
        advanceUntilIdle()

        val row = dao.itemsSnapshot().single()
        assertEquals(stored.id, row.id)
        assertEquals(7, row.desiredAmount)
        assertEquals("kg", row.unit)
    }

    @Test
    fun customUnit_isSavedTrimmed() = runTest {
        val vm = startViewModel()

        vm.openAddItem()
        vm.selectSuggestion(apple)
        vm.selectCustomUnit()
        vm.onCustomUnitChange("  jar ")
        vm.saveItem()
        advanceUntilIdle()

        assertEquals("jar", dao.itemsSnapshot().single().unit)
    }

    @Test
    fun blankDesiredAmountOrBlankCustomUnit_cannotSave() = runTest {
        val vm = startViewModel()
        vm.openAddItem()
        vm.selectSuggestion(apple)

        vm.onDesiredAmountChange("")
        assertFalse(vm.itemEditor.value!!.canSave)
        vm.onDesiredAmountChange("2")

        vm.selectCustomUnit()            // custom picked but not typed
        assertFalse(vm.itemEditor.value!!.canSave)

        vm.saveItem()
        advanceUntilIdle()
        assertTrue(dao.itemsSnapshot().isEmpty())
    }

    @Test
    fun zeroDesiredAmount_isAValidTargetMeaningDontRestock() = runTest {
        // 0 is what receipt-added items start with; it must also be settable by
        // hand so any item can opt into the used-up cleanup (deleteIfDepleted).
        val vm = startViewModel()
        vm.openAddItem()
        vm.selectSuggestion(apple)
        vm.onDesiredAmountChange("0")
        vm.saveItem()
        advanceUntilIdle()

        assertEquals(0, dao.itemsSnapshot().single().desiredAmount)
    }

    // --- item editor: modify -------------------------------------------------

    @Test
    fun editingAnItem_updatesItsDetailsInPlace() = runTest {
        val stored = dao.seedItems(item()).single()
        val vm = startViewModel()

        vm.openEditItem(stored)
        vm.onNameChange("granny smith")
        vm.onDesiredAmountChange("9")
        vm.selectUnit("kg")              // from the common-unit chips
        vm.saveItem()
        advanceUntilIdle()

        val row = dao.itemsSnapshot().single()
        assertEquals(stored.id, row.id)
        assertEquals("granny smith", row.name)
        assertEquals(9, row.desiredAmount)
        assertEquals("kg", row.unit)
        assertEquals(stored.spoonacularId, row.spoonacularId)   // identity untouched
    }

    @Test
    fun editorForAnExistingItem_offersItsCurrentUnitFirst() = runTest {
        val stored = dao.seedItems(item(unit = "carton")).single()
        val vm = startViewModel()

        vm.openEditItem(stored)

        with(vm.itemEditor.value!!) {
            assertEquals("carton", unitOptions.first())   // custom units stay pickable
            assertEquals("carton", unit)
            assertEquals(stored.name, name)
            assertEquals(stored.desiredAmount.toString(), desiredAmount)
        }
    }

    @Test
    fun leavingEditMode_discardsAHalfFinishedEditor() = runTest {
        val vm = startViewModel()
        vm.toggleEditMode()              // enter
        vm.openAddItem()
        vm.onQueryChange("ap")

        vm.toggleEditMode()              // leave

        assertNull(vm.itemEditor.value)
        assertFalse(vm.editMode.value)
    }
}
