package com.example.pantryparty

import com.example.pantryparty.recipe.sourceCreditName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeAttributionTest {

    @Test
    fun prefersTheSourceNameTheApiGave() {
        assertEquals(
            "Foodista",
            sourceCreditName("Foodista", "https://www.foodista.com/recipe/NS3B8M6T/x")
        )
    }

    @Test
    fun fallsBackToTheHostWhenTheNameIsMissing() {
        // The terms require naming the site, and the host is the site's name.
        assertEquals("pinkwhen.com", sourceCreditName(null, "https://www.pinkwhen.com/some-recipe/"))
        assertEquals("pinkwhen.com", sourceCreditName("   ", "https://pinkwhen.com/some-recipe/"))
    }

    @Test
    fun stripsWwwButKeepsOtherSubdomains() {
        assertEquals("blog.example.com", sourceCreditName(null, "https://blog.example.com/r/1"))
    }

    @Test
    fun ignoresPortsPathsAndQueryStrings() {
        assertEquals("example.com", sourceCreditName(null, "http://example.com:8080/r/1?ref=x#top"))
    }

    @Test
    fun returnsNullWhenThereIsNothingToLinkTo() {
        assertNull(sourceCreditName("Foodista", null))
        assertNull(sourceCreditName("Foodista", ""))
        // Not a web link, so there is no attribution to render.
        assertNull(sourceCreditName("Foodista", "ftp://example.com/r"))
    }

    @Test
    fun fallsBackToAGenericLabelWhenTheHostCannotBeRead() {
        assertEquals("the original site", sourceCreditName(null, "https://"))
    }
}
