package com.example.pantryparty.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

// ingredientImageUrl/recipeImageUrl moved to ImageUrls.kt (same package) so they
// can be unit-tested without loading this file's Compose and Coil dependencies.

/**
 * Coil-backed image with a crossfade, a spinner while loading, and a friendly
 * fork-and-knife fallback when the URL is null or fails. Caller controls size
 * and clipping via [modifier].
 */
@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { Placeholder() },
        error = { Placeholder() }
    )
}

/** Neutral placeholder shown while loading or when no image is available. */
@Composable
private fun Placeholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Restaurant,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
