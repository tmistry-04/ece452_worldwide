package com.example.pantryparty

import com.example.pantryparty.network.AnalyzedInstruction
import com.example.pantryparty.network.InstructionStep
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.RecipeInstructions
import com.example.pantryparty.recipe.RecipeSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeInstructionsTest {

    private fun info(
        analyzed: List<AnalyzedInstruction> = emptyList(),
        instructions: String? = null
    ) = RecipeInformation(
        id = 1, title = "Test",
        analyzedInstructions = analyzed,
        instructions = instructions
    )

    private fun group(name: String = "", vararg steps: String) =
        AnalyzedInstruction(
            name = name,
            steps = steps.mapIndexed { i, s -> InstructionStep(number = i + 1, step = s) }
        )

    @Test
    fun analyzedInstructions_winOverThePlainString() {
        val steps = RecipeInstructions.of(
            info(analyzed = listOf(group("", "Peel it.")), instructions = "<p>Ignore me.</p>")
        )
        val structured = steps as RecipeSteps.Structured
        assertEquals("Peel it.", structured.groups.single().steps.single().text)
    }

    @Test
    fun analyzedInstructionsWithNoSteps_fallsThroughToThePlainString() {
        // The single most common broken shape from the API: a non-empty list
        // holding a group with zero steps. Branching on isEmpty() would render an
        // empty Steps section and never reach the fallback.
        val steps = RecipeInstructions.of(
            info(
                analyzed = listOf(AnalyzedInstruction(name = "", steps = emptyList())),
                instructions = "<p>Mash the bananas.</p>"
            )
        )
        assertEquals(listOf("Mash the bananas."), (steps as RecipeSteps.Plain).paragraphs)
    }

    @Test
    fun blankStepText_isDroppedAndCanTriggerTheFallback() {
        val steps = RecipeInstructions.of(
            info(analyzed = listOf(group("", "   ", "")), instructions = "Just do it.")
        )
        assertEquals(listOf("Just do it."), (steps as RecipeSteps.Plain).paragraphs)
    }

    @Test
    fun plainInstructions_areSplitIntoSteps() {
        val steps = RecipeInstructions.of(
            info(instructions = "<ol><li>Boil water.</li><li>Add pasta.</li></ol>")
        )
        assertEquals(listOf("Boil water.", "Add pasta."), (steps as RecipeSteps.Plain).paragraphs)
    }

    @Test
    fun neitherSource_yieldsNone() {
        assertTrue(RecipeInstructions.of(info()) is RecipeSteps.None)
        assertTrue(RecipeInstructions.of(info(instructions = "   ")) is RecipeSteps.None)
    }

    @Test
    fun namedGroups_keepTheirNamesAndPerGroupNumbering() {
        val steps = RecipeInstructions.of(
            info(analyzed = listOf(group("Dough", "Mix.", "Knead."), group("Filling", "Chop.")))
        )
        val groups = (steps as RecipeSteps.Structured).groups
        assertEquals(listOf("Dough", "Filling"), groups.map { it.name })
        assertEquals(listOf(1, 2), groups[0].steps.map { it.number })
        assertEquals(listOf(1), groups[1].steps.map { it.number })
    }

    /**
     * End-to-end for the reported bug: recipe 715394's analyzed step 1 is truncated,
     * and its own `instructions` field holds the number the parser dropped.
     */
    @Test
    fun truncatedStepNumbers_areRepairedFromThePlainInstructions() {
        val steps = RecipeInstructions.of(
            info(
                analyzed = listOf(group("", "Heat oven to 37", "Prepare a 9 x 13 baking dish.")),
                instructions = "<p>Heat oven to 375. Prepare a 9 x 13 baking dish.</p>"
            )
        )
        val texts = (steps as RecipeSteps.Structured).groups.single().steps.map { it.text }
        assertEquals(listOf("Heat oven to 375", "Prepare a 9 x 13 baking dish."), texts)
    }

    @Test
    fun stepTextIsHtmlStrippedBeforeItReachesTheScreen() {
        val steps = RecipeInstructions.of(info(analyzed = listOf(group("", "<b>Mash</b> the &amp; bananas."))))
        assertEquals(
            "Mash the & bananas.",
            (steps as RecipeSteps.Structured).groups.single().steps.single().text
        )
    }

    @Test
    fun emptyGroupsAreDropped_butPopulatedOnesSurvive() {
        val steps = RecipeInstructions.of(
            info(analyzed = listOf(AnalyzedInstruction("Prep", emptyList()), group("Cook", "Bake.")))
        )
        val groups = (steps as RecipeSteps.Structured).groups
        assertEquals(listOf("Cook"), groups.map { it.name })
    }
}
