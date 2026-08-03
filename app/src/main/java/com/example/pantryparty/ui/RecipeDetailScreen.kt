package com.example.pantryparty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pantryparty.ui.theme.PantryPartyTheme

/**
 * Screen 6 — Recipe Detail (have vs. need).
 *
 * Requirements covered visually:
 *  - 3.1 identify which required ingredients are NOT in the pantry (the "Need" rows)
 *  - 3.2 add missing ingredients to the grocery list in one tap (the cart button)
 *  - 6.2 how many ingredients you have (the "2 to buy" summary)
 *  - 7.1 dietary badges (Vegetarian · GF)
 *
 * Mock + standalone. Each ingredient is a [DetailIngredient]; its [owned] flag
 * decides whether it shows a green check (in pantry) or an "add to list" cue.
 */

/** One ingredient line on the detail screen. */
data class DetailIngredient(
    val name: String,
    val amount: String,   // e.g. "3 large"; blank when a quantity isn't specified
    val owned: Boolean    // true = already in pantry, false = needs buying
)

/** A recipe step. */
data class RecipeStep(val number: Int, val text: String)

/** Full mock recipe used by the preview. */
data class RecipeDetail(
    val title: String,
    val cookMinutes: Int,
    val serves: Int,
    val dietTags: List<String>,
    val accent: Color,
    val ingredients: List<DetailIngredient>,
    val steps: List<RecipeStep>
)

private val sampleDetail = RecipeDetail(
    title = "Spinach & Feta Omelette",
    cookMinutes = 18,
    serves = 2,
    dietTags = listOf("Vegetarian", "GF"),
    accent = Color(0xFF4C6B3C),
    ingredients = listOf(
        DetailIngredient("Eggs", "3 large", owned = true),
        DetailIngredient("Baby spinach", "1 cup", owned = true),
        DetailIngredient("Feta cheese", "¼ cup", owned = true),
        DetailIngredient("Fresh dill", "", owned = false),
        DetailIngredient("Cherry tomatoes", "", owned = false),
    ),
    steps = listOf(
        RecipeStep(1, "Whisk eggs with a pinch of salt. Wilt the spinach in a buttered pan over medium heat."),
        RecipeStep(2, "Pour in the eggs, tilt to coat, and cook until just set at the edges."),
        RecipeStep(3, "Scatter feta and dill over one half, fold, and slide onto a plate."),
    )
)

@Composable
fun RecipeDetailScreen(
    recipe: RecipeDetail = sampleDetail,
    onBack: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onAddMissingToList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val toBuy = recipe.ingredients.count { !it.owned }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // --- Hero image with back + bookmark overlaid ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(recipe.accent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OverlayIcon(Icons.Filled.ArrowBack, "Back", onBack)
                    OverlayIcon(Icons.Outlined.BookmarkBorder, "Save recipe", onBookmark)
                }
            }
        }

        // --- Title + meta ---
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(Icons.Filled.Schedule, "${recipe.cookMinutes} min")
                    Spacer(Modifier.size(16.dp))
                    MetaChip(Icons.Filled.Group, "Serves ${recipe.serves}")
                    Spacer(Modifier.size(16.dp))
                    Text(
                        recipe.dietTags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Ingredients header with "to buy" summary ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (toBuy > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "$toBuy to buy",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // --- Ingredient rows ---
        recipe.ingredients.forEach { ingredient ->
            item { IngredientRow(ingredient) }
        }

        // --- One-tap "add missing to grocery list" (req 3.2) ---
        if (toBuy > 0) {
            item {
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = onAddMissingToList,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AddShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Add $toBuy missing to grocery list",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // --- Steps ---
        item {
            Text(
                "Steps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        recipe.steps.forEach { step ->
            item { StepRow(step) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Round translucent icon used over the hero image. */
@Composable
private fun OverlayIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x55FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
    onClick
}

/** Small icon + label used in the meta row (time / servings). */
@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One ingredient row. Owned ingredients get a filled green check; ones you need
 * get an empty circle plus a "Need" tag on the right (req 3.1 — surfaces exactly
 * what's missing).
 */
@Composable
private fun IngredientRow(ingredient: DetailIngredient) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ingredient.owned) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (ingredient.owned) "In pantry" else "Missing",
            tint = if (ingredient.owned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        Text(
            ingredient.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (ingredient.amount.isNotBlank()) {
            Text(
                ingredient.amount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!ingredient.owned) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    "Need",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** One numbered step: a green circle with the number, then the instruction. */
@Composable
private fun StepRow(step: RecipeStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                step.number.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            step.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ---- Android Studio preview ----------------------------------------------
@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun RecipeDetailScreenPreview() {
    PantryPartyTheme {
        RecipeDetailScreen()
    }
}
