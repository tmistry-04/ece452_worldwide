package com.example.pantryparty.recipe

/**
 * Minimal HTML-to-text for the small fragments Spoonacular puts in `summary` and
 * `instructions` (`<b>`, `<a>`, `<ol><li>`, `&amp;`).
 *
 * Deliberately not androidx.core.text.HtmlCompat: that delegates to
 * android.text.Html, which is an unmocked stub in this module's JVM unit tests
 * (no Robolectric, no `testOptions.unitTests.isReturnDefaultValues`). The step
 * fallback chain this feeds is worth testing, so it has to run off-device.
 */

// Block-level boundaries become newlines *before* the generic tag strip below —
// otherwise "<li>a</li><li>b</li>" collapses into "ab".
private val BLOCK_BREAK = Regex("(?i)<br\\s*/?>|</(p|li|div|h[1-6]|tr|ol|ul)\\s*>")
private val TAG = Regex("<[^>]*>")

// One pass, so a literal "&amp;lt;" decodes to "&lt;" and stops there. Chained
// .replace("&amp;", "&").replace("&lt;", "<") calls would keep going and yield "<".
private val ENTITY = Regex("&(#\\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,9});")
private val SPACE_RUN = Regex("[ \\t\\u00A0]+")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "deg" to "°", "hellip" to "…", "mdash" to "—", "ndash" to "–",
    "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
    "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "times" to "×", "divide" to "÷"
)

/** Plain text, with block boundaries preserved as newlines. Null/blank in, "" out. */
fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    val withBreaks = BLOCK_BREAK.replace(html, "\n")
    val withoutTags = TAG.replace(withBreaks, "")
    val decoded = ENTITY.replace(withoutTags) { decodeEntity(it) }
    return decoded.lineSequence()
        .map { SPACE_RUN.replace(it, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

/** [stripHtml] split into non-blank lines — the plain-instructions step fallback. */
fun htmlParagraphs(html: String?): List<String> =
    stripHtml(html).split('\n').filter { it.isNotBlank() }

/** An unrecognized entity is left exactly as written rather than silently dropped. */
private fun decodeEntity(match: MatchResult): String {
    val body = match.groupValues[1]
    return when {
        body.startsWith("#x", ignoreCase = true) ->
            body.drop(2).toIntOrNull(16)?.asCodePoint() ?: match.value
        body.startsWith("#") ->
            body.drop(1).toIntOrNull()?.asCodePoint() ?: match.value
        else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
    }
}

/** Handles astral-plane code points, which Int.toChar() would truncate. */
private fun Int.asCodePoint(): String? =
    if (this in 1..0x10FFFF) String(Character.toChars(this)) else null
