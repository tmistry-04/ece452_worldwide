package com.example.pantryparty

import com.example.pantryparty.network.AnalyzedInstruction
import com.example.pantryparty.network.InstructionStep
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.StepEntity
import com.example.pantryparty.recipe.RecipeEquipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEquipmentTest {

    private fun step(vararg equipment: String) = InstructionStep(
        number = 1,
        step = "do a thing",
        equipment = equipment.map { StepEntity(name = it) }
    )

    private fun info(vararg steps: InstructionStep) = RecipeInformation(
        id = 1, title = "Test",
        analyzedInstructions = listOf(AnalyzedInstruction(name = "", steps = steps.toList()))
    )

    @Test
    fun collectsEquipmentAcrossEveryStep_titleCased() {
        val equipment = RecipeEquipment.of(info(step("oven"), step("baking pan", "aluminum foil")))
        assertEquals(listOf("Oven", "Baking Pan", "Aluminum Foil"), equipment)
    }

    @Test
    fun dedupesCaseInsensitively_keepingFirstSeenOrder() {
        // The same oven shows up in six consecutive steps and the API is not
        // consistent about capitalising it.
        val equipment = RecipeEquipment.of(info(step("oven"), step("bowl"), step("Oven"), step("OVEN")))
        assertEquals(listOf("Oven", "Bowl"), equipment)
    }

    @Test
    fun collectsAcrossNamedGroups() {
        val recipe = RecipeInformation(
            id = 1, title = "Test",
            analyzedInstructions = listOf(
                AnalyzedInstruction("Dough", listOf(step("stand mixer"))),
                AnalyzedInstruction("Filling", listOf(step("sauce pan")))
            )
        )
        assertEquals(listOf("Stand Mixer", "Sauce Pan"), RecipeEquipment.of(recipe))
    }

    @Test
    fun blankNamesAreSkipped() {
        assertEquals(listOf("Whisk"), RecipeEquipment.of(info(step("", "   ", "whisk"))))
    }

    @Test
    fun noEquipmentYieldsAnEmptyList() {
        assertTrue(RecipeEquipment.of(info(step())).isEmpty())
        assertTrue(RecipeEquipment.of(RecipeInformation(id = 1, title = "Test")).isEmpty())
    }
}
