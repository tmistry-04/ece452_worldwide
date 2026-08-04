package com.example.pantryparty.recipe

import com.example.pantryparty.network.InstructionStep
import com.example.pantryparty.network.RecipeInformation

/** One named group of steps ("Dough", "Filling"); [name] is blank for the usual single group. */
data class StepGroup(val name: String, val steps: List<InstructionStep>)

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
        val groups = info.analyzedInstructions
            .map { group ->
                StepGroup(
                    name = group.name.trim(),
                    steps = group.steps.filter { stripHtml(it.step).isNotBlank() }
                )
            }
            .filter { it.steps.isNotEmpty() }
        if (groups.isNotEmpty()) return RecipeSteps.Structured(groups)

        val paragraphs = htmlParagraphs(info.instructions)
        if (paragraphs.isNotEmpty()) return RecipeSteps.Plain(paragraphs)

        return RecipeSteps.None
    }
}
