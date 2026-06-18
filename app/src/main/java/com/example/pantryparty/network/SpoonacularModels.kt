package com.example.pantryparty.network

import com.google.gson.annotations.SerializedName

/** One suggestion from the autocomplete endpoint (metaInformation=true). */
data class IngredientAutocomplete(
    val id: Int,
    val name: String,
    val image: String?,
    val aisle: String?,
    val possibleUnits: List<String> = emptyList()
)

data class IngredientSearchResponse(
    val results: List<IngredientSearchResult>,
    val offset: Int,
    val number: Int,
    val totalResults: Int
)

data class IngredientSearchResult(
    val id: Int,
    val name: String,
    val image: String?
)

data class IngredientInfo(
    val id: Int,
    val name: String,
    val image: String?,
    val aisle: String?,
    @SerializedName("categoryPath") val categoryPath: List<String>?,
    val nutrition: NutritionInfo?,
    val estimatedCost: EstimatedCost?
)

data class NutritionInfo(
    val nutrients: List<Nutrient>?,
    val caloricBreakdown: CaloricBreakdown?,
    val weightPerServing: WeightPerServing?
)

data class Nutrient(
    val name: String,
    val amount: Double,
    val unit: String,
    @SerializedName("percentOfDailyNeeds") val percentOfDailyNeeds: Double?
)

data class CaloricBreakdown(
    val percentProtein: Double,
    val percentFat: Double,
    val percentCarbs: Double
)

data class WeightPerServing(
    val amount: Double,
    val unit: String
)

data class EstimatedCost(
    val value: Double,
    val unit: String
)
