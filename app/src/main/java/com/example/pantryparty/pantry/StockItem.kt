package com.example.pantryparty.pantry

/**
 * A catalog item with its on-hand stock computed from the transaction ledger —
 * the recipe features' read-only view of the pantry. [id] is the catalog item id.
 */
data class StockItem(
    val id: Long,
    val spoonacularId: Int,
    val name: String,
    val unit: String,
    val quantity: Int
)
