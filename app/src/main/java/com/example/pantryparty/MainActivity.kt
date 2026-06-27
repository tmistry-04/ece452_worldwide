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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryDatabase
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.ui.NetworkImage
import com.example.pantryparty.ui.RecipeScreen
import com.example.pantryparty.ui.ingredientImageUrl
import com.example.pantryparty.ui.theme.PantryPartyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 */
@Composable
fun PantryScreen(dao: PantryDao) {
    val scope = rememberCoroutineScope()
    val items by dao.observeAll().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        // Add-ingredient flow lives at the top of the list.
        item { AddIngredientCard(dao = dao) }

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
                    onIncrement = {
                        // Bump the count by one.
                        scope.launch { dao.upsert(item.copy(quantity = item.quantity + 1)) }
                    },
                    onDecrement = {
                        // Decrement; using the last one removes the row entirely.
                        scope.launch {
                            if (item.quantity <= 1) dao.delete(item)
                            else dao.upsert(item.copy(quantity = item.quantity - 1))
                        }
                    },
                    onDelete = { scope.launch { dao.delete(item) } }
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddIngredientCard(dao: PantryDao) {
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<IngredientAutocomplete>>(emptyList()) }
    var selected by remember { mutableStateOf<IngredientAutocomplete?>(null) }
    var selectedUnit by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("1") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Debounced autocomplete: re-runs on each keystroke, cancels the in-flight delay.
    LaunchedEffect(query) {
        if (selected != null || query.trim().length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        error = null
        SpoonacularRepository.autocompleteIngredients(query.trim())
            .onSuccess { suggestions = it }
            .onFailure { error = it.message }
        loading = false
    }

    fun reset() {
        query = ""
        suggestions = emptyList()
        selected = null
        selectedUnit = null
        amount = "1"
        error = null
    }

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

            if (selected == null) {
                // Step 1: search + autocomplete dropdown
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Start typing… e.g. apple") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column {
                        suggestions.forEach { s ->
                            // Suggestion row: thumbnail + name, tappable.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = s
                                        selectedUnit = s.possibleUnits.firstOrNull()
                                        suggestions = emptyList()
                                    }
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
                val ingredient = selected!!
                // Step 2: chosen ingredient + unit picker
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkImage(
                        url = ingredientImageUrl(ingredient.image),
                        contentDescription = ingredient.name,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = ingredient.name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        ingredient.aisle?.let {
                            Text("Aisle: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Unit:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                if (ingredient.possibleUnits.isEmpty()) {
                    Text("No units returned — defaulting to \"piece\".")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ingredient.possibleUnits.forEach { unit ->
                            FilterChip(
                                selected = selectedUnit == unit,
                                onClick = { selectedUnit = unit },
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
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() } },
                    label = { Text("Number of ${selectedUnit ?: "units"}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val qty = amount.toIntOrNull() ?: 0
                    FilledTonalButton(
                        onClick = {
                            val unit = selectedUnit ?: "piece"
                            scope.launch {
                                // Merge into the existing row instead of inserting a
                                // duplicate for the same ingredient.
                                val existing = dao.findBySpoonacularId(ingredient.id)
                                val toSave = when {
                                    existing == null -> PantryItem(
                                        name = ingredient.name,
                                        quantity = qty,
                                        unit = unit,
                                        spoonacularId = ingredient.id,
                                        imageUrl = ingredient.image   // persist for thumbnails
                                    )
                                    // Same unit -> add to what's already on hand.
                                    existing.unit == unit ->
                                        existing.copy(quantity = existing.quantity + qty)
                                    // Different unit -> adopt the newly entered unit/amount.
                                    else -> existing.copy(
                                        quantity = qty,
                                        unit = unit,
                                        imageUrl = ingredient.image
                                    )
                                }
                                dao.upsert(toSave)
                                reset()
                            }
                        },
                        enabled = qty > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save to pantry") }
                    TextButton(onClick = { reset() }) { Text("Cancel") }
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
