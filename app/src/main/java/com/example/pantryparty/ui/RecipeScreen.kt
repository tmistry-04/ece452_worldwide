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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.recipe.ConsumeResult
import com.example.pantryparty.recipe.RecipeMatch
import com.example.pantryparty.recipe.RecipeMatcher
import com.example.pantryparty.viewmodel.RecipeCardState
import com.example.pantryparty.viewmodel.RecipeMode
import com.example.pantryparty.viewmodel.RecipeViewModel

/**
 * Feature 2 — recipe finding. State and the Spoonacular calls live in
 * [RecipeViewModel]; this screen observes its state and forwards events.
 *  - FROM_PANTRY:      uses every pantry item.
 *  - PICK_INGREDIENTS: uses only the items the user selects.
 */
@Composable
fun RecipeScreen(dao: PantryDao, modifier: Modifier = Modifier) {
    val viewModel: RecipeViewModel = viewModel(factory = RecipeViewModel.factory(dao))
    val pantry by viewModel.pantry.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val cardStates by viewModel.cardStates.collectAsStateWithLifecycle()

    // Mode A auto-runs once when first opened (or re-entered) with a non-empty pantry.
    LaunchedEffect(ui.mode, pantry.isNotEmpty()) {
        viewModel.autoLoadFromPantryIfNeeded()
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
            mode = ui.mode,
            onFromPantry = viewModel::showFromPantry,
            onPick = viewModel::showPickIngredients
        )
        Spacer(Modifier.height(12.dp))

        when (ui.mode) {
            RecipeMode.FROM_PANTRY -> FromPantryControls(
                pantry = pantry,
                onRefresh = viewModel::refreshFromPantry
            )
            RecipeMode.PICK_INGREDIENTS -> PickIngredientsControls(
                pantry = pantry,
                selectedIds = ui.selectedIds,
                onToggle = viewModel::toggleSelected,
                onSearch = viewModel::searchSelected
            )
        }

        Spacer(Modifier.height(12.dp))

        if (ui.loading) {
            CircularProgressIndicator()
        }
        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (!ui.loading && ui.hasSearched && ui.error == null) {
            RecipeResults(
                recipes = ui.recipes,
                cardStates = cardStates,
                onCheckAmounts = viewModel::checkAmounts,
                onPrepareConsume = viewModel::prepareConsume,
                onConfirmConsume = viewModel::confirmConsume,
                onDismissConsume = viewModel::dismissConsume
            )
        }
    }
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
private fun RecipeResults(
    recipes: List<RecipeByIngredient>,
    cardStates: Map<Int, RecipeCardState>,
    onCheckAmounts: (Int) -> Unit,
    onPrepareConsume: (Int) -> Unit,
    onConfirmConsume: (Int) -> Unit,
    onDismissConsume: (Int) -> Unit
) {
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
            RecipeCard(
                recipe = recipe,
                cardState = cardStates[recipe.id] ?: RecipeCardState(),
                onCheckAmounts = { onCheckAmounts(recipe.id) },
                onPrepareConsume = { onPrepareConsume(recipe.id) },
                onConfirmConsume = { onConfirmConsume(recipe.id) },
                onDismissConsume = { onDismissConsume(recipe.id) }
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(
    recipe: RecipeByIngredient,
    cardState: RecipeCardState,
    onCheckAmounts: () -> Unit,
    onPrepareConsume: () -> Unit,
    onConfirmConsume: () -> Unit,
    onDismissConsume: () -> Unit
) {
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
                        onClick = onCheckAmounts,
                        enabled = !cardState.checking
                    ) { Text("Check amounts") }

                    FilledTonalButton(
                        onClick = onPrepareConsume,
                        enabled = !cardState.checking
                    ) { Text("I made this") }

                    if (cardState.checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }

                cardState.checkError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                cardState.amountCheck?.let { AmountDetail(it) }
            }
        }
    }

    // Confirmation dialog for "I made this" — applies on confirm.
    cardState.pendingConsume?.let { result ->
        MadeThisDialog(
            result = result,
            onDismiss = onDismissConsume,
            onConfirm = onConfirmConsume
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
