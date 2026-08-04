package com.example.pantryparty.recipe

import com.example.pantryparty.network.RecipeInformation

/**
 * One rendered step: the number the API gave it, and its display text with the HTML
 * already stripped and [repairTruncatedNumber] already applied.
 *
 * Deliberately not the API's `InstructionStep`: that type's `step` field holds text
 * the analyzer truncated, and a caller reading it directly would quietly bypass the
 * repair. Carrying only the finished text makes that impossible.
 */
data class RecipeStep(val number: Int, val text: String)

/** One named group of steps ("Dough", "Filling"); [name] is blank for the usual single group. */
data class StepGroup(val name: String, val steps: List<RecipeStep>)

/** What the detail page can actually show for "how do I make this?". */
sealed interface RecipeSteps {
    /** Real per-step data — numbered, optionally split into named groups. */
    data class Structured(val groups: List<StepGroup>) : RecipeSteps

    /** Only the free-text `instructions` blob, split into paragraphs. */
    data class Plain(val paragraphs: List<String>) : RecipeSteps

    /** Nothing usable; the "View original recipe" link is the only way through. */
    data object None : RecipeSteps
}

object RecipeInstructions {

    /**
     * Picks the best available representation: analyzed steps, else the plain
     * instructions blob, else nothing.
     *
     * The important case is the middle one. Spoonacular very often returns
     * `"analyzedInstructions": [{"name": "", "steps": []}]` — a *non-empty* list
     * holding a group with *no* steps. Branching on `analyzedInstructions.isEmpty()`
     * would render an empty Steps section and never reach the fallback, so the
     * check below is on the flattened count of non-blank steps.
     */
    fun of(info: RecipeInformation): RecipeSteps {
        // Stripped once and shared: the repair searches the whole blob per step.
        val plain = stripHtml(info.instructions)

        val groups = info.analyzedInstructions
            .map { group ->
                StepGroup(
                    name = group.name.trim(),
                    steps = group.steps.mapNotNull { raw ->
                        val text = repairTruncatedNumber(stripHtml(raw.step), plain)
                        text.takeIf { it.isNotBlank() }?.let { RecipeStep(raw.number, it) }
                    }
                )
            }
            .filter { it.steps.isNotEmpty() }
        if (groups.isNotEmpty()) return RecipeSteps.Structured(groups)

        val paragraphs = htmlParagraphs(info.instructions)
        if (paragraphs.isNotEmpty()) return RecipeSteps.Plain(paragraphs)

        return RecipeSteps.None
    }
}
