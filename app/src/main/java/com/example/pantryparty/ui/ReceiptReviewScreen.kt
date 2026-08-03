package com.example.pantryparty.ui

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pantryparty.ui.theme.PantryPartyTheme

/**
 * Screen 4 — Receipt Review (confirm before adding).
 *
 * Covers requirements 4.2 (review/confirm before adding) and 4.3 (manually
 * correct misread items). Requirement 11.1 says: if a field can't be parsed,
 * leave it blank and show an inline warning rather than guessing — that's the
 * "No match found" card at the top.
 *
 * Data model: [ReviewItem] below is a small mock class. It mirrors the fields
 * you'll eventually get from Textract, so swapping in real data later is a
 * one-for-one replacement. Each item has a [status] that drives its look.
 */

/** One scanned line the user is reviewing before it's added to the pantry. */
data class ReviewItem(
    val displayName: String,
    val rawText: String,            // exactly what the OCR read, shown as a sub-label
    val quantity: String,           // e.g. "1 bag" — blank if unparsed
    val expiryNote: String,         // e.g. "Exp 2 days" — advisory only (req 6.2)
    val status: ReviewStatus
)

/** Drives the row's appearance: confirmed, needs a manual match, or a duplicate. */
enum class ReviewStatus { MATCHED, NEEDS_MATCH, ALREADY_IN_PANTRY }

/** Hard-coded sample so the screen renders without a scan. */
private val sampleReviewItems = listOf(
    ReviewItem("", "CHKN BRST 2LB", "", "", ReviewStatus.NEEDS_MATCH),
    ReviewItem("Baby spinach", "BABY SPINACH 5OZ", "1 bag", "Exp 2 days", ReviewStatus.MATCHED),
    ReviewItem("Whole milk", "WHOLE MILK GAL", "1 gal", "Exp 5 days", ReviewStatus.MATCHED),
    ReviewItem("Greek yogurt", "GRK YOGURT 32Z", "2 cups", "Exp today", ReviewStatus.MATCHED),
    ReviewItem("Strawberries", "STRAWBERRY 1LB", "1 lb", "Exp 3 days", ReviewStatus.MATCHED),
    ReviewItem("Eggs", "EGGS LG DOZ", "1 dozen", "already in pantry", ReviewStatus.ALREADY_IN_PANTRY),
)

@Composable
fun ReceiptReviewScreen(
    items: List<ReviewItem> = sampleReviewItems,
    storeName: String = "Greenfield Mkt",
    scanDate: String = "Aug 2",
    onBack: () -> Unit = {},
    onFixItem: (ReviewItem) -> Unit = {},
    onDeleteItem: (ReviewItem) -> Unit = {},
    onAddAll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // "Ready to add" = everything that isn't a duplicate and has a match.
    val addableCount = items.count { it.status == ReviewStatus.MATCHED }
    val needsMatchCount = items.count { it.status == ReviewStatus.NEEDS_MATCH }

    Column(modifier = modifier.fillMaxSize()) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    "Review ${items.size} items",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$storeName · $scanDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Inline warning banner (req 11.1) — only if something needs a match.
            if (needsMatchCount > 0) {
                item { NeedsMatchBanner(needsMatchCount) }
            }

            items(items) { item ->
                when (item.status) {
                    ReviewStatus.NEEDS_MATCH -> NoMatchCard(item, onFix = { onFixItem(item) })
                    else -> ReviewRow(item, onDelete = { onDeleteItem(item) })
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // --- Bottom bar: delete-all · add-to-pantry ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Discard scan")
            }
            Button(
                onClick = onAddAll,
                enabled = needsMatchCount == 0 && addableCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add $addableCount to pantry")
            }
        }
    }
}

/** Amber banner telling the user how many items still need a manual match. */
@Composable
private fun NeedsMatchBanner(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.size(10.dp))
            Text(
                if (count == 1) "1 item needs a match. Tap to fix before adding."
                else "$count items need a match. Tap to fix before adding.",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The "couldn't read this" card (req 11.1 / 4.3): shows the raw OCR text, a
 * "Fix" button, and a stand-in search field so the user can pick the right
 * ingredient from the food database. Fields are left blank, never guessed.
 */
@Composable
private fun NoMatchCard(item: ReviewItem, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.rawText, fontWeight = FontWeight.SemiBold)
                    Text(
                        "No match found",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onFix) { Text("Fix") }
            }
            Spacer(Modifier.height(10.dp))
            // Stand-in for the food-database search field (wired later to Spoonacular).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "Search the food database",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** A confirmed (or duplicate) scanned item row. */
@Composable
private fun ReviewRow(item: ReviewItem, onDelete: () -> Unit) {
    val isDuplicate = item.status == ReviewStatus.ALREADY_IN_PANTRY
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDuplicate)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isDuplicate) Icons.Outlined.RadioButtonUnchecked else Icons.Filled.CheckCircle,
                contentDescription = if (isDuplicate) "Skipped" else "Confirmed",
                tint = if (isDuplicate)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (item.quantity.isNotBlank()) {
                    Text(item.quantity, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    item.expiryNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.expiryNote.contains("today"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isDuplicate) {
                Spacer(Modifier.size(8.dp))
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove ${item.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---- Android Studio preview ----------------------------------------------
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ReceiptReviewScreenPreview() {
    PantryPartyTheme {
        ReceiptReviewScreen()
    }
}
