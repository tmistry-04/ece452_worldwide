package com.example.pantryparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.viewmodel.ReceiptScanViewModel
import com.example.pantryparty.viewmodel.ScanRowUi
import com.example.pantryparty.viewmodel.ScanState

/**
 * The whole receipt-scan flow: viewfinder, then a review list, then the write.
 *
 * A full-screen [Dialog] rather than a new screen — the viewfinder needs the space, but
 * the project has no navigation library, and a dialog keeps the flow entirely local to
 * the Pantry tab.
 */
@Composable
fun ReceiptScanDialog(dao: PantryDao, onDismiss: () -> Unit) {
    val viewModel: ReceiptScanViewModel = viewModel(factory = ReceiptScanViewModel.factory(dao))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()

    // The pantry list updates reactively, so a landed write just closes the dialog.
    LaunchedEffect(state) {
        if (state is ScanState.Saved) onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScanHeader(
                    title = when (state) {
                        is ScanState.Review -> "Review items"
                        else -> "Scan a receipt"
                    },
                    onDismiss = onDismiss
                )
                HorizontalDivider()

                Box(modifier = Modifier.weight(1f)) {
                    when (val current = state) {
                        ScanState.Camera -> ReceiptCameraStep(
                            onCaptureStarted = viewModel::onCaptureStarted,
                            onLines = viewModel::onLinesRecognized,
                            onFailure = viewModel::onCaptureFailed
                        )

                        ScanState.Processing, is ScanState.Saved ->
                            BusyStep(if (saving) "Adding to your pantry…" else "Reading your receipt…")

                        is ScanState.Failed -> FailedStep(
                            message = current.message,
                            onRetry = viewModel::retry
                        )

                        is ScanState.Review -> ReceiptReviewStep(
                            review = current,
                            saving = saving,
                            onToggleInclude = viewModel::toggleInclude,
                            onQuantityChange = viewModel::setQuantity,
                            onUnitChange = viewModel::setUnit,
                            onOpenSearch = viewModel::openSearch,
                            onCloseSearch = viewModel::closeSearch,
                            onSearchQueryChange = viewModel::onSearchQueryChange,
                            onSelectMatch = viewModel::selectMatch,
                            onConfirm = viewModel::confirmAll,
                            onRescan = viewModel::retry
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BusyStep(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FailedStep(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

// ---------------------------------------------------------------------------
// Review
// ---------------------------------------------------------------------------

/**
 * The confirmation step. Nothing has touched the database yet — receipt shorthand is
 * ambiguous enough that some guesses are always wrong, so every row is editable and
 * unmatched rows start unchecked.
 */
@Composable
private fun ReceiptReviewStep(
    review: ScanState.Review,
    saving: Boolean,
    onToggleInclude: (Int) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onUnitChange: (Int, String) -> Unit,
    onOpenSearch: (Int) -> Unit,
    onCloseSearch: (Int) -> Unit,
    onSearchQueryChange: (Int, String) -> Unit,
    onSelectMatch: (Int, IngredientAutocomplete) -> Unit,
    onConfirm: () -> Unit,
    onRescan: () -> Unit
) {
    val selected = review.rows.count { it.include && it.canAdd }
    val unmatched = review.rows.count { it.match == null }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item(key = "summary") {
                Text(
                    buildString {
                        append("Found ${review.rows.size} items")
                        if (unmatched > 0) append(" · $unmatched need a match")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            review.warning?.let { warning ->
                item(key = "warning") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            warning,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            items(review.rows, key = { it.key }) { row ->
                ScanRow(
                    row = row,
                    onToggleInclude = { onToggleInclude(row.key) },
                    onQuantityChange = { onQuantityChange(row.key, it) },
                    onUnitChange = { onUnitChange(row.key, it) },
                    onOpenSearch = { onOpenSearch(row.key) },
                    onCloseSearch = { onCloseSearch(row.key) },
                    onSearchQueryChange = { onSearchQueryChange(row.key, it) },
                    onSelectMatch = { onSelectMatch(row.key, it) }
                )
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRescan, enabled = !saving) { Text("Rescan") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onConfirm, enabled = selected > 0 && !saving) {
                Text(if (selected > 0) "Add $selected items" else "Add items")
            }
        }
    }
}

/** One reviewable line: the guess, the text it came from, and the controls to fix it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanRow(
    row: ScanRowUi,
    onToggleInclude: () -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectMatch: (IngredientAutocomplete) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.include,
                    onCheckedChange = { onToggleInclude() },
                    enabled = row.match != null
                )
                NetworkImage(
                    url = ingredientImageUrl(row.match?.image),
                    contentDescription = row.match?.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.match?.let { titleCase(it.name) } ?: "No match found",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (row.match == null) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                    // Always show the printed text: it's the only way to judge the guess.
                    Text(
                        row.raw,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (row.match != null && !row.confident) {
                        Text(
                            "Best guess — worth a check",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                IconButton(onClick = if (row.searchOpen) onCloseSearch else onOpenSearch) {
                    Icon(
                        if (row.searchOpen) Icons.Filled.Close else Icons.Filled.Edit,
                        contentDescription = if (row.searchOpen) "Cancel search"
                                             else "Choose a different ingredient"
                    )
                }
            }

            if (row.searchOpen) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = row.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search ingredients…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (row.loading) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                row.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                row.suggestions.forEach { suggestion ->
                    IngredientSuggestionRow(suggestion, onClick = { onSelectMatch(suggestion) })
                }
            }

            if (row.match != null && row.include && !row.searchOpen) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AmountField(
                        value = row.quantity,
                        onChange = onQuantityChange,
                        label = "Amount",
                        isError = row.amount <= 0,
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.unitOptions.take(MAX_UNIT_CHIPS).forEach { unit ->
                            FilterChip(
                                selected = unit == row.unit,
                                onClick = { onUnitChange(unit) },
                                label = { Text(unit) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Spoonacular can return a dozen units; more than this and the row stops being scannable. */
private const val MAX_UNIT_CHIPS = 5
