package com.example.pantryparty.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pantryparty.ui.theme.PantryPartyTheme

/**
 * Screen 5 — Recipe Discovery with dietary filters.
 *
 * Requirements covered visually here:
 *  - 6.1 recipe suggestions from the pantry
 *  - 6.2 "how many of the required ingredients you have" (the "6 of 6" pill)
 *  - 6.3 filter by cook time (the filter icon / chips)
 *  - 7.1 dietary profile filters (Vegetarian / GF / Dairy-free chips)
 *  - NFR 1.2: recipes you CAN make are in colour; ones you're missing items
 *    for are shown in black & white. See [grayscale] below for how that's done.
 *
 * This is standalone + mock. The existing RecipeScreen.kt stays untouched;
 * this file is the newer "discovery" layout from the mockups.
 */

/** Mock recipe row for the discovery list. */
data class DiscoveryRecipe(
    val title: String,
    val cookMinutes: Int,
    val haveCount: Int,
    val totalCount: Int,
    val tags: List<String>,       // e.g. ["Vegetarian", "GF"]
    val accent: Color             // stand-in for the hero thumbnail colour
) {
    val missing: Int get() = totalCount - haveCount
    val isReady: Boolean get() = missing == 0
}

private val sampleRecipes = listOf(
    DiscoveryRecipe("Spinach & Feta Omelette", 18, 6, 6, listOf("Vegetarian", "GF"), Color(0xFF4C6B3C)),
    DiscoveryRecipe("Strawberry Yogurt Bowl", 5, 4, 4, listOf("GF"), Color(0xFFE0592A)),
    DiscoveryRecipe("Veggie Frittata", 35, 5, 7, listOf("Vegetarian"), Color(0xFF8A9A5B)),
)

@Composable
fun RecipeDiscoveryScreen(
    recipes: List<DiscoveryRecipe> = sampleRecipes,
    activeDietTags: Set<String> = setOf("Vegetarian"),
    onOpenRecipe: (DiscoveryRecipe) -> Unit = {},
    onToggleDiet: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ready = recipes.filter { it.isReady }
    val almost = recipes.filter { !it.isReady }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Recipes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Cook with what you've got",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Search bar + filter button.
        item { SearchBarRow(activeFilterCount = 2) }

        // Dietary filter chips (req 7.1).
        item {
            DietFilterRow(
                allTags = listOf("Vegetarian", "GF", "Dairy-free"),
                activeTags = activeDietTags,
                extraCount = 2,
                onToggle = onToggleDiet
            )
        }

        // "Missing" count filter (req 6.2 / 6.3 style quick filter).
        item { MissingFilterRow() }

        // Ready-to-cook section (in colour).
        if (ready.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Filled.Check,
                    text = "Ready to cook · ${ready.size}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            items2(ready) { RecipeDiscoveryCard(it, onClick = { onOpenRecipe(it) }) }
        }

        // Almost-there section (black & white — NFR 1.2).
        if (almost.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Filled.Schedule,
                    text = "Almost there · need a few items",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items2(almost) { RecipeDiscoveryCard(it, onClick = { onOpenRecipe(it) }) }
        }
    }
}

/**
 * LazyListScope helper so we can write items2(list) { ... } and keep each recipe
 * as its own list item. (Just wraps forEach + item for readability.)
 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2(
    data: List<T>,
    itemContent: @Composable (T) -> Unit
) {
    data.forEach { element -> item { itemContent(element) } }
}

@Composable
private fun SearchBarRow(activeFilterCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Search recipes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        // Filter button with a small count badge.
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "Filters",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (activeFilterCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        activeFilterCount.toString(),
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DietFilterRow(
    allTags: List<String>,
    activeTags: Set<String>,
    extraCount: Int,
    onToggle: (String) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allTags.forEach { tag ->
            DietChip(text = tag, active = tag in activeTags, onClick = { onToggle(tag) })
        }
        if (extraCount > 0) {
            DietChip(text = "+$extraCount", active = false, onClick = {})
        }
    }
}

/** A pill chip; green + checkmark when the diet tag is active. */
@Composable
private fun DietChip(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (active) null else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (active) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
    onClick
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingFilterRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Missing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("0", "1", "2", "3+").forEachIndexed { index, label ->
                CountPill(label = label, selected = index == 0)
            }
        }
    }
}

@Composable
private fun CountPill(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
    }
}

/**
 * One recipe card. When [DiscoveryRecipe.isReady] is false we apply the
 * [grayscale] modifier to the whole card so it renders black & white — the
 * visual cue from NFR 1.2 that you can't make it yet.
 */
@Composable
private fun RecipeDiscoveryCard(recipe: DiscoveryRecipe, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .grayscale(enabled = !recipe.isReady)
    ) {
        Column {
            // Hero band (stand-in for the recipe photo).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(recipe.accent),
                contentAlignment = Alignment.TopEnd
            ) {
                if (recipe.isReady) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            "Ready",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            "${recipe.missing} missing",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "${recipe.cookMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(12.dp))
                    // "have of total" — requirement 6.2
                    Text(
                        "${recipe.haveCount} of ${recipe.totalCount}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.size(12.dp))
                    recipe.tags.forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
        }
    }
    onClick
}

/**
 * Reusable "make it black & white" modifier.
 *
 * How it works: we intercept drawing, push the content onto its own layer, and
 * paint that layer through a ColorMatrix whose saturation is set to 0. A
 * saturation of 0 collapses every colour to its grey equivalent — exactly the
 * "recipes you can't make are greyscale" rule (NFR 1.2). When [enabled] is
 * false we just draw normally, so ready recipes stay in full colour.
 */
fun Modifier.grayscale(enabled: Boolean): Modifier =
    if (!enabled) this
    else this.drawWithContent {
        val saturationMatrix = ColorMatrix().apply { setToSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(saturationMatrix) }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, Size(size.width, size.height)), paint)
            drawContent()
            canvas.restore()
        }
    }

// ---- Android Studio preview ----------------------------------------------
@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun RecipeDiscoveryScreenPreview() {
    PantryPartyTheme {
        RecipeDiscoveryScreen()
    }
}
