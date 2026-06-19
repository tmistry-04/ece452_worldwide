package com.example.pantryparty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.pantryparty.data.PantryDatabase
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.SpoonacularRepository
import com.example.pantryparty.ui.RecipeScreen
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
private enum class Tab(val label: String) { PANTRY("Pantry"), RECIPES("Recipes") }

@Composable
fun MainScaffold(dao: PantryDao) {
    var tab by remember { mutableStateOf(Tab.PANTRY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.PANTRY,
                    onClick = { tab = Tab.PANTRY },
                    icon = { Text("🥫") },
                    label = { Text(Tab.PANTRY.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.RECIPES,
                    onClick = { tab = Tab.RECIPES },
                    icon = { Text("🍳") },
                    label = { Text(Tab.RECIPES.label) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            when (tab) {
                Tab.PANTRY -> PantryScreen(dao = dao)
                Tab.RECIPES -> RecipeScreen(dao = dao)
            }
        }
    }
}

/** Original home screen: add an ingredient, then the pantry list. */
@Composable
fun PantryScreen(dao: PantryDao) {
    AddIngredientScreen(dao = dao)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    PantryList(dao = dao)
}

/**
 * Feature 1 — guided ingredient entry:
 *   type -> autocomplete suggestions -> pick one -> pick a unit -> enter amount -> save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddIngredientScreen(dao: PantryDao) {
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

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add Ingredient", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (selected == null) {
            // Step 1: search + autocomplete dropdown
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Start typing… e.g. apple") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        suggestions.forEach { s ->
                            Text(
                                text = s.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = s
                                        selectedUnit = s.possibleUnits.firstOrNull()
                                        suggestions = emptyList()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        } else {
            val ingredient = selected!!
            // Step 2: chosen ingredient + unit picker
            Text(
                text = ingredient.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ingredient.aisle?.let { Text("Aisle: $it", style = MaterialTheme.typography.bodySmall) }
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
                Button(
                    onClick = {
                        val qty = amount.toIntOrNull() ?: 1
                        scope.launch {
                            dao.upsert(
                                PantryItem(
                                    name = ingredient.name,
                                    quantity = qty,
                                    unit = selectedUnit ?: "piece",
                                    spoonacularId = ingredient.id
                                )
                            )
                            reset()
                        }
                    },
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

@Composable
fun PantryList(dao: PantryDao, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val items by dao.observeAll().collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Pantry", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text("No items yet — add one above.")
        } else {
            // Bounded height: the outer Column already scrolls.
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(items, key = { it.id }) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${item.name}  —  ${item.quantity} ${item.unit}",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { scope.launch { dao.delete(item) } }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
