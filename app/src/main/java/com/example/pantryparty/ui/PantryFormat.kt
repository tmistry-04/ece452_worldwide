package com.example.pantryparty.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Millis in a day; Material's date picker speaks UTC-midnight millis. */
const val MILLIS_PER_DAY = 86_400_000L

private val SAME_YEAR = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val OTHER_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** "Aug 2" within the current year, "Aug 2, 2025" otherwise. */
fun formatDate(date: LocalDate): String =
    if (date.year == LocalDate.now().year) SAME_YEAR.format(date) else OTHER_YEAR.format(date)

/** Same, for the epoch-day longs the ledger stores. */
fun formatDay(epochDay: Long): String = formatDate(LocalDate.ofEpochDay(epochDay))

/** "apple" -> "Apple"; ingredient names come lowercase from Spoonacular. */
fun titleCase(name: String): String = name.replaceFirstChar { it.uppercase() }

/** "2.0 cup flour" -> "2 cup flour"; drops trailing .0 for readability. */
fun formatAmount(amount: Double, unit: String, name: String): String {
    val amountText = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
    return listOf(amountText, unit, name).filter { it.isNotBlank() }.joinToString(" ")
}
