package com.example.pantryparty.ui.theme

import androidx.compose.ui.graphics.Color

// Pantry Party brand palette — a warm "fresh kitchen" look:
// herb green as the primary, warm pumpkin orange as the accent (tertiary).
// Tokens are split into light/dark so the fixed (non-dynamic) theme reads well
// in both modes.

// --- Light scheme tokens ---
val HerbGreen = Color(0xFF4C6B3C)          // primary: deep herb green
val HerbGreenContainer = Color(0xFFCDEDB6) // soft leafy container
val OnHerbGreenContainer = Color(0xFF0E2003)
val SageGrey = Color(0xFF55624C)           // secondary: muted sage
val SageGreyContainer = Color(0xFFD8E7CB)
val Pumpkin = Color(0xFFB5651D)            // tertiary: warm pumpkin accent
val PumpkinContainer = Color(0xFFFFDCC2)
val OnPumpkinContainer = Color(0xFF341100)
val Cream = Color(0xFFFBFDF5)              // background/surface: warm off-white
val OnCream = Color(0xFF1A1C18)
val CreamSurfaceVariant = Color(0xFFE0E4D6) // card outlines / dividers

// --- Dark scheme tokens ---
val HerbGreenDark = Color(0xFFB2D29C)       // lighter green so it pops on dark
val HerbGreenContainerDark = Color(0xFF354E26)
val OnHerbGreenContainerDark = Color(0xFFCDEDB6)
val SageGreyDark = Color(0xFFBCCBB0)
val SageGreyContainerDark = Color(0xFF3D4A35)
val PumpkinDark = Color(0xFFFFB77C)
val PumpkinContainerDark = Color(0xFF8A4C00)
val OnPumpkinContainerDark = Color(0xFFFFDCC2)
val CharcoalGreen = Color(0xFF1A1C18)       // dark background/surface
val OnCharcoalGreen = Color(0xFFE3E3DB)
val CharcoalSurfaceVariant = Color(0xFF44483F)
