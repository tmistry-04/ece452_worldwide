package com.example.pantryparty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed brand schemes — no Material-You dynamic colour, so the app looks the same
// on every device (and matches the design system in the deck).
//
// Role mapping:
//   primary     Tomato      CTAs, FAB, selected nav
//   secondary   HerbGreen   "fresh"/ready states
//   tertiary    HoneyAmber  "expiring soon" warnings
//   error       Tomato      expired / destructive
private val LightColorScheme = lightColorScheme(
    primary = Tomato,
    onPrimary = Color.White,
    primaryContainer = TomatoContainer,
    onPrimaryContainer = OnTomatoContainer,

    secondary = HerbGreen,
    onSecondary = Color.White,
    secondaryContainer = HerbGreenContainer,
    onSecondaryContainer = OnHerbGreenContainer,

    tertiary = HoneyAmber,
    onTertiary = CocoaInk,              // never white on amber — fails AA
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,

    background = Cream,
    onBackground = CocoaInk,
    surface = Cream,
    onSurface = CocoaInk,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF8),
    surfaceContainer = CardSurface,
    surfaceContainerHigh = Color(0xFFF6EFE2),
    surfaceContainerHighest = Color(0xFFF2EADC),
    surfaceDim = Color(0xFFEDE4D5),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceVariant = TomatoContainer,
    onSurfaceVariant = WarmGreyDark,
    outline = WarmGreyLight,
    outlineVariant = Color(0xFFE5DDD0),

    error = Tomato,
    onError = Color.White,
    errorContainer = Color(0xFFFAD5C6),
    onErrorContainer = OnTomatoContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = TomatoDark,
    onPrimary = Color(0xFF4A1509),
    primaryContainer = TomatoContainerDark,
    onPrimaryContainer = OnTomatoContainerDark,

    secondary = HerbGreenDark,
    onSecondary = Color(0xFF062914),
    secondaryContainer = HerbGreenContainerDark,
    onSecondaryContainer = OnHerbGreenContainerDark,

    tertiary = AmberDark,
    onTertiary = Color(0xFF3D2500),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,

    background = InkSurface,
    onBackground = OnInkSurface,
    surface = InkSurface,
    onSurface = OnInkSurface,
    surfaceContainerLowest = Color(0xFF141210),
    surfaceContainerLow = Color(0xFF211E1B),
    surfaceContainer = InkSurfaceElevated,
    surfaceContainerHigh = Color(0xFF302B27),
    surfaceContainerHighest = Color(0xFF3B352F),
    surfaceDim = Color(0xFF1C1917),
    surfaceBright = Color(0xFF3B352F),
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCFC6BA),
    outline = Color(0xFF6B6357),
    outlineVariant = Color(0xFF3A342E),

    error = TomatoDark,
    onError = Color(0xFF4A1509),
    errorContainer = TomatoContainerDark,
    onErrorContainer = OnTomatoContainerDark
)

@Composable
fun PantryPartyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}