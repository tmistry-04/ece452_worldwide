package com.example.pantryparty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryDatabase
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.ui.NetworkImage
import com.example.pantryparty.ui.RecipeScreen
import com.example.pantryparty.ui.ingredientImageUrl
import com.example.pantryparty.ui.theme.PantryPartyTheme
import com.example.pantryparty.viewmodel.AddIngredientUiState
import com.example.pantryparty.viewmodel.PantryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = PantryDatabase.getInstance(this).pantryDao()
        setContent {
            PantryPartyTheme {
                MainScaffold(dao = dao)
            }
        }
    }
}

/** Top-level screens reachable from the bottom navigation bar. */
private enum class Tab(val label: String, val icon: ImageVector) {
    PANTRY("Pantry", Icons.Filled.Kitchen),
    RECIPES("Recipes", Icons.Filled.Restaurant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(dao: PantryDao) {
    var tab by remember { mutableStateOf(Tab.PANTRY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Branded title bar; shows the active tab's name.
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Pantry Party",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Each tab manages its own scrolling content.
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (tab) {
                Tab.PANTRY -> PantryScreen(dao = dao)
                Tab.RECIPES -> RecipeScreen(dao = dao)
            }
        }
    }
}

/**
 * Pantry tab: a single scrolling list whose first item is the "add ingredient"
 * card, followed by the pantry rows. Using one LazyColumn (instead of an outer
 * scroll wrapping an inner list) keeps scrolling smooth for long pantries.
 *
 * All state and pantry mutations live in [PantryViewModel]; this composable only
 * observes state and forwards events.
 */
@Composable
fun PantryScreen(dao: PantryDao) {
    val viewModel: PantryViewModel = viewModel(factory = PantryViewModel.factory(dao))
    val items by viewModel.pantry.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        // Add-ingredient flow lives at the top of the list.
        item {
            AddIngredientCard(
                state = addState,
                onQueryChange = viewModel::onQueryChange,
                onSelectSuggestion = viewModel::selectSuggestion,
                onSelectUnit = viewModel::selectUnit,
                onAmountChange = viewModel::onAmountChange,
                onSave = viewModel::save,
                onCancel = viewModel::resetAdd
            )
        }

        item {
            Text(
                "My Pantry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (items.isEmpty()) {
            item { EmptyPantryHint() }
        } else {
            items(items, key = { it.id }) { item ->
                PantryRow(
                    item = item,
                    onIncrement = { viewModel.increment(item) },
                    onDecrement = { viewModel.decrement(item) },
                    onDelete = { viewModel.delete(item) }
                )
            }
        }
    }
}

/** Friendly empty state shown when the pantry has no items yet. */
@Composable
private fun EmptyPantryHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your pantry is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "Search above to add your first ingredient.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * One pantry item rendered as a card: thumbnail · name + unit · −/qty/+ stepper
 * · delete. The stepper is the manual "I used some" control.
 */
@Composable
private fun PantryRow(
    item: PantryItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ingredient thumbnail (or fork-and-knife placeholder).
            NetworkImage(
                url = ingredientImageUrl(item.imageUrl),
                contentDescription = item.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // −/qty/+ stepper.
            StepperButton(icon = Icons.Filled.Remove, description = "Use one", onClick = onDecrement)
            Text(
                item.quantity.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            StepperButton(icon = Icons.Filled.Add, description = "Add one", onClick = onIncrement)

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${item.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Small circular tonal +/- button used by the pantry stepper. */
@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Guided ingredient entry, wrapped in a card:
 *   type -> autocomplete suggestions -> pick one -> pick a unit -> enter amount -> save.
 *
 * Stateless: all flow state lives in [AddIngredientUiState]; user actions are
 * dispatched through the callbacks (wired to [PantryViewModel]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddIngredientCard(
    state: AddIngredientUiState,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (IngredientAutocomplete) -> Unit,
    onSelectUnit: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Add Ingredient",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            val selected = state.selected
            if (selected == null) {
                // Step 1: search + autocomplete dropdown
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Start typing… e.g. apple") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.loading) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                if (state.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column {
                        state.suggestions.forEach { s ->
                            // Suggestion row: thumbnail + name, tappable.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSuggestion(s) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NetworkImage(
                                    url = ingredientImageUrl(s.image),
                                    contentDescription = s.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(s.name.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            } else {
                // Step 2: chosen ingredient + unit picker
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkImage(
                        url = ingredientImageUrl(selected.image),
                        contentDescription = selected.name,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = selected.name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        selected.aisle?.let {
                            Text("Aisle: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Unit:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                if (selected.possibleUnits.isEmpty()) {
                    Text("No units returned — defaulting to \"piece\".")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.possibleUnits.forEach { unit ->
                            FilterChip(
                                selected = state.selectedUnit == unit,
                                onClick = { onSelectUnit(unit) },
                                label = { Text(unit) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                // Step 3: amount
                Text("Amount:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = onAmountChange,
                    label = { Text("Number of ${state.selectedUnit ?: "units"}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onSave,
                        enabled = state.quantity > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save to pantry") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
