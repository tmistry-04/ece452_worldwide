package com.example.pantryparty.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * +/- button shared by the pantry-row steppers and the "I made this" deduction
 * dialog. IconButton gives the 48dp minimum touch target plus button
 * semantics/ripple for free; the colors dim automatically when disabled.
 */
@Composable
fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(icon, contentDescription = description)
    }
}
