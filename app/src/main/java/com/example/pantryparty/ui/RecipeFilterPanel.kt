package com.example.pantryparty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.recipe.NutrientRange
import com.example.pantryparty.recipe.NutrientSpec
import com.example.pantryparty.recipe.RecipeFilters

/**
 * Collapsible complexSearch filter panel: free-text query, pantry-ingredient
 * selection, and one sub-section per family of API options (cuisines, diets,
 * intolerances, meal type, nutrients incl. vitamins, misc). All state lives in
 * [RecipeFilters] / the ViewModel; only the open/closed flags are local.
 */
@Composable
fun RecipeFilterPanel(
    pantry: List<StockItem>,
    selectedIds: Set<Long>,
    filters: RecipeFilters,
    searchEnabled: Boolean,
    onToggleIngredient: (Long) -> Unit,
    onSelectAllIngredients: () -> Unit,
    onClearIngredients: () -> Unit,
    onFiltersChange: (RecipeFilters) -> Unit,
    onSearch: () -> Unit,
    onReset: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (filters.activeFilterCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(${filters.activeFilterCount})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse filters" else "Expand filters"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = { onFiltersChange(filters.copy(query = it)) },
                        label = { Text("Search recipes") },
                        placeholder = { Text("e.g. pasta with basil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    IngredientSection(
                        pantry = pantry,
                        selectedIds = selectedIds,
                        onToggle = onToggleIngredient,
                        onSelectAll = onSelectAllIngredients,
                        onClear = onClearIngredients
                    )
                    MultiChipSection("Cuisines", RecipeFilters.CUISINES, filters.cuisines) {
                        onFiltersChange(filters.copy(cuisines = it))
                    }
                    MultiChipSection("Exclude cuisines", RecipeFilters.CUISINES, filters.excludeCuisines) {
                        onFiltersChange(filters.copy(excludeCuisines = it))
                    }
                    MultiChipSection("Diets", RecipeFilters.DIETS, filters.diets) {
                        onFiltersChange(filters.copy(diets = it))
                    }
                    MultiChipSection("Intolerances", RecipeFilters.INTOLERANCES, filters.intolerances) {
                        onFiltersChange(filters.copy(intolerances = it))
                    }
                    SingleChipSection("Meal type", RecipeFilters.MEAL_TYPES, filters.mealType) {
                        onFiltersChange(filters.copy(mealType = it))
                    }
                    NutrientSection(filters, onFiltersChange)
                    MoreOptionsSection(filters, onFiltersChange)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSearch, enabled = searchEnabled) { Text("Search") }
                TextButton(onClick = onReset, enabled = filters.activeFilterCount > 0) {
                    Text("Reset filters")
                }
            }
        }
    }
}

/** One collapsible family of options; the count shows how many are set inside. */
@Composable
private fun FilterSection(
    title: String,
    activeCount: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (activeCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "($activeCount)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Collapse $title" else "Expand $title"
            )
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(bottom = 8.dp), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientSection(
    pantry: List<StockItem>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    FilterSection("Ingredients from my pantry", activeCount = selectedIds.size) {
        if (pantry.isEmpty()) {
            Text(
                "Your pantry is empty — add ingredients first, or search with the other filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FilterSection
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSelectAll) { Text("Select all") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pantry.forEach { item ->
                FilterChip(
                    selected = item.id in selectedIds,
                    onClick = { onToggle(item.id) },
                    label = { Text(item.name) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChipSection(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit
) {
    FilterSection(title, activeCount = selected.size) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = {
                        onSelectedChange(if (option in selected) selected - option else selected + option)
                    },
                    label = { Text(option) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChipSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit
) {
    FilterSection(title, activeCount = if (selected.isEmpty()) 0 else 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    // Tapping the active choice clears it (single-select, optional).
                    onClick = { onSelectedChange(if (option == selected) "" else option) },
                    label = { Text(option) }
                )
            }
        }
    }
}

@Composable
private fun NutrientSection(filters: RecipeFilters, onFiltersChange: (RecipeFilters) -> Unit) {
    FilterSection(
        "Nutrition & vitamins",
        activeCount = filters.nutrients.values.count { it.isActive }
    ) {
        Text(
            "Per serving. Leave blank for no limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RecipeFilters.NUTRIENTS.groupBy { it.group }.forEach { (group, specs) ->
            Spacer(Modifier.height(8.dp))
            Text(group, style = MaterialTheme.typography.labelLarge)
            specs.forEach { spec ->
                NutrientRow(
                    spec = spec,
                    range = filters.nutrients[spec.param] ?: NutrientRange(),
                    onChange = { new ->
                        onFiltersChange(filters.withNutrient(spec.param) { new })
                    }
                )
            }
        }
    }
}

@Composable
private fun NutrientRow(spec: NutrientSpec, range: NutrientRange, onChange: (NutrientRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                spec.unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        NumberField("Min", range.min, Modifier.width(90.dp)) { onChange(range.copy(min = it)) }
        NumberField("Max", range.max, Modifier.width(90.dp)) { onChange(range.copy(max = it)) }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun MoreOptionsSection(filters: RecipeFilters, onFiltersChange: (RecipeFilters) -> Unit) {
    val activeCount =
        (if (filters.titleMatch.isNotBlank()) 1 else 0) +
            (if (filters.equipment.isNotBlank()) 1 else 0) +
            (if (filters.excludeIngredients.isNotBlank()) 1 else 0) +
            (if (filters.maxReadyTime.isNotBlank()) 1 else 0) +
            (if (filters.minServings.isNotBlank() || filters.maxServings.isNotBlank()) 1 else 0) +
            (if (filters.sort.isNotEmpty()) 1 else 0) +
            (if (!filters.instructionsRequired) 1 else 0)

    FilterSection("More options", activeCount = activeCount) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filters.titleMatch,
                onValueChange = { onFiltersChange(filters.copy(titleMatch = it)) },
                label = { Text("Title must contain") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = filters.equipment,
                onValueChange = { onFiltersChange(filters.copy(equipment = it)) },
                label = { Text("Equipment (comma-separated)") },
                placeholder = { Text("e.g. blender, frying pan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = filters.excludeIngredients,
                onValueChange = { onFiltersChange(filters.copy(excludeIngredients = it)) },
                label = { Text("Exclude ingredients (comma-separated)") },
                placeholder = { Text("e.g. olives, capers") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Max ready time (min)", filters.maxReadyTime, Modifier.weight(1f)) {
                    onFiltersChange(filters.copy(maxReadyTime = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Min servings", filters.minServings, Modifier.weight(1f)) {
                    onFiltersChange(filters.copy(minServings = it))
                }
                NumberField("Max servings", filters.maxServings, Modifier.weight(1f)) {
                    onFiltersChange(filters.copy(maxServings = it))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Require instructions", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Only recipes with cooking steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = filters.instructionsRequired,
                    onCheckedChange = { onFiltersChange(filters.copy(instructionsRequired = it)) }
                )
            }
            SortPicker(filters, onFiltersChange)
        }
    }
}

@Composable
private fun SortPicker(filters: RecipeFilters, onFiltersChange: (RecipeFilters) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Text("Sort by", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { menuOpen = true }) {
                Text(RecipeFilters.sortLabel(filters.sort))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                RecipeFilters.SORTS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onFiltersChange(filters.copy(sort = option.param))
                            menuOpen = false
                        }
                    )
                }
            }
        }
        // Direction only means something once a sort is chosen (and "random" has none).
        if (filters.sort.isNotEmpty() && filters.sort != "random") {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.sortDirection == "desc",
                    onClick = { onFiltersChange(filters.copy(sortDirection = "desc")) },
                    label = { Text("High to low") }
                )
                FilterChip(
                    selected = filters.sortDirection == "asc",
                    onClick = { onFiltersChange(filters.copy(sortDirection = "asc")) },
                    label = { Text("Low to high") }
                )
            }
        }
    }
}
