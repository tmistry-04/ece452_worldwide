package com.example.pantryparty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantryparty.data.CatalogItem
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.viewmodel.PantryRowUi
import com.example.pantryparty.viewmodel.PantryViewModel

/**
 * Feature 1 — the pantry. A catalog of items the household wants stocked, with
 * stock/expiry/score derived from the purchase ledger by [PantryViewModel].
 *
 * Outside edit mode each row shows stock vs. desired and expiry warnings;
 * swiping right records a purchase, swiping left records use/toss, tapping
 * opens the details modal. Edit mode switches the rows to rearrange/modify/
 * delete controls and enables adding items.
 */
@Composable
fun PantryScreen(dao: PantryDao) {
    val viewModel: PantryViewModel = viewModel(factory = PantryViewModel.factory(dao))
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val itemEditor by viewModel.itemEditor.collectAsStateWithLifecycle()
    val buyDraft by viewModel.buyDraft.collectAsStateWithLifecycle()
    val useDraft by viewModel.useDraft.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val txnDraft by viewModel.txnDraft.collectAsStateWithLifecycle()
    val staples by viewModel.staples.collectAsStateWithLifecycle()
    val stapleEditor by viewModel.stapleEditor.collectAsStateWithLifecycle()

    // Purely visual confirmation step, so it stays local UI state.
    var confirmDeleteItem by remember { mutableStateOf<CatalogItem?>(null) }

    // The scan flow owns its own ViewModel and dies with the dialog, so whether it's
    // open is just UI state here.
    var scanning by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item(key = "header") {
            PantryHeader(
                editMode = editMode,
                itemCount = rows.size,
                onToggleEditMode = viewModel::toggleEditMode
            )
        }

        // Scanning stocks the pantry, so it belongs with the everyday actions rather
        // than behind edit mode — but edit mode is about arranging the catalog, so it
        // steps out of the way there.
        if (!editMode) {
            item(key = "scan-receipt") {
                FilledTonalButton(
                    onClick = { scanning = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan a receipt")
                }
            }
        }

        if (editMode) {
            item(key = "add-item") {
                FilledTonalButton(
                    onClick = viewModel::openAddItem,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add item")
                }
            }
            item(key = "staples") {
                FilledTonalButton(
                    onClick = viewModel::openStaples,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (staples.isEmpty()) "Always have…"
                        else "Always have (${staples.size})"
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            item(key = "empty") { EmptyPantryHint(editMode) }
        } else {
            itemsIndexed(rows, key = { _, row -> row.item.id }) { index, row ->
                if (editMode) {
                    EditableRow(
                        row = row,
                        isFirst = index == 0,
                        isLast = index == rows.lastIndex,
                        onMoveUp = { viewModel.moveUp(row.item) },
                        onMoveDown = { viewModel.moveDown(row.item) },
                        onEdit = { viewModel.openEditItem(row.item) },
                        onDelete = { confirmDeleteItem = row.item }
                    )
                } else {
                    SwipeablePantryRow(
                        row = row,
                        onBuy = { viewModel.openBuy(row.item) },
                        onUse = { viewModel.openUse(row.item) },
                        onClick = { viewModel.openDetail(row.item) }
                    )
                }
            }
        }
    }

    // ---- dialogs & modals (each shows only while its draft/state is open) ----

    stapleEditor?.let { state ->
        StaplesEditorDialog(
            state = state,
            staples = staples,
            onQueryChange = viewModel::onStapleQueryChange,
            onAdd = viewModel::addStaple,
            onRemove = viewModel::removeStaple,
            onDismiss = viewModel::dismissStaples
        )
    }

    itemEditor?.let { state ->
        ItemEditorDialog(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSelectSuggestion = viewModel::selectSuggestion,
            onNameChange = viewModel::onNameChange,
            onDesiredAmountChange = viewModel::onDesiredAmountChange,
            onSelectUnit = viewModel::selectUnit,
            onSelectCustomUnit = viewModel::selectCustomUnit,
            onCustomUnitChange = viewModel::onCustomUnitChange,
            onSave = viewModel::saveItem,
            onDismiss = viewModel::dismissItemEditor
        )
    }

    buyDraft?.let { draft ->
        BuyDialog(
            draft = draft,
            onAmountChange = viewModel::setBuyAmount,
            onDateChange = viewModel::setBuyDate,
            onExpiryChange = viewModel::setBuyExpiry,
            onConfirm = viewModel::confirmBuy,
            onDismiss = viewModel::dismissBuy
        )
    }

    useDraft?.let { draft ->
        UseDialog(
            draft = draft,
            onAmountChange = viewModel::setUseAmount,
            onSelectLot = viewModel::selectUseLot,
            onKindChange = viewModel::setUseKind,
            onConfirm = viewModel::confirmUse,
            onDismiss = viewModel::dismissUse
        )
    }

    txnDraft?.let { draft ->
        TransactionEditDialog(
            draft = draft,
            onDateChange = viewModel::setTxnDate,
            onBoughtChange = viewModel::setTxnBought,
            onUsedChange = viewModel::setTxnUsed,
            onThrownChange = viewModel::setTxnThrown,
            onExpiryChange = viewModel::setTxnExpiry,
            onConfirm = viewModel::confirmTransactionEdit,
            onDismiss = viewModel::dismissTransactionEditor
        )
    }

    detail?.let { detailUi ->
        PantryDetailsSheet(
            detail = detailUi,
            onEditTransaction = viewModel::openTransactionEditor,
            onDeleteTransaction = viewModel::deleteTransaction,
            onClearHistory = { viewModel.clearHistory(detailUi.row.item) },
            onDismiss = viewModel::closeDetail
        )
    }

    if (scanning) {
        ReceiptScanDialog(dao = dao, onDismiss = { scanning = false })
    }

    confirmDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDeleteItem = null },
            title = { Text("Remove ${titleCase(item.name)}?") },
            text = { Text("The item and its entire purchase history will be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item)
                        confirmDeleteItem = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteItem = null }) { Text("Cancel") }
            }
        )
    }
}

/** Title, a context line, and the edit-mode toggle. */
@Composable
private fun PantryHeader(
    editMode: Boolean,
    itemCount: Int,
    onToggleEditMode: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "My Pantry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    editMode -> "Add, rearrange, or remove items"
                    itemCount == 0 -> "Track what you want to keep stocked"
                    else -> "Swipe right to buy, left to use · tap for details"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (editMode) {
            FilledTonalButton(onClick = onToggleEditMode) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done")
            }
        } else {
            TextButton(onClick = onToggleEditMode) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
        }
    }
}

/** Friendly empty state; the wording steers toward the add flow. */
@Composable
private fun EmptyPantryHint(editMode: Boolean) {
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
                if (editMode) "Tap “Add item” above to start your catalog."
                else "Tap “Edit” to add the items you want to keep stocked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Normal mode: swipeable stock rows
// ---------------------------------------------------------------------------

/**
 * Wraps a row in swipe gestures: right (start→end) opens the buy dialog, left
 * (end→start) the use dialog. The callbacks return false from
 * confirmValueChange, so the row springs back instead of dismissing.
 */
@Composable
private fun SwipeablePantryRow(
    row: PantryRowUi,
    onBuy: () -> Unit,
    onUse: () -> Unit,
    onClick: () -> Unit
) {
    // The state outlives recompositions; read the latest callbacks through
    // rememberUpdatedState so it never fires stale lambdas.
    val currentOnBuy by rememberUpdatedState(onBuy)
    val currentOnUse by rememberUpdatedState(onUse)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentOnBuy()
                SwipeToDismissBoxValue.EndToStart -> currentOnUse()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Never actually dismiss — the row is a control, not a list entry
            // being removed.
            value == SwipeToDismissBoxValue.Settled
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = { SwipeActionBackground(state) },
        content = { PantryRowCard(row = row, onClick = onClick) }
    )
}

/** The reveal behind a swiped row: green-ish "Buy" on the left, "Use" on the right. */
@Composable
private fun SwipeActionBackground(state: SwipeToDismissBoxState) {
    val buying = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    val using = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
    val container = when {
        buying -> MaterialTheme.colorScheme.primaryContainer
        using -> MaterialTheme.colorScheme.tertiaryContainer
        else -> Color.Transparent
    }
    val content = when {
        buying -> MaterialTheme.colorScheme.onPrimaryContainer
        using -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))   // matches the card's shape
            .background(container)
            .padding(horizontal = 24.dp),
        contentAlignment = if (buying) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (buying) Icons.Filled.AddShoppingCart else Icons.Filled.Restaurant,
                contentDescription = null,
                tint = content
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (buying) "Buy" else "Use",
                color = content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * One catalog item outside edit mode: thumbnail, name, a stock-vs-desired
 * progress line, and warning pills for empty stock and expiring lots.
 */
@Composable
private fun PantryRowCard(row: PantryRowUi, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetworkImage(
                url = ingredientImageUrl(row.item.imageUrl),
                contentDescription = row.item.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titleCase(row.item.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                StockProgressLine(row)
                if (row.stock == 0 || row.expiry.hasWarning) {
                    Spacer(Modifier.height(6.dp))
                    WarningPills(row)
                }
            }
        }
    }
}

/** "▬▬▬▭▭  3 / 5 pieces" — how the on-hand stock compares to the target. */
@Composable
private fun StockProgressLine(row: PantryRowUi) {
    val desired = row.item.desiredAmount
    val fraction = if (desired <= 0) 1f else (row.stock.toFloat() / desired).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (row.stock == 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${row.stock} / $desired ${row.item.unit}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Compact status pills: out of stock, expired units, soon-to-expire units. */
@Composable
private fun WarningPills(row: PantryRowUi) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (row.stock == 0) {
            StatusPill(
                text = "Out of stock",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        if (row.expiry.expiredCount > 0) {
            StatusPill(
                text = "${row.expiry.expiredCount} expired",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        if (row.expiry.expiringSoonCount > 0) {
            val days = row.expiry.daysToNext ?: 0L
            StatusPill(
                text = when (days) {
                    0L -> "${row.expiry.expiringSoonCount} expiring today"
                    else -> "${row.expiry.expiringSoonCount} expiring in ${days}d"
                },
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** Small rounded status pill used on pantry rows. */
@Composable
fun StatusPill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Edit mode: rearrange / modify / delete rows
// ---------------------------------------------------------------------------

/** One catalog item in edit mode: reorder arrows, modify, and delete controls. */
@Composable
private fun EditableRow(
    row: PantryRowUi,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetworkImage(
                url = ingredientImageUrl(row.item.imageUrl),
                contentDescription = row.item.name,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titleCase(row.item.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Want ${row.item.desiredAmount} ${row.item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Stacked arrows keep the row compact enough for four controls.
            Column {
                SmallIconButton(
                    icon = Icons.Filled.KeyboardArrowUp,
                    description = "Move ${row.item.name} up",
                    enabled = !isFirst,
                    onClick = onMoveUp
                )
                SmallIconButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    description = "Move ${row.item.name} down",
                    enabled = !isLast,
                    onClick = onMoveDown
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${row.item.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${row.item.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 32dp icon button for tight spots (the stacked reorder arrows). */
@Composable
private fun SmallIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}
