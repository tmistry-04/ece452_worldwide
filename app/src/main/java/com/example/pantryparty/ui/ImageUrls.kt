package com.example.pantryparty.ui

/**
 * Turns whatever Spoonacular put in an `image` field into something loadable.
 *
 * These live apart from NetworkImage.kt deliberately: they are pure String
 * functions, and keeping them out of a file full of @Composables and Coil types
 * lets a plain JUnit test call them.
 */

/** Base path for Spoonacular's 100x100 ingredient thumbnails. */
private const val INGREDIENT_IMAGE_BASE = "https://img.spoonacular.com/ingredients_100x100/"

/** Base path for recipe images. */
private const val RECIPE_IMAGE_BASE = "https://img.spoonacular.com/recipes/"

/**
 * Builds a full ingredient image URL from a stored Spoonacular filename
 * (e.g. "apple.jpg"). Already-absolute URLs are returned unchanged; blank/null
 * yields null so the caller can show a placeholder.
 */
fun ingredientImageUrl(fileOrUrl: String?): String? = when {
    fileOrUrl.isNullOrBlank() -> null
    fileOrUrl.startsWith("http") -> fileOrUrl
    else -> INGREDIENT_IMAGE_BASE + fileOrUrl
}

/**
 * Same, for recipe images — and the endpoints genuinely disagree about the format.
 *
 * complexSearch and information return an absolute URL, which passes through
 * untouched. `recipes/{id}/similar` returns a bare filename
 * ("Chicken-Verde-638409.jpg"), which resolves under [RECIPE_IMAGE_BASE] (verified
 * against the live API). Every recipe image goes through here so no caller has to
 * remember which endpoint handed it which form.
 */
fun recipeImageUrl(fileOrUrl: String?): String? = when {
    fileOrUrl.isNullOrBlank() -> null
    fileOrUrl.startsWith("http") -> fileOrUrl
    else -> RECIPE_IMAGE_BASE + fileOrUrl
}
