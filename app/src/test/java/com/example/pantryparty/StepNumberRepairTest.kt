package com.example.pantryparty

import com.example.pantryparty.recipe.repairTruncatedNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class StepNumberRepairTest {

    // Verbatim from the live API for recipe 715394 — the report that started this.
    private val ENCHILADA_PLAIN =
        "Instructions Heat oven to 375. Prepare a 9 x 13 baking dish and begin by " +
            "placing a layer of tortillas on the bottom of the dish."

    @Test
    fun restoresTheDroppedDigit() {
        assertEquals(
            "Heat oven to 375",
            repairTruncatedNumber("Heat oven to 37", ENCHILADA_PLAIN)
        )
    }

    @Test
    fun restoresMoreThanOneDigitWhenTheParserAteMore() {
        assertEquals(
            "Preheat oven to 350",
            repairTruncatedNumber("Preheat oven to 3", "Preheat oven to 350. Grease a pan.")
        )
    }

    @Test
    fun leavesAStepAloneWhenThePlainTextAgrees() {
        // Already complete: the plain text continues with ". ", not another digit.
        assertEquals(
            "Heat oven to 375",
            repairTruncatedNumber("Heat oven to 375", ENCHILADA_PLAIN)
        )
    }

    @Test
    fun leavesStepsNotEndingInADigitAlone() {
        val step = "Bake for 30 minutes."
        assertEquals(step, repairTruncatedNumber(step, "Bake for 30 minutes. Then cool."))
    }

    @Test
    fun leavesTheStepAloneWhenItAppearsTwice() {
        // Two occurrences and two different continuations — there is no way to know
        // which one belongs to this step, so guessing is worse than truncating.
        val plain = "Heat oven to 375. Later, heat oven to 400. Heat oven to 37 again."
        assertEquals("Heat oven to 37", repairTruncatedNumber("Heat oven to 37", plain))
    }

    @Test
    fun leavesTheStepAloneWithoutPlainInstructions() {
        assertEquals("Heat oven to 37", repairTruncatedNumber("Heat oven to 37", ""))
        assertEquals("Heat oven to 37", repairTruncatedNumber("Heat oven to 37", "   "))
    }

    @Test
    fun leavesTheStepAloneWhenItIsNotInThePlainText() {
        assertEquals(
            "Heat oven to 37",
            repairTruncatedNumber("Heat oven to 37", "Something else entirely.")
        )
    }

    @Test
    fun ignoresVeryShortFragments() {
        // "5" would match almost anywhere; too weak a signal to act on.
        assertEquals("5", repairTruncatedNumber("5", "Bake at 500 degrees."))
    }

    @Test
    fun capsHowManyDigitsItWillAppend() {
        val repaired = repairTruncatedNumber("Set timer to 1", "Set timer to 123456789 seconds")
        assertEquals("Set timer to 1234", repaired)
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        assertEquals("Heat oven to 375", repairTruncatedNumber("Heat oven to 37 ", ENCHILADA_PLAIN))
    }
}
