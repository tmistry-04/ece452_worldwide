package com.example.pantryparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.recipe.ConsumeResult
import com.example.pantryparty.recipe.PantryConsumer
import com.example.pantryparty.recipe.RecipeMatch
import com.example.pantryparty.recipe.RecipeMatcher
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Which recipe-finding mode the screen is showing. */
private enum class RecipeMode { FROM_PANTRY, PICK_INGREDIENTS }

/** How many candidate recipes to request per search (keeps API point cost ~1). */
private const val RECIPE_COUNT = 10

/**
 * Feature 2 — recipe finding. Both modes use a single cheap findByIngredients
 * call (1 point), which already returns the have/missing split with staples
 * ignored. The amount ("do I have enough?") check is on-demand per recipe.
 *  - FROM_PANTRY:      uses every pantry item.
 *  - PICK_INGREDIENTS: uses only the items the user selects.
 */
@Composable
fun RecipeScreen(dao: PantryDao, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val pantry by dao.observeAll().collectAsState(initial = emptyList())

    var mode by remember { mutableStateOf(RecipeMode.FROM_PANTRY) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var recipes by remember { mutableStateOf<List<RecipeByIngredient>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    // Guards Mode A so it auto-runs only once per entry, not on every pantry edit.
    var pantryLoaded by remember { mutableStateOf(false) }

    // One cheap call: findByIngredients returns have/missing already bucketed.
    fun findRecipes(names: List<String>) {
        if (names.isEmpty()) return
        scope.launch {
            loading = true
            error = null
            hasSearched = true
            SpoonacularRepository.findRecipesByIngredients(names, number = RECIPE_COUNT)
                .onSuccess { recipes = RecipeMatcher.bucketByMissed(it) }
                .onFailure { error = friendlyError(it) }
            loading = false
        }
    }

    // Mode A auto-runs once when first opened with a non-empty pantry.
    LaunchedEffect(mode, pantry.isNotEmpty()) {
        if (mode == RecipeMode.FROM_PANTRY && pantry.isNotEmpty() && !pantryLoaded) {
            pantryLoaded = true
            findRecipes(pantry.map { it.name })
        }
    }

    // Owns its own vertical scroll (MainScaffold no longer provides one).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Recipes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        // Mode toggle
        FlowRowModes(
            mode = mode,
            onFromPantry = {
                mode = RecipeMode.FROM_PANTRY
                // Drop any picked-ingredient results and let Mode A re-run fresh,
                // so the pantry view never shows the previous mode's recipes.
                recipes = emptyList()
                hasSearched = false
                error = null
                pantryLoaded = false
            },
            onPick = {
                mode = RecipeMode.PICK_INGREDIENTS
                recipes = emptyList()
                hasSearched = false
                error = null
            }
        )
        Spacer(Modifier.height(12.dp))

        when (mode) {
            RecipeMode.FROM_PANTRY -> FromPantryControls(
                pantry = pantry,
                onRefresh = { findRecipes(pantry.map { it.name }) }
            )
            RecipeMode.PICK_INGREDIENTS -> PickIngredientsControls(
                pantry = pantry,
                selectedIds = selectedIds,
                onToggle = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                onSearch = {
                    val names = pantry.filter { it.id in selectedIds }.map { it.name }
                    findRecipes(names)
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            CircularProgressIndicator()
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (!loading && hasSearched && error == null) {
            RecipeResults(recipes = recipes, dao = dao)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowModes(mode: RecipeMode, onFromPantry: () -> Unit, onPick: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == RecipeMode.FROM_PANTRY,
            onClick = onFromPantry,
            label = { Text("From my pantry") }
        )
        FilterChip(
            selected = mode == RecipeMode.PICK_INGREDIENTS,
            onClick = onPick,
            label = { Text("Pick ingredients") }
        )
    }
}

@Composable
private fun FromPantryControls(pantry: List<PantryItem>, onRefresh: () -> Unit) {
    if (pantry.isEmpty()) {
        Text("Your pantry is empty — add ingredients first.")
        return
    }
    Text(
        "Showing recipes you can make with your ${pantry.size} pantry item(s).",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onRefresh) { Text("Refresh") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickIngredientsControls(
    pantry: List<PantryItem>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onSearch: () -> Unit
) {
    if (pantry.isEmpty()) {
        Text("Your pantry is empty — add ingredients first.")
        return
    }
    Text("Pick the ingredients to cook with:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pantry.forEach { item ->
            FilterChip(
                selected = item.id in selectedIds,
                onClick = { onToggle(item.id) },
                label = { Text(item.name) }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onSearch, enabled = selectedIds.isNotEmpty()) {
        Text("Find recipes")
    }
}

@Composable
private fun RecipeResults(recipes: List<RecipeByIngredient>, dao: PantryDao) {
    if (recipes.isEmpty()) {
        // Polished empty-results state.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No recipes within ${RecipeMatcher.MAX_MISSING} missing ingredients.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Headline: how many we can make right now.
    val readyCount = recipes.count { it.missedIngredientCount == 0 }
    Text(
        "You can make $readyCount recipe(s) right now",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))

    // Render section by section: Ready to make, Missing 1, Missing 2, Missing 3.
    val grouped = recipes.groupBy { it.missedIngredientCount }
    grouped.keys.sorted().forEach { count ->
        val header = if (count == 0) "Ready to make" else "Missing $count"
        Text(header, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        grouped.getValue(count).forEach { recipe ->
            RecipeCard(recipe = recipe, dao = dao)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(recipe: RecipeByIngredient, dao: PantryDao) {
    val scope = rememberCoroutineScope()
    // null = not checked yet; otherwise the amount-level result for this recipe.
    var amountCheck by remember(recipe.id) { mutableStateOf<RecipeMatch?>(null) }
    var checking by remember(recipe.id) { mutableStateOf(false) }
    var checkError by remember(recipe.id) { mutableStateOf<String?>(null) }
    // Pending deduction awaiting user confirmation (the "I made this" flow).
    var pendingConsume by remember(recipe.id) { mutableStateOf<ConsumeResult?>(null) }

    val isReady = recipe.missedIngredientCount == 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Hero image (full width). Spoonacular returns an absolute URL here.
            NetworkImage(
                url = recipe.image,
                contentDescription = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))

                // Status chip: ready vs. how many missing.
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(if (isReady) "Ready to make" else "Missing ${recipe.missedIngredientCount}")
                    },
                    leadingIcon = if (isReady) {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = if (isReady)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer,
                        disabledLabelColor = if (isReady)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                if (recipe.usedIngredients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("You have:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recipe.usedIngredients.forEach { IngredientPill(it.name, owned = true) }
                    }
                }

                if (recipe.missedIngredients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Need:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recipe.missedIngredients.forEach { IngredientPill(it.name, owned = false) }
                    }
                }

                // Actions: on-demand amount check + "I made this" deduction.
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                checking = true
                                checkError = null
                                val pantry = dao.getAll()
                                SpoonacularRepository.getRecipeInformationBulk(listOf(recipe.id))
                                    .onSuccess { infos ->
                                        val info = infos.firstOrNull()
                                        amountCheck = info?.let { RecipeMatcher.match(pantry, it) }
                                    }
                                    .onFailure { checkError = friendlyError(it) }
                                checking = false
                            }
                        },
                        enabled = !checking
                    ) { Text("Check amounts") }

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                checking = true
                                checkError = null
                                val pantry = dao.getAll()
                                // Fetch required amounts, then compute the deduction preview.
                                SpoonacularRepository.getRecipeInformationBulk(listOf(recipe.id))
                                    .onSuccess { infos ->
                                        infos.firstOrNull()?.let { info ->
                                            pendingConsume = PantryConsumer.consume(pantry, info)
                                        }
                                    }
                                    .onFailure { checkError = friendlyError(it) }
                                checking = false
                            }
                        },
                        enabled = !checking
                    ) { Text("I made this") }

                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }

                checkError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                amountCheck?.let { AmountDetail(it) }
            }
        }
    }

    // Confirmation dialog for "I made this" — applies on confirm.
    pendingConsume?.let { result ->
        MadeThisDialog(
            result = result,
            onDismiss = { pendingConsume = null },
            onConfirm = {
                scope.launch {
                    // Apply the previewed deductions to the pantry.
                    result.toUpdate.forEach { dao.update(it) }
                    result.toDelete.forEach { dao.delete(it) }
                }
                pendingConsume = null
            }
        )
    }
}

/** Small rounded pill for an ingredient name — green if owned, neutral if needed. */
@Composable
private fun IngredientPill(name: String, owned: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (owned)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Confirmation sheet for "I made this": shows exactly what will be deducted and
 * what's skipped (different unit / not in pantry) before touching the pantry.
 */
@Composable
private fun MadeThisDialog(
    result: ConsumeResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val nothingToDo = result.toUpdate.isEmpty() && result.toDelete.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Made this recipe?") },
        text = {
            Column {
                if (nothingToDo) {
                    Text("Nothing in your pantry can be safely deducted for this recipe.")
                } else {
                    Text("Your pantry will be updated:", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    result.toUpdate.forEach {
                        Text("• ${it.name} → ${it.quantity} ${it.unit} left", style = MaterialTheme.typography.bodySmall)
                    }
                    result.toDelete.forEach {
                        Text("• ${it.name} → used up (removed)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (result.skipped.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Skipped (different unit or not tracked):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    result.skipped.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            // Nothing to deduct -> just acknowledge.
            if (nothingToDo) {
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                Button(onClick = onConfirm) { Text("Update pantry") }
            }
        },
        dismissButton = if (nothingToDo) null else {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        }
    )
}

/** Amount-level breakdown for one recipe after the on-demand details fetch. */
@Composable
private fun AmountDetail(match: RecipeMatch) {
    Spacer(Modifier.height(8.dp))
    if (match.missing.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "You have enough of everything.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }
    Text("Short on:", style = MaterialTheme.typography.labelLarge)
    match.missing.forEach { m ->
        val need = formatAmount(m.required.amount, m.required.unit, m.required.name)
        val haveNote = if (m.haveQuantity != null) "  (have ${m.haveQuantity} ${m.haveUnit})" else ""
        Text("• $need$haveNote", style = MaterialTheme.typography.bodySmall)
    }
}

/** "2.0 cup flour" -> "2 cup flour"; drops trailing .0 for readability. */
private fun formatAmount(amount: Double, unit: String, name: String): String {
    val amountText = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
    return listOf(amountText, unit, name).filter { it.isNotBlank() }.joinToString(" ")
}
