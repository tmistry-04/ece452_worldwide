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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.SpoonacularRepository
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

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Recipes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Mode toggle
        FlowRowModes(
            mode = mode,
            onFromPantry = { mode = RecipeMode.FROM_PANTRY },
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
        Text("No recipes found within ${RecipeMatcher.MAX_MISSING} missing ingredients.")
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
        Spacer(Modifier.height(4.dp))
        grouped.getValue(count).forEach { recipe ->
            RecipeCard(recipe = recipe, dao = dao)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RecipeCard(recipe: RecipeByIngredient, dao: PantryDao) {
    val scope = rememberCoroutineScope()
    // null = not checked yet; otherwise the amount-level result for this recipe.
    var amountCheck by remember(recipe.id) { mutableStateOf<RecipeMatch?>(null) }
    var checking by remember(recipe.id) { mutableStateOf(false) }
    var checkError by remember(recipe.id) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(recipe.title, fontWeight = FontWeight.Bold)

            if (recipe.missedIngredients.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Need:", style = MaterialTheme.typography.labelLarge)
                recipe.missedIngredients.forEach { m ->
                    Text("• ${m.name}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (recipe.usedIngredients.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("You have:", style = MaterialTheme.typography.labelLarge)
                recipe.usedIngredients.forEach { u ->
                    Text("• ${u.name}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // On-demand amount check: fetches just this one recipe (~1 point).
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                }
            }

            checkError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            amountCheck?.let { AmountDetail(it) }
        }
    }
}

/** Amount-level breakdown for one recipe after the on-demand details fetch. */
@Composable
private fun AmountDetail(match: RecipeMatch) {
    Spacer(Modifier.height(4.dp))
    if (match.missing.isEmpty()) {
        Text(
            "You have enough of everything for this recipe.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
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
