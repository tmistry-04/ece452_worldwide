package com.example.pantryparty.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fixed brand schemes built from the food-themed tokens in Color.kt.
// Using explicit schemes (rather than Material-You dynamic color) keeps the
// app on-brand across every device.
private val LightColorScheme = lightColorScheme(
    primary = HerbGreen,
    onPrimary = Color.White,
    primaryContainer = HerbGreenContainer,
    onPrimaryContainer = OnHerbGreenContainer,
    secondary = SageGrey,
    onSecondary = Color.White,
    secondaryContainer = SageGreyContainer,
    onSecondaryContainer = OnHerbGreenContainer,
    tertiary = Pumpkin,
    onTertiary = Color.White,
    tertiaryContainer = PumpkinContainer,
    onTertiaryContainer = OnPumpkinContainer,
    background = Cream,
    onBackground = OnCream,
    surface = Cream,
    onSurface = OnCream,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = OnCream,
)

private val DarkColorScheme = darkColorScheme(
    primary = HerbGreenDark,
    onPrimary = OnHerbGreenContainer,
    primaryContainer = HerbGreenContainerDark,
    onPrimaryContainer = OnHerbGreenContainerDark,
    secondary = SageGreyDark,
    onSecondary = OnHerbGreenContainer,
    secondaryContainer = SageGreyContainerDark,
    onSecondaryContainer = OnHerbGreenContainerDark,
    tertiary = PumpkinDark,
    onTertiary = OnPumpkinContainer,
    tertiaryContainer = PumpkinContainerDark,
    onTertiaryContainer = OnPumpkinContainerDark,
    background = CharcoalGreen,
    onBackground = OnCharcoalGreen,
    surface = CharcoalGreen,
    onSurface = OnCharcoalGreen,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = OnCharcoalGreen,
)

@Composable
fun PantryPartyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: we want a consistent brand look, not wallpaper-derived color.
    // Callers can still opt back into Material-You if desired.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
