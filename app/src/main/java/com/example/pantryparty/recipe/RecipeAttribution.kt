package com.example.pantryparty.recipe

/**
 * The name to credit a recipe's source under.
 *
 * Spoonacular's terms require showing the original site's name alongside a working
 * link to it, so this has to produce something whenever there is a link at all.
 * `sourceName` is occasionally null or blank on recipes that do carry a `sourceUrl`,
 * so fall back to the URL's own host — which *is* the site's name — before settling
 * for a generic label. Returns null only when there is no link to attribute.
 *
 * A regex rather than android.net.Uri: this package has to run in JVM unit tests,
 * where the android.* classes are unmocked stubs.
 */
fun sourceCreditName(sourceName: String?, sourceUrl: String?): String? {
    if (sourceUrl.isNullOrBlank() || !sourceUrl.startsWith("http")) return null
    sourceName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return HOST.find(sourceUrl)?.groupValues?.get(1) ?: "the original site"
}

private val HOST = Regex("^https?://(?:www\\.)?([^/?#:]+)", RegexOption.IGNORE_CASE)
