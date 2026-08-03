package com.example.pantryparty.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.viewmodel.BuyDraft
import com.example.pantryparty.viewmodel.ItemEditorState
import com.example.pantryparty.viewmodel.TransactionDraft
import com.example.pantryparty.viewmodel.UseDraft
import com.example.pantryparty.viewmodel.UseKind
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/**
 * A tappable date field for dialogs: label above the formatted value, a
 * calendar icon, and (for nullable dates) a clear button. Tapping opens the
 * Material date picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    placeholder: String = "None"
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedCard(onClick = { showPicker = true }, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value?.let(::formatDate) ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                )
            }
            if (clearable && value != null) {
                IconButton(onClick = { onChange(null) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear $label",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).toEpochDay() * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(LocalDate.ofEpochDay(it / MILLIS_PER_DAY))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Digits-only text field used for every amount entry in the pantry dialogs. */
@Composable
internal fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------------
// Buy (swipe right)
// ---------------------------------------------------------------------------

/** Records a purchase: amount, purchase date (defaults to today), optional expiry. */
@Composable
fun BuyDialog(
    draft: BuyDraft,
    onAmountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onExpiryChange: (LocalDate?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
        title = { Text("Buy ${titleCase(draft.item.name)}") },
        text = {
            Column {
                AmountField(
                    value = draft.amount,
                    onChange = onAmountChange,
                    label = "Amount (${draft.item.unit})"
                )
                Spacer(Modifier.height(10.dp))
                DateField(
                    label = "Bought on",
                    value = draft.dateBought,
                    onChange = { it?.let(onDateChange) }
                )
                Spacer(Modifier.height(8.dp))
                DateField(
                    label = "Expiry (optional)",
                    value = draft.expiry,
                    onChange = onExpiryChange,
                    clearable = true,
                    placeholder = "No expiry"
                )
                draft.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = draft.canConfirm) { Text("Add purchase") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------
// Use / toss (swipe left)
// ---------------------------------------------------------------------------

/**
 * Records consumption: used vs. thrown away, an amount, and which purchase it
 * came from (oldest open lot preselected).
 */
@Composable
fun UseDialog(
    draft: UseDraft,
    onAmountChange: (String) -> Unit,
    onSelectLot: (Long) -> Unit,
    onKindChange: (UseKind) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val nothingInStock = draft.lots.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Restaurant, contentDescription = null) },
        title = { Text("Use ${titleCase(draft.item.name)}") },
        text = {
            if (nothingInStock) {
                Text("Nothing in stock — swipe right on the item to record a purchase first.")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = draft.kind == UseKind.USED,
                            onClick = { onKindChange(UseKind.USED) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Used") }
                        SegmentedButton(
                            selected = draft.kind == UseKind.TOSSED,
                            onClick = { onKindChange(UseKind.TOSSED) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Threw away") }
                    }
                    Spacer(Modifier.height(12.dp))

                    val remaining = draft.selectedLot?.remaining ?: 0
                    AmountField(
                        value = draft.amount,
                        onChange = onAmountChange,
                        label = "Amount (${draft.item.unit})",
                        supportingText = "$remaining left in the selected purchase",
                        isError = draft.quantity > remaining
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("From purchase", style = MaterialTheme.typography.labelLarge)
                    draft.lots.forEachIndexed { index, lot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectLot(lot.id) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = lot.id == draft.selectedLotId,
                                onClick = { onSelectLot(lot.id) }
                            )
                            Column {
                                Text(
                                    buildString {
                                        append("Bought ${formatDay(lot.date)} · ${lot.remaining} left")
                                        if (index == 0) append("  (oldest)")
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                lot.expiry?.let {
                                    Text(
                                        "Expires ${formatDay(it)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (nothingInStock) {
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                Button(onClick = onConfirm, enabled = draft.canConfirm) {
                    Text(if (draft.kind == UseKind.USED) "Record use" else "Record toss")
                }
            }
        },
        dismissButton = if (nothingInStock) null else {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        }
    )
}

// ---------------------------------------------------------------------------
// Transaction editor (details modal)
// ---------------------------------------------------------------------------

/**
 * Manual correction of one ledger row. Illegal combinations (used + tossed
 * exceeding bought, expiry before purchase, …) disable Save and explain why.
 */
@Composable
fun TransactionEditDialog(
    draft: TransactionDraft,
    onDateChange: (LocalDate) -> Unit,
    onBoughtChange: (String) -> Unit,
    onUsedChange: (String) -> Unit,
    onThrownChange: (String) -> Unit,
    onExpiryChange: (LocalDate?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit purchase") },
        text = {
            Column {
                DateField(
                    label = "Bought on",
                    value = draft.date,
                    onChange = { it?.let(onDateChange) }
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField(
                        value = draft.bought,
                        onChange = onBoughtChange,
                        label = "Bought",
                        modifier = Modifier.weight(1f)
                    )
                    AmountField(
                        value = draft.used,
                        onChange = onUsedChange,
                        label = "Used",
                        modifier = Modifier.weight(1f)
                    )
                    AmountField(
                        value = draft.thrown,
                        onChange = onThrownChange,
                        label = "Tossed",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                DateField(
                    label = "Expiry",
                    value = draft.expiry,
                    onChange = onExpiryChange,
                    clearable = true,
                    placeholder = "No expiry"
                )
                draft.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = draft.canConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------
// Item editor (edit mode: add / modify)
// ---------------------------------------------------------------------------

/**
 * Add flow: search Spoonacular → pick a suggestion → details step. Modify flow
 * jumps straight to the details step. Details = name, desired amount, and a
 * unit picked from chips (Spoonacular's possibleUnits when adding, a common
 * set when modifying) or typed as a custom unit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditorDialog(
    state: ItemEditorState,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (IngredientAutocomplete) -> Unit,
    onNameChange: (String) -> Unit,
    onDesiredAmountChange: (String) -> Unit,
    onSelectUnit: (String) -> Unit,
    onSelectCustomUnit: () -> Unit,
    onCustomUnitChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val adding = state.editing == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) "Add item" else "Edit item") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (state.searching) {
                    EditorSearchStep(state, onQueryChange, onSelectSuggestion)
                } else {
                    EditorDetailsStep(
                        state = state,
                        onNameChange = onNameChange,
                        onDesiredAmountChange = onDesiredAmountChange,
                        onSelectUnit = onSelectUnit,
                        onSelectCustomUnit = onSelectCustomUnit,
                        onCustomUnitChange = onCustomUnitChange
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (!state.searching) {
                Button(onClick = onSave, enabled = state.canSave) {
                    Text(if (adding) "Add to pantry" else "Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Step 1 of adding: autocomplete search with thumbnail + aisle suggestions. */
@Composable
private fun EditorSearchStep(
    state: ItemEditorState,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (IngredientAutocomplete) -> Unit
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        label = { Text("Search ingredients… e.g. apple") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (state.loading) {
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
    state.suggestions.forEach { suggestion ->
        IngredientSuggestionRow(suggestion, onClick = { onSelectSuggestion(suggestion) })
    }
}

/**
 * One tappable autocomplete result: thumbnail, name, aisle. Shared by the item editor
 * and the receipt-scan review screen so a suggestion looks the same wherever it's picked.
 */
@Composable
internal fun IngredientSuggestionRow(
    suggestion: IngredientAutocomplete,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkImage(
            url = ingredientImageUrl(suggestion.image),
            contentDescription = suggestion.name,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titleCase(suggestion.name))
            suggestion.aisle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Step 2 of adding, and the whole modify flow: name, desired amount, unit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorDetailsStep(
    state: ItemEditorState,
    onNameChange: (String) -> Unit,
    onDesiredAmountChange: (String) -> Unit,
    onSelectUnit: (String) -> Unit,
    onSelectCustomUnit: () -> Unit,
    onCustomUnitChange: (String) -> Unit
) {
    val image = state.selected?.image ?: state.editing?.imageUrl
    val aisle = state.selected?.aisle ?: state.editing?.aisle

    Row(verticalAlignment = Alignment.CenterVertically) {
        NetworkImage(
            url = ingredientImageUrl(image),
            contentDescription = state.name,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                titleCase(state.name.ifBlank { "New item" }),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            aisle?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))

    AmountField(
        value = state.desiredAmount,
        onChange = onDesiredAmountChange,
        label = "Desired amount",
        supportingText = "How much you want to keep stocked"
    )
    Spacer(Modifier.height(10.dp))

    Text("Unit", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.unitOptions.forEach { unit ->
            FilterChip(
                selected = !state.customUnit && state.unit == unit,
                onClick = { onSelectUnit(unit) },
                label = { Text(unit) }
            )
        }
        FilterChip(
            selected = state.customUnit,
            onClick = onSelectCustomUnit,
            label = { Text("Custom…") }
        )
    }
    if (state.customUnit) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.customUnitText,
            onValueChange = onCustomUnitChange,
            label = { Text("Custom unit") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
