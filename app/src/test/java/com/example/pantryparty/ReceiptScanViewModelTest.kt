package com.example.pantryparty

import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.fakes.FakeOpenRouterRepository
import com.example.pantryparty.fakes.FakePantryDao
import com.example.pantryparty.fakes.FakeSpoonacularRepository
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.viewmodel.ReceiptScanViewModel
import com.example.pantryparty.viewmodel.ScanState
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ReceiptScanViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    // --- helpers --------------------------------------------------------------

    private val today = LocalDate.of(2026, 8, 3)

    private fun ingredient(
        id: Int,
        name: String,
        units: List<String> = listOf("piece"),
        image: String? = null,
        aisle: String? = null
    ) = IngredientAutocomplete(id = id, name = name, image = image, aisle = aisle, possibleUnits = units)

    private fun viewModel(dao: FakePantryDao, repo: FakeSpoonacularRepository, llm: FakeOpenRouterRepository) =
        ReceiptScanViewModel(dao, repo, llm) { today }

    /** JSON for one item as the model is prompted to emit it. */
    private fun llmItem(
        name: String,
        quantity: Int = 1,
        unit: String? = null,
        raw: String = name.uppercase()
    ): String {
        val unitJson = if (unit == null) "null" else "\"$unit\""
        return """{"raw": "$raw", "name": "$name", "quantity": $quantity, "unit": $unitJson}"""
    }

    /** A configured fake model whose reply is a JSON array of the given [llmItem]s. */
    private fun llmAnswering(vararg items: String) = FakeOpenRouterRepository().apply {
        isConfigured = true
        completion = Result.success("[${items.joinToString(",")}]")
    }

    /** Answers only for the listed queries; anything else comes back empty. */
    private fun FakeSpoonacularRepository.answer(vararg pairs: Pair<String, IngredientAutocomplete>) {
        val table = pairs.toMap()
        autocompleteHandler = { query ->
            Result.success(listOfNotNull(table[query]))
        }
    }

    private fun review(state: ScanState): ScanState.Review =
        state as? ScanState.Review ?: error("expected Review but was $state")

    // --- matching -------------------------------------------------------------

    @Test
    fun extractedItems_becomeIncludedRows() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer(
            "whole milk" to ingredient(1, "whole milk", listOf("l", "cup")),
            "bananas" to ingredient(2, "banana", listOf("piece"))
        )
        val llm = llmAnswering(llmItem("whole milk", unit = "l"), llmItem("bananas"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("WHL MILK 2L        5.49", "BANANAS            1.82"))
        advanceUntilIdle()

        val rows = review(vm.state.value).rows
        assertEquals(2, rows.size)
        assertEquals(listOf("whole milk", "banana"), rows.map { it.match?.name })
        assertTrue(rows.all { it.include })
    }

    @Test
    fun exactNameHit_isConfident_butABestGuessIsNot() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer(
            "whole milk" to ingredient(1, "whole milk"),
            // The API answers "cucumber pickle" for "pickles" — usable, but not certain.
            "pickles" to ingredient(9, "cucumber pickle")
        )
        val llm = llmAnswering(llmItem("whole milk"), llmItem("pickles"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("WHL MILK           5.49", "PICKLES            3.99"))
        advanceUntilIdle()

        val rows = review(vm.state.value).rows
        assertTrue("exact name match should be confident", rows[0].confident)
        assertFalse("approximate match should be flagged for review", rows[1].confident)
    }

    @Test
    fun noSuggestions_leavesRowUnmatchedAndExcluded() = runTest {
        val repo = FakeSpoonacularRepository()   // returns empty for everything
        val llm = llmAnswering(llmItem("mystery item"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("MYSTERY ITEM       4.99"))
        advanceUntilIdle()

        val row = review(vm.state.value).rows.single()
        assertNull(row.match)
        assertFalse("an unmatched row must not be added by accident", row.include)
        assertFalse(row.canAdd)
    }

    @Test
    fun modelFindsNoItems_failsWithGuidance() = runTest {
        // "[]" is the model's answer for a receipt with nothing purchasable on it.
        val vm = viewModel(FakePantryDao(), FakeSpoonacularRepository(), llmAnswering())

        vm.onLinesRecognized(listOf("SUBTOTAL   24.51", "TOTAL   27.69", "VISA   27.69"))
        advanceUntilIdle()

        assertTrue(vm.state.value is ScanState.Failed)
    }

    // --- query fallback and caching -------------------------------------------

    @Test
    fun looserQueryIsTried_whenTheFullOneMisses() = runTest {
        val repo = FakeSpoonacularRepository()
        // Only the bare head noun is a known ingredient — the whole shelf label isn't.
        repo.answer("corn" to ingredient(3, "corn"))
        val llm = llmAnswering(llmItem("green giant sweet corn"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("GRN GIANT SWT CRN   2.49"))
        advanceUntilIdle()

        val row = review(vm.state.value).rows.single()
        assertEquals("corn", row.match?.name)
        assertEquals(
            listOf("green giant sweet corn", "sweet corn", "corn"),
            repo.autocompleteQueries
        )
    }

    @Test
    fun duplicateModelItems_collapseIntoOneRowWithTheSummedQuantity() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer("milk" to ingredient(1, "milk"))
        // The prompt asks the model to merge a product's repeated lines itself;
        // this is the seatbelt for replies that list them separately.
        val llm = llmAnswering(llmItem("milk", quantity = 2), llmItem("milk", quantity = 1))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("MILK      4.99", "MILK      4.99", "MILK      4.99"))
        advanceUntilIdle()

        val row = review(vm.state.value).rows.single()
        assertEquals("3", row.quantity)
        assertEquals("the grouped row should be looked up once", 1, repo.autocompleteCalls)
    }

    @Test
    fun lookupFailure_surfacesAsAWarningRatherThanSilence() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.autocompleteHandler = { Result.failure(IOException("offline")) }
        val llm = llmAnswering(llmItem("bananas"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()

        val state = review(vm.state.value)
        assertNotNull("a failed lookup must explain itself", state.warning)
        assertNull(state.rows.single().match)
    }

    // --- review edits ---------------------------------------------------------

    @Test
    fun pickingAMatch_makesTheRowConfidentAndIncluded() = runTest {
        val repo = FakeSpoonacularRepository()
        val llm = llmAnswering(llmItem("mystery item"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("MYSTERY ITEM       4.99"))
        advanceUntilIdle()
        val key = review(vm.state.value).rows.single().key

        vm.selectMatch(key, ingredient(7, "brown rice", listOf("cup", "g")))

        val row = review(vm.state.value).rows.single()
        assertEquals("brown rice", row.match?.name)
        assertTrue(row.confident)
        assertTrue(row.include)
        assertEquals("cup", row.unit)
    }

    @Test
    fun quantityInput_keepsOnlyDigits() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer("bananas" to ingredient(2, "banana"))
        val llm = llmAnswering(llmItem("bananas"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()
        val key = review(vm.state.value).rows.single().key

        vm.setQuantity(key, "3a-2")

        assertEquals("32", review(vm.state.value).rows.single().quantity)
    }

    @Test
    fun modelUnit_isChosenWhenTheIngredientSupportsIt() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer("whole milk" to ingredient(1, "whole milk", listOf("cup", "l", "ml")))
        val llm = llmAnswering(llmItem("whole milk", quantity = 2, unit = "l"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("WHL MILK 2L        5.49"))
        advanceUntilIdle()

        // The "2L" the model read off the receipt beats the API's first-listed "cup".
        val row = review(vm.state.value).rows.single()
        assertEquals("l", row.unit)
        assertEquals("2", row.quantity)
    }

    // --- committing -----------------------------------------------------------

    @Test
    fun confirmAll_createsCatalogRowAndLedgerLot() = runTest {
        val dao = FakePantryDao()
        val repo = FakeSpoonacularRepository()
        repo.answer("bananas" to ingredient(2, "banana", listOf("piece"), image = "banana.jpg", aisle = "Produce"))
        val llm = llmAnswering(llmItem("bananas", quantity = 2))
        val vm = viewModel(dao, repo, llm)

        vm.onLinesRecognized(listOf("2 BANANAS      1.82"))
        advanceUntilIdle()
        vm.confirmAll()
        advanceUntilIdle()

        val item = dao.itemsSnapshot().single()
        assertEquals("banana", item.name)
        assertEquals(2, item.spoonacularId)
        assertEquals("banana.jpg", item.imageUrl)
        assertEquals("Produce", item.aisle)
        // A receipt records what was bought, not what the user wants stocked.
        assertEquals(0, item.desiredAmount)

        val lot = dao.transactionsSnapshot().single()
        assertEquals(item.id, lot.catalogItemId)
        assertEquals(2, lot.amountBought)
        assertEquals(today.toEpochDay(), lot.date)
        assertNull("receipts don't print expiry dates", lot.expiry)

        assertEquals(ScanState.Saved(1), vm.state.value)
    }

    @Test
    fun existingIngredient_gainsALotInsteadOfADuplicateRow() = runTest {
        val dao = FakePantryDao()
        dao.seedItems(
            CatalogItem(
                name = "banana", desiredAmount = 6, spoonacularId = 2,
                imageUrl = null, sortOrder = 0, unit = "piece", aisle = null
            )
        )
        val repo = FakeSpoonacularRepository()
        repo.answer("bananas" to ingredient(2, "banana"))
        val llm = llmAnswering(llmItem("bananas"))
        val vm = viewModel(dao, repo, llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()
        vm.confirmAll()
        advanceUntilIdle()

        // The unique index on spoonacularId makes merging mandatory, not just tidy.
        assertEquals(1, dao.itemsSnapshot().size)
        assertEquals(6, dao.itemsSnapshot().single().desiredAmount)   // target untouched
        assertEquals(1, dao.transactionsSnapshot().size)
    }

    @Test
    fun deselectedAndUnmatchedRows_areSkipped() = runTest {
        val dao = FakePantryDao()
        val repo = FakeSpoonacularRepository()
        repo.answer(
            "bananas" to ingredient(2, "banana"),
            "milk" to ingredient(1, "milk")
        )
        val llm = llmAnswering(llmItem("bananas"), llmItem("milk"), llmItem("mystery"))
        val vm = viewModel(dao, repo, llm)

        vm.onLinesRecognized(listOf("BANANAS   1.82", "MILK   4.99", "MYSTERY   9.99"))
        advanceUntilIdle()

        val bananas = review(vm.state.value).rows.first { it.match?.name == "banana" }
        vm.toggleInclude(bananas.key)   // user drops the bananas
        vm.confirmAll()
        advanceUntilIdle()

        assertEquals(listOf("milk"), dao.itemsSnapshot().map { it.name })
        assertEquals(1, dao.transactionsSnapshot().size)
    }

    @Test
    fun confirmAll_withNothingSelected_doesNotWrite() = runTest {
        val dao = FakePantryDao()
        val llm = llmAnswering(llmItem("mystery item"))
        val vm = viewModel(dao, FakeSpoonacularRepository(), llm)

        vm.onLinesRecognized(listOf("MYSTERY ITEM   9.99"))
        advanceUntilIdle()
        vm.confirmAll()
        advanceUntilIdle()

        assertTrue(dao.itemsSnapshot().isEmpty())
        assertTrue(dao.transactionsSnapshot().isEmpty())
        assertTrue("should stay on the review screen", vm.state.value is ScanState.Review)
    }

    @Test
    fun aRepeatedProduct_becomesOneLotWithTheFullCount() = runTest {
        val dao = FakePantryDao()
        val repo = FakeSpoonacularRepository()
        repo.answer("milk" to ingredient(1, "milk"))
        // The model merged the receipt's two identical lines into quantity 2.
        val llm = llmAnswering(llmItem("milk", quantity = 2))
        val vm = viewModel(dao, repo, llm)

        vm.onLinesRecognized(listOf("MILK   4.99", "MILK   4.99"))
        advanceUntilIdle()
        vm.confirmAll()
        advanceUntilIdle()

        // Both units came off one receipt, so they share a purchase date and expiry —
        // one lot of two, not two lots of one.
        assertEquals(1, dao.itemsSnapshot().size)
        val lot = dao.transactionsSnapshot().single()
        assertEquals(2, lot.amountBought)
    }

    // --- match quality --------------------------------------------------------

    @Test
    fun aSuggestionSharingNoWordWithTheQuery_isRejected() = runTest {
        val repo = FakeSpoonacularRepository()
        // Even a bad extraction (a street address the model failed to skip) must not
        // become a confident-looking row when autocomplete answers with junk.
        repo.answer("n florida ave" to ingredient(9, "agave"))
        val llm = llmAnswering(llmItem("n florida ave"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("8885 N FLORIDA AVE   1.00"))
        advanceUntilIdle()

        val row = review(vm.state.value).rows.single()
        assertNull("a coincidental substring is not a match", row.match)
        assertFalse(row.include)
    }

    @Test
    fun ordinaryPlurals_stillMatch() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer("bananas" to ingredient(2, "banana"))
        val llm = llmAnswering(llmItem("bananas"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("BANANAS   1.82"))
        advanceUntilIdle()

        // The similarity gate must not be so strict that it rejects real matches.
        assertEquals("banana", review(vm.state.value).rows.single().match?.name)
    }

    @Test
    fun withNoPrintedSize_aPackageUnitIsPreferredOverRawWeight() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer("bread" to ingredient(3, "bread", listOf("g", "kg", "piece")))
        val llm = llmAnswering(llmItem("bread"))   // no unit on the receipt line
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("BREAD   2.88"))
        advanceUntilIdle()

        // Was "g" — a loaf of bread measured in grams the user never weighed.
        assertEquals("piece", review(vm.state.value).rows.single().unit)
    }

    // --- the model call -------------------------------------------------------

    @Test
    fun theOcrText_reachesTheModel_andTheRawLineSurvivesToReview() = runTest {
        val repo = FakeSpoonacularRepository()
        repo.answer("whole milk" to ingredient(1, "whole milk", listOf("l")))
        val llm = llmAnswering(llmItem("whole milk", quantity = 2, unit = "l", raw = "WHL MLK 2L 5.49"))
        val vm = viewModel(FakePantryDao(), repo, llm)

        vm.onLinesRecognized(listOf("WHL MLK 2L        5.49"))
        advanceUntilIdle()

        assertEquals(1, llm.completeCalls)
        assertTrue("OCR text must reach the model", llm.lastUser!!.contains("WHL MLK 2L"))
        val row = review(vm.state.value).rows.single()
        assertEquals("WHL MLK 2L 5.49", row.raw)   // printed text still shown in review
        assertTrue(row.confident)
    }

    @Test
    fun llmFailure_failsTheScanWithAFriendlyMessage() = runTest {
        val llm = FakeOpenRouterRepository().apply {
            isConfigured = true
            completion = Result.failure(IOException("offline"))
        }
        val vm = viewModel(FakePantryDao(), FakeSpoonacularRepository(), llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()

        val failed = vm.state.value as ScanState.Failed
        assertTrue("expected a connectivity hint, got: ${failed.message}", failed.message.contains("internet"))
    }

    @Test
    fun aProseReply_failsTheScanInsteadOfInventingItems() = runTest {
        val llm = FakeOpenRouterRepository().apply {
            isConfigured = true
            completion = Result.success("I'm sorry, I can't parse this receipt.")
        }
        val vm = viewModel(FakePantryDao(), FakeSpoonacularRepository(), llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()

        assertTrue(vm.state.value is ScanState.Failed)
    }

    /** Regression: a stalled model must cost at most the timeout, never hang the scan. */
    @Test
    fun aStalledModel_timesOutIntoAFailure() = runTest {
        val llm = FakeOpenRouterRepository().apply {
            isConfigured = true
            hang = true
        }
        val vm = viewModel(FakePantryDao(), FakeSpoonacularRepository(), llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()   // virtual clock runs past the timeout

        val failed = vm.state.value as ScanState.Failed
        assertTrue("expected a timeout message, got: ${failed.message}", failed.message.contains("too long"))
    }

    @Test
    fun withoutAnApiKey_theScanFailsBeforeCallingAnything() = runTest {
        val llm = FakeOpenRouterRepository()   // isConfigured = false
        val vm = viewModel(FakePantryDao(), FakeSpoonacularRepository(), llm)

        vm.onLinesRecognized(listOf("BANANAS      1.82"))
        advanceUntilIdle()

        assertEquals(0, llm.completeCalls)
        val failed = vm.state.value as ScanState.Failed
        assertTrue(
            "the message must say how to enable scanning, got: ${failed.message}",
            failed.message.contains("OPENROUTER_API_KEY")
        )
    }

    // --- fallback query construction -----------------------------------------

    @Test
    fun fallbackQueries_dropLeadingModifiersThenTryTheLeadingToken() {
        assertEquals(
            listOf("green giant sweet corn", "sweet corn", "corn", "green"),
            ReceiptScanViewModel.fallbackQueries("green giant sweet corn")
        )
        assertEquals(listOf("milk"), ReceiptScanViewModel.fallbackQueries("milk"))
        // Two tokens: the pair is the whole query, so it isn't repeated.
        assertEquals(
            listOf("tomato roma", "roma", "tomato"),
            ReceiptScanViewModel.fallbackQueries("tomato roma")
        )
    }
}
