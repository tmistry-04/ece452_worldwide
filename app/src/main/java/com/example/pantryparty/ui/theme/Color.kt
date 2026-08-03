package com.example.pantryparty.ui.theme

import androidx.compose.ui.graphics.Color

// Pantry Party brand palette — warm, food-forward, easy on daily eyes.
//
// Contrast note: the display tomato (#E8543C) and fresh green (#1FBA5B) from the
// design system are only safe behind *dark* text or as large decorative fills.
// Anything carrying white text uses the darkened variants below, which clear
// WCAG AA (4.5:1). The bright originals are kept for containers and accents.

// --- Core brand ---
val TomatoBright = Color(0xFFE8543C)   // display/decorative fill only (3.6:1 on white)
val Tomato = Color(0xFFC93D28)         // primary — CTAs, brand (5.0:1 with white)
val TomatoContainer = Color(0xFFFCE9DF) // soft coral card/chip background
val OnTomatoContainer = Color(0xFF5C1A0E)

val HerbGreenBright = Color(0xFF1FBA5B) // "fresh" chips — dark text only
val HerbGreen = Color(0xFF12793A)       // secondary (5.5:1 with white)
val HerbGreenContainer = Color(0xFFD9EBDF)
val OnHerbGreenContainer = Color(0xFF062914)

val HoneyAmber = Color(0xFFF4A93C)      // accent — "expiring soon"; dark text only
val AmberContainer = Color(0xFFFBE9C9)
val OnAmberContainer = Color(0xFF4A2E00)

// --- Neutrals ---
val Cream = Color(0xFFFBF5EA)           // app background
val CocoaInk = Color(0xFF2A2420)        // primary text (14.1:1 on cream)
val WarmGreyDark = Color(0xFF6B6357)    // secondary text
val WarmGreyLight = Color(0xFF9A9082)   // dividers, tertiary text
val CardSurface = Color(0xFFFFFDF8)     // cards sit slightly above the background

// --- Dark scheme ---
val TomatoDark = Color(0xFFFF8A72)
val TomatoContainerDark = Color(0xFF7A2415)
val OnTomatoContainerDark = Color(0xFFFFDAD1)
val HerbGreenDark = Color(0xFF7ED9A0)
val HerbGreenContainerDark = Color(0xFF0E4D28)
val OnHerbGreenContainerDark = Color(0xFFC6EFD5)
val AmberDark = Color(0xFFFFC46B)
val AmberContainerDark = Color(0xFF6B4A00)
val OnAmberContainerDark = Color(0xFFFFE2AE)
val InkSurface = Color(0xFF1C1917)
val InkSurfaceElevated = Color(0xFF262220)
val OnInkSurface = Color(0xFFEDE7DE)
val InkSurfaceVariant = Color(0xFF4A443E)