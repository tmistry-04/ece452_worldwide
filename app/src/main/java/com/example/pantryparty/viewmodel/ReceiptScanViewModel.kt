package com.example.pantryparty.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.OpenRouterRepository
import com.example.pantryparty.network.OpenRouterRepositoryImpl
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.network.SpoonacularRepositoryImpl
import com.example.pantryparty.network.friendlyApiError
import com.example.pantryparty.network.friendlyLlmError
import com.example.pantryparty.receipt.LlmReceiptExtractor
import com.example.pantryparty.receipt.ReceiptLine
import com.example.pantryparty.recipe.normalizeIngredientName
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the scan flow is: framing a shot, working, reviewing, done, or stuck. */
sealed interface ScanState {
    data object Camera : ScanState
    data object Processing : ScanState

    /**
     * The editable result. [warning] carries a lookup failure (usually "no internet")
     * so an offline scan explains itself instead of silently matching nothing.
     */
    data class Review(val rows: List<ScanRowUi>, val warning: String? = null) : ScanState
    data class Saved(val count: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * One reviewable receipt line.
 *
 * [raw] is always shown next to the guess: receipt shorthand is ambiguous enough that
 * the user needs to see what was printed to judge whether [match] is right.
 */
data class ScanRowUi(
    val key: Int,
    val raw: String,
    val query: String,
    val match: IngredientAutocomplete?,   // null = nothing found; needs a manual pick
    val confident: Boolean,               // the ingredient name matched the line exactly
    val quantity: String,
    val unit: String,
    val unitOptions: List<String>,
    val include: Boolean,
    // Inline correction search, mirroring ItemEditorState's fields.
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val suggestions: List<IngredientAutocomplete> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val amount: Int get() = quantity.toIntOrNull() ?: 0
    val canAdd: Boolean get() = match != null && amount > 0
}

/**
 * Owns the receipt scan: OCR text in, reviewed pantry purchases out.
 *
 * Separate from [PantryViewModel] because the flow is self-contained and short-lived —
 * it exists only while the scan dialog is open, and its state dies with it.
 *
 * The ViewModel never touches ML Kit or the camera. It takes plain recognized text via
 * [onLinesRecognized], which keeps the whole matching pipeline unit-testable with no
 * device and no vision library. [today] is injectable so tests can pin the clock.
 */
class ReceiptScanViewModel(
    private val dao: PantryDao,
    private val repository: SpoonacularRepository = SpoonacularRepositoryImpl,
    private val llm: OpenRouterRepository = OpenRouterRepositoryImpl,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Camera)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * Query results for this scan only. A receipt repeats tokens constantly ("organic"
     * this, "organic" that) and every fallback attempt is another API call, so without
     * this a 20-line receipt could spend most of its 5 s budget re-asking the same
     * questions. Safe as a plain map: everything below runs on the main dispatcher.
     */
    private val lookupCache = mutableMapOf<String, List<IngredientAutocomplete>>()
    private var lookupFailure: String? = null

    /** In-flight correction lookup; cancelled on each keystroke for debouncing. */
    private var searchJob: Job? = null

    // ---- capture ------------------------------------------------------------

    fun onCaptureStarted() { _state.value = ScanState.Processing }

    fun onCaptureFailed(message: String) { _state.value = ScanState.Failed(message) }

    /** Back to the viewfinder after a failed or unusable scan. */
    fun retry() {
        lookupFailure = null
        _state.value = ScanState.Camera
    }

    /**
     * Turns recognized text into reviewable rows: the model parses the receipt, then
     * each candidate is resolved against Spoonacular. Nothing is written to the pantry
     * here — the user confirms first, because the model's guesses can be wrong.
     *
     * The LLM is the only parser, so scanning requires OpenRouter to be configured
     * and reachable; every miss (no key, timeout, request error, unusable reply)
     * fails the scan with a message that says which of those it was.
     */
    fun onLinesRecognized(lines: List<String>) {
        _state.value = ScanState.Processing
        viewModelScope.launch {
            lookupFailure = null
            if (!llm.isConfigured) {
                _state.value = ScanState.Failed(
                    "Receipt scanning needs an OpenRouter API key — set OPENROUTER_API_KEY in local.properties."
                )
                return@launch
            }
            val reply = withTimeoutOrNull(LLM_TIMEOUT_MS) {
                llm.complete(
                    system = LlmReceiptExtractor.SYSTEM_PROMPT,
                    user = LlmReceiptExtractor.userPrompt(lines)
                )
            }
            if (reply == null) {
                _state.value = ScanState.Failed("Reading the receipt took too long — try again.")
                return@launch
            }
            val content = reply.getOrElse { e ->
                _state.value = ScanState.Failed(friendlyLlmError(e))
                return@launch
            }
            // An unusable reply and a genuinely item-free receipt get the same
            // guidance: both look like "nothing scannable here" to the user.
            val candidates = LlmReceiptExtractor.parseResponse(content).orEmpty()
            if (candidates.isEmpty()) {
                _state.value = ScanState.Failed(
                    "No grocery items found on that receipt. Try again with more light, " +
                        "holding the phone steady and level."
                )
                return@launch
            }
            val rows = resolveAll(groupDuplicates(candidates))
            _state.value = ScanState.Review(rows = rows, warning = lookupFailure)
        }
    }

    /**
     * Merges rows that resolved to the same cleaned name.
     *
     * The prompt already asks the model to merge a product's repeated lines (receipts
     * print one line per unit bought); this is the seatbelt for replies that list them
     * separately, so the user never confirms one purchase twice and the same name is
     * never looked up twice.
     */
    private fun groupDuplicates(lines: List<ReceiptLine>): List<ReceiptLine> =
        lines.groupBy { it.query }
            .map { (_, group) -> group.first().copy(quantity = group.sumOf { it.quantity }) }

    /**
     * Resolves candidates in small parallel batches.
     *
     * Serially this would be one round-trip per line and blow NFR 3.2's 5 s budget on a
     * long receipt; all at once it would fire twenty-odd simultaneous requests at a
     * shared free-tier key. Batching splits the difference.
     */
    private suspend fun resolveAll(candidates: List<ReceiptLine>): List<ScanRowUi> = coroutineScope {
        candidates.withIndex()
            .chunked(LOOKUP_CONCURRENCY)
            .flatMap { batch ->
                batch.map { (index, line) -> async { toRow(index, line) } }.awaitAll()
            }
    }

    private suspend fun toRow(key: Int, line: ReceiptLine): ScanRowUi {
        val match = bestMatch(line.query, lookup(line.query))
        val units = match?.possibleUnits.orEmpty().ifEmpty { listOf(PantryViewModel.DEFAULT_UNIT) }
        return ScanRowUi(
            key = key,
            raw = line.raw,
            query = line.query,
            match = match,
            confident = match != null &&
                normalizeIngredientName(match.name) == normalizeIngredientName(line.query),
            quantity = line.quantity.toString(),
            // Honour the size printed on the receipt when the ingredient supports it,
            // otherwise prefer a package-style unit. A receipt row is a thing the user
            // carried home, not a measured amount — falling straight through to
            // possibleUnits.first() lands on a raw weight for most foods and shows
            // "1 g" of bread.
            unit = line.unitHint?.takeIf { it in units }
                ?: units.firstOrNull { it in PACKAGE_LIKE_UNITS }
                ?: units.first(),
            unitOptions = units,
            include = match != null
        )
    }

    /**
     * Prefers an exact ingredient-name hit, else the API's top suggestion — but only if
     * it actually resembles what was asked for.
     *
     * Autocomplete always answers with *something*, so an unfiltered `firstOrNull()`
     * turns any junk query into a confident-looking card: a street address ending in
     * "AVE" comes back as agave. Requiring a shared token means a bad query lands as
     * "No match found" and stays unchecked in review, which is the honest outcome.
     */
    private fun bestMatch(
        query: String,
        suggestions: List<IngredientAutocomplete>
    ): IngredientAutocomplete? {
        val normalized = normalizeIngredientName(query)
        return suggestions.firstOrNull { normalizeIngredientName(it.name) == normalized }
            ?: suggestions.firstOrNull { sharesToken(normalized, normalizeIngredientName(it.name)) }
    }

    /** Tries the full query, then progressively looser ones, stopping at the first hit. */
    private suspend fun lookup(query: String): List<IngredientAutocomplete> {
        for (attempt in fallbackQueries(query)) {
            val hits = cachedAutocomplete(attempt)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    private suspend fun cachedAutocomplete(query: String): List<IngredientAutocomplete> {
        lookupCache[query]?.let { return it }
        val result = repository.autocompleteIngredients(query)
        return result.fold(
            onSuccess = { hits -> hits.also { lookupCache[query] = it } },
            onFailure = { error ->
                // Report the first failure only, and don't cache it — a later line may
                // succeed once connectivity returns.
                if (lookupFailure == null) lookupFailure = friendlyApiError(error)
                emptyList()
            }
        )
    }

    // ---- review -------------------------------------------------------------

    fun toggleInclude(key: Int) = updateRow(key) { it.copy(include = !it.include) }

    /** Digits only, so a scanned quantity stays a valid count (as in [BuyDraft]). */
    fun setQuantity(key: Int, input: String) =
        updateRow(key) { it.copy(quantity = input.filter(Char::isDigit)) }

    fun setUnit(key: Int, unit: String) = updateRow(key) { it.copy(unit = unit) }

    fun openSearch(key: Int) = updateRow(key) {
        it.copy(searchOpen = true, searchQuery = it.query, suggestions = emptyList(), error = null)
    }

    fun closeSearch(key: Int) {
        searchJob?.cancel()
        updateRow(key) { it.copy(searchOpen = false, suggestions = emptyList(), loading = false) }
    }

    /** Debounced correction lookup, mirroring [PantryViewModel.onQueryChange]. */
    fun onSearchQueryChange(key: Int, query: String) {
        searchJob?.cancel()
        val searchable = query.trim().length >= MIN_QUERY_LENGTH
        updateRow(key) {
            if (searchable) it.copy(searchQuery = query)
            else it.copy(searchQuery = query, suggestions = emptyList(), loading = false, error = null)
        }
        if (!searchable) return
        searchJob = viewModelScope.launch {
            delay(PantryViewModel.DEBOUNCE_MS)
            updateRow(key) { it.copy(loading = true, error = null) }
            repository.autocompleteIngredients(query.trim())
                .onSuccess { hits -> updateRow(key) { it.copy(suggestions = hits, loading = false) } }
                .onFailure { e -> updateRow(key) { it.copy(error = friendlyApiError(e), loading = false) } }
        }
    }

    /** A user-picked ingredient is correct by definition, so the row becomes confident. */
    fun selectMatch(key: Int, suggestion: IngredientAutocomplete) {
        searchJob?.cancel()
        val units = suggestion.possibleUnits.ifEmpty { listOf(PantryViewModel.DEFAULT_UNIT) }
        updateRow(key) { row ->
            row.copy(
                match = suggestion,
                confident = true,
                unitOptions = units,
                unit = row.unit.takeIf { it in units } ?: units.first(),
                include = true,
                searchOpen = false,
                suggestions = emptyList(),
                loading = false,
                error = null
            )
        }
    }

    private fun updateRow(key: Int, transform: (ScanRowUi) -> ScanRowUi) {
        _state.update { current ->
            if (current !is ScanState.Review) current
            else current.copy(rows = current.rows.map { if (it.key == key) transform(it) else it })
        }
    }

    // ---- commit -------------------------------------------------------------

    /**
     * Writes every checked row as a purchase, then reports how many landed.
     *
     * Scanned lots carry no expiry: receipts don't print one. They show up in the
     * pantry immediately but contribute nothing to expiry warnings until edited.
     */
    fun confirmAll() {
        val review = _state.value as? ScanState.Review ?: return
        if (_saving.value) return
        val additions = review.rows.filter { it.include && it.canAdd }
        if (additions.isEmpty()) return

        viewModelScope.launch {
            _saving.value = true
            val date = today().toEpochDay()
            additions.forEach { row ->
                val match = row.match ?: return@forEach
                dao.recordPurchase(
                    spoonacularId = match.id,
                    name = match.name,
                    unit = row.unit,
                    quantity = row.amount,
                    dateEpochDay = date,
                    imageUrl = match.image,
                    aisle = match.aisle
                )
            }
            _saving.value = false
            _state.value = ScanState.Saved(additions.size)
        }
    }

    companion object {
        /**
         * Lookups run at most this many at a time. Six keeps a typical receipt inside
         * the 5 s budget without hammering a free-tier key shared by the whole team.
         */
        private const val LOOKUP_CONCURRENCY = 6

        /**
         * Hard cap on the model call. Generous because there is no other parser:
         * past this the scan fails with a retry message rather than hanging.
         */
        private const val LLM_TIMEOUT_MS = 20_000L

        /**
         * Units that describe a thing rather than an amount of it. Preferred when the
         * receipt printed no size, since a receipt line is a purchased package.
         */
        private val PACKAGE_LIKE_UNITS = setOf("piece", "package", "serving", "item", "unit")

        /** Shortest prefix overlap that counts as a real relationship between two words. */
        private const val MIN_TOKEN_OVERLAP = 4

        /**
         * True when the query and a candidate name share a word.
         *
         * Prefix-tolerant so ordinary plurals and inflections still match ("banana"
         * against "bananas"), with a length floor that stops incidental overlaps from
         * qualifying — "ave" is not agave, and a stray flag letter is not a food.
         */
        internal fun sharesToken(query: String, name: String): Boolean {
            val nameTokens = name.split(" ").filter { it.isNotBlank() }
            return query.split(" ").filter { it.isNotBlank() }.any { q ->
                nameTokens.any { n ->
                    val shorter = minOf(q.length, n.length)
                    shorter >= MIN_TOKEN_OVERLAP &&
                        (q.startsWith(n.take(shorter)) && n.startsWith(q.take(shorter)))
                }
            }
        }

        /** Shorter than this and autocomplete just returns noise. */
        private const val MIN_QUERY_LENGTH = 2

        /**
         * Fallbacks for a query the API doesn't recognize, loosest last.
         *
         * English noun phrases are head-final, so dropping leading modifiers is what
         * rescues "green giant sweet corn" → "sweet corn" → "corn". The leading token is
         * tried last for the inverted names some receipts print ("TOM ROMA" → "tomato").
         */
        internal fun fallbackQueries(query: String): List<String> {
            val tokens = query.split(" ").filter { it.isNotBlank() }
            if (tokens.size <= 1) return listOf(query)
            return listOf(
                query,
                tokens.takeLast(2).joinToString(" "),
                tokens.last(),
                tokens.first()
            ).distinct()
        }

        /** Factory that injects the dependencies (no DI framework in the project). */
        fun factory(
            dao: PantryDao,
            repository: SpoonacularRepository = SpoonacularRepositoryImpl,
            llm: OpenRouterRepository = OpenRouterRepositoryImpl
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReceiptScanViewModel(dao, repository, llm) }
        }
    }
}
