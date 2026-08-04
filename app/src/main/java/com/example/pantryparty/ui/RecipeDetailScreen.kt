package com.example.pantryparty.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.DetailIngredientRow
import com.example.pantryparty.recipe.IngredientStatus
import com.example.pantryparty.recipe.RecipeBadges
import com.example.pantryparty.recipe.RecipeDetailRows
import com.example.pantryparty.recipe.RecipeInstructions
import com.example.pantryparty.recipe.RecipeSteps
import com.example.pantryparty.recipe.stripHtml
import com.example.pantryparty.viewmodel.RecipeDetailUi

/**
 * The full recipe: what it is, what it needs, and how to make it.
 *
 * One scrolling page — header, then ingredients, then steps — because a recipe is
 * read top to bottom while cooking, and tabs would hide the ingredient list at
 * exactly the moment you want to glance back at it.
 *
 * Everything here is a pure render of [RecipeDetailUi]; the pantry matching and
 * the instruction fallback both happened in the ViewModel/`recipe` package.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    detail: RecipeDetailUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        // Fixed, so back stays reachable however far you've scrolled.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recipes")
            }
            Text(
                detail.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Absolute URL from Spoonacular — not an ingredient filename.
            NetworkImage(
                url = detail.image,
                contentDescription = detail.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    detail.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                detail.info?.let { info ->
                    FactRow(info)
                    BadgeRows(info)
                }

                if (detail.loading) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
                detail.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                detail.info?.let { info ->
                    Spacer(Modifier.height(20.dp))
                    IngredientsSection(detail.rows)

                    Spacer(Modifier.height(20.dp))
                    StepsSection(info)

                    SourceLink(info) { url -> openUrl(context, url) }
                }
            }

            // Clearance so the last line clears the bottom navigation bar.
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Cook time and yield. Omitted entirely when the API gave us neither. */
@Composable
private fun FactRow(info: RecipeInformation) {
    val minutes = info.readyInMinutes?.takeIf { it > 0 }
    val servings = info.servings?.takeIf { it > 0 }
    if (minutes == null && servings == null) return

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        minutes?.let { FactChip(Icons.Outlined.Schedule, "$it min") }
        servings?.let { FactChip(Icons.Outlined.Group, if (it == 1) "1 serving" else "$it servings") }
    }
}

/**
 * Two rows with different tonal weight: which diets a recipe satisfies reads as a
 * different kind of fact from what sort of dish it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRows(info: RecipeInformation) {
    val diets = remember(info) { RecipeBadges.diets(info) }
    val categories = remember(info) { RecipeBadges.categories(info) }

    if (diets.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            diets.forEach {
                BadgePill(
                    it,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
    if (categories.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach {
                BadgePill(
                    it,
                    // surfaceVariant is TomatoContainer (a warm coral) in this theme;
                    // surfaceContainerHigh is the genuinely neutral surface.
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IngredientsSection(rows: List<DetailIngredientRow>) {
    val have = RecipeDetailRows.haveCount(rows)
    val checked = RecipeDetailRows.checkedCount(rows)
    SectionHeader(
        "Ingredients",
        trailing = if (checked > 0) "$have of $checked in your pantry" else null
    )
    Spacer(Modifier.height(4.dp))
    rows.forEachIndexed { index, row ->
        IngredientRow(row)
        if (index < rows.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun IngredientRow(row: DetailIngredientRow) {
    val (icon, tint) = when (row.status) {
        // secondary is HerbGreen. primary is Tomato *red* in this theme, which
        // would read as an error on a "you have this" row.
        IngredientStatus.HAVE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.secondary
        IngredientStatus.SHORT -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.tertiary
        IngredientStatus.MISSING -> Icons.Outlined.Cancel to MaterialTheme.colorScheme.error
        IngredientStatus.STAPLE -> Icons.Outlined.Kitchen to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatAmount(row.required.amount, row.required.unit, titleCase(row.required.name)),
                style = MaterialTheme.typography.bodyMedium
            )
            val caption = when (row.status) {
                // Same wording as the card's "Short on:" list, so the two agree.
                IngredientStatus.SHORT -> "have ${row.haveQuantity} ${row.haveUnit}"
                IngredientStatus.STAPLE -> "assumed on hand"
                else -> null
            }
            caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val label = when (row.status) {
            IngredientStatus.SHORT -> "SHORT"
            IngredientStatus.MISSING -> "MISSING"
            else -> null
        }
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}

@Composable
private fun StepsSection(info: RecipeInformation) {
    SectionHeader("Steps")
    Spacer(Modifier.height(8.dp))

    when (val steps = remember(info) { RecipeInstructions.of(info) }) {
        is RecipeSteps.Structured -> steps.groups.forEach { group ->
            if (group.name.isNotBlank()) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            // The API numbers steps per group, restarting at 1 — which is what a
            // reader expects once the group names are on screen.
            group.steps.forEach { StepRow(it.number, stripHtml(it.step)) }
        }

        is RecipeSteps.Plain ->
            if (steps.paragraphs.size == 1) {
                // One blob of prose isn't a numbered list; numbering it would lie.
                Text(steps.paragraphs.single(), style = MaterialTheme.typography.bodyMedium)
            } else {
                steps.paragraphs.forEachIndexed { i, p -> StepRow(i + 1, p) }
            }

        RecipeSteps.None -> Text(
            "This recipe's steps aren't available here — open the original below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SourceLink(info: RecipeInformation, onOpen: (String) -> Unit) {
    val url = info.sourceUrl?.takeIf { it.startsWith("http") } ?: return
    Spacer(Modifier.height(20.dp))
    FilledTonalButton(onClick = { onOpen(url) }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("View original recipe")
    }
    info.sourceName?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            "from $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- small shared pieces ----------------------------------------------------

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BadgePill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FactChip(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Hands the recipe's source URL to whatever browser the user has.
 *
 * No `resolveActivity` guard: the manifest declares no `<queries>`, so on modern
 * targets package-visibility filtering would return null even with a browser
 * installed. Launching an http(s) VIEW intent is exempt from that filtering;
 * querying for one is not — so try it and swallow the miss.
 */
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
