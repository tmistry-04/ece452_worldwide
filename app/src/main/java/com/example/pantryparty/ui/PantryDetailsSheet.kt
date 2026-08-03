package com.example.pantryparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pantryparty.data.PantryTransaction
import com.example.pantryparty.pantry.PantryMath
import com.example.pantryparty.viewmodel.ItemDetailUi
import java.time.LocalDate

/**
 * Tap-a-row modal: the item's aisle and waste score, expiry warnings, and its
 * full transaction history with per-row edit/delete plus clear-all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryDetailsSheet(
    detail: ItemDetailUi,
    onEditTransaction: (PantryTransaction) -> Unit,
    onDeleteTransaction: (PantryTransaction) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmClear by remember { mutableStateOf(false) }
    val todayEpoch = remember { LocalDate.now().toEpochDay() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            item { SheetHeader(detail) }
            item {
                Spacer(Modifier.height(16.dp))
                StatRow(detail)
                Spacer(Modifier.height(8.dp))
                ScoreCaption(detail.row.score)
            }
            if (detail.row.expiry.hasWarning) {
                item {
                    Spacer(Modifier.height(12.dp))
                    ExpiryBanner(detail)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = detail.transactions.isNotEmpty()
                    ) { Text("Clear all") }
                }
            }
            if (detail.transactions.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "No purchases yet — swipe right on the item to record one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(detail.transactions, key = { it.id }) { txn ->
                    TransactionRow(
                        txn = txn,
                        todayEpoch = todayEpoch,
                        onEdit = { onEditTransaction(txn) },
                        onDelete = { onDeleteTransaction(txn) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = {
                Text(
                    "Every transaction for ${titleCase(detail.row.item.name)} will be deleted " +
                        "and its stock and score will reset."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

/** Thumbnail, name, and the catalog details (aisle, unit target). */
@Composable
private fun SheetHeader(detail: ItemDetailUi) {
    val item = detail.row.item
    Row(verticalAlignment = Alignment.CenterVertically) {
        NetworkImage(
            url = ingredientImageUrl(item.imageUrl),
            contentDescription = item.name,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                titleCase(item.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            item.aisle?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Aisle: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Three tonal tiles: current stock, the target, and the waste score. */
@Composable
private fun StatRow(detail: ItemDetailUi) {
    val row = detail.row
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            label = "In stock",
            value = "${row.stock}",
            caption = row.item.unit,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            label = "Want",
            value = "${row.item.desiredAmount}",
            caption = row.item.unit,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            label = "Score",
            value = row.score?.toString() ?: "—",
            caption = if (row.score == null) "no data" else "of 100",
            valueColor = scoreColor(row.score),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Green when little is wasted, amber when some, red when most goes to the bin. */
@Composable
private fun scoreColor(score: Int?): Color = when {
    score == null -> MaterialTheme.colorScheme.onSurface
    score >= 90 -> MaterialTheme.colorScheme.primary
    score >= 60 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun ScoreCaption(score: Int?) {
    Text(
        if (score == null)
            "The score appears once some of this item has been used or thrown away."
        else
            "Score = share of finished stock that was used, not thrown away. Tossing food lowers it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** One banner summarising expired / soon-to-expire units on hand. */
@Composable
private fun ExpiryBanner(detail: ItemDetailUi) {
    val expiry = detail.row.expiry
    val expired = expiry.expiredCount > 0
    val container = if (expired) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (expired) MaterialTheme.colorScheme.onErrorContainer
                  else MaterialTheme.colorScheme.onTertiaryContainer
    val message = buildList {
        if (expiry.expiredCount > 0) add("${expiry.expiredCount} ${detail.row.item.unit} expired")
        if (expiry.expiringSoonCount > 0) {
            val days = expiry.daysToNext ?: 0L
            add(
                if (days == 0L) "${expiry.expiringSoonCount} expiring today"
                else "${expiry.expiringSoonCount} expiring in ${days}d"
            )
        }
    }.joinToString(" · ")

    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = content)
            Spacer(Modifier.width(10.dp))
            Text(message, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One ledger row: what was bought when, what happened to it, and its expiry. */
@Composable
private fun TransactionRow(
    txn: PantryTransaction,
    todayEpoch: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Bought ${txn.amountBought} on ${formatDay(txn.date)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Used ${txn.amountUsed} · Tossed ${txn.amountThrown} · ${txn.remaining} left",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            txn.expiry?.let { expiry ->
                val daysLeft = expiry - todayEpoch
                val stillStocked = txn.remaining > 0
                Text(
                    when {
                        daysLeft < 0 -> "Expired ${formatDay(expiry)}"
                        else -> "Expires ${formatDay(expiry)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !stillStocked -> MaterialTheme.colorScheme.onSurfaceVariant
                        daysLeft < 0 -> MaterialTheme.colorScheme.error
                        daysLeft <= PantryMath.EXPIRY_WARN_DAYS -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit this purchase",
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete this purchase",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
