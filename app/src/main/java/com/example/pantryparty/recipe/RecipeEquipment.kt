package com.example.pantryparty.recipe

import com.example.pantryparty.network.RecipeInformation

/**
 * The pots, pans and appliances a recipe's steps call for.
 *
 * Collected from data the app was already paying for: every `analyzedInstructions`
 * step carries an `equipment` list that nothing rendered. Zero extra API cost.
 */
object RecipeEquipment {

    /**
     * Every distinct piece of equipment across every step, in the order the recipe
     * first reaches for it, title-cased for display.
     *
     * Deduplicated case-insensitively on `name`: the same oven turns up in six
     * consecutive steps and Spoonacular is not consistent about capitalising it.
     * First-seen order rather than alphabetical, because that tracks the order
     * you'd actually get things out.
     *
     * Reads `name` rather than `localizedName` — the two agree for the default
     * locale, and `name` is the one that is reliably English.
     */
    fun of(info: RecipeInformation): List<String> {
        val seen = LinkedHashMap<String, String>()
        info.analyzedInstructions
            .asSequence()
            .flatMap { it.steps.asSequence() }
            .flatMap { it.equipment.asSequence() }
            .forEach { entity ->
                val display = titleCaseWords(entity.name) ?: return@forEach
                seen.putIfAbsent(display.lowercase(), display)
            }
        return seen.values.toList()
    }
}
