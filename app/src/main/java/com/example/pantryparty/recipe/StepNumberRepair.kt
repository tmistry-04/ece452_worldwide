package com.example.pantryparty.recipe

/**
 * Restores the final digit Spoonacular's `analyzedInstructions` parser drops when a
 * step's text ends in a number.
 *
 * Verified against the live API: recipe 715394 step 1 reads "Heat oven to 37" while
 * the same recipe's plain `instructions` field says "Heat oven to 375."; recipe
 * 642041 says "Preheat oven to 35" for a 350 degree oven. Sampling 40 random recipes
 * turned up two steps ending in a digit and both were truncated — rare, but always
 * wrong, and always the oven temperature, which is the one number in a recipe you
 * cannot infer from context.
 *
 * The repair is deliberately timid, because a confidently wrong temperature is worse
 * than a visibly truncated one. [step] comes back untouched unless all of these hold:
 *  - it ends in a digit (nothing else is truncated this way),
 *  - [plainInstructions] is non-blank, so there is a second source to check against,
 *  - [step] occurs there exactly once — two occurrences give us no way to tell which
 *    continuation belongs to this step, and
 *  - the plain text carries straight on with at least one more digit.
 *
 * Both arguments must already be [stripHtml]-ed: the two fields carry different
 * markup around the same prose, so the substring search only lines up on plain text.
 */
fun repairTruncatedNumber(step: String, plainInstructions: String): String {
    if (plainInstructions.isBlank()) return step
    val trimmed = step.trim()
    if (trimmed.length < MIN_STEP_LENGTH || !trimmed.last().isDigit()) return step

    val at = plainInstructions.indexOf(trimmed)
    if (at < 0) return step
    // Ambiguous: a recipe that repeats a line verbatim gives us nothing to pick with.
    if (plainInstructions.indexOf(trimmed, at + 1) >= 0) return step

    val tail = plainInstructions.drop(at + trimmed.length)
        .takeWhile { it.isDigit() }
        .take(MAX_APPENDED_DIGITS)
    return if (tail.isEmpty()) step else step.trimEnd() + tail
}

// Short enough to be a fragment rather than a sentence, and correspondingly likely
// to match somewhere unintended.
private const val MIN_STEP_LENGTH = 3

// The observed bug drops exactly one digit off an oven temperature, so three is
// already generous; the cap just bounds the damage if the search ever lands
// somewhere unexpected.
private const val MAX_APPENDED_DIGITS = 3
