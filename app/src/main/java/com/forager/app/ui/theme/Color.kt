package com.forager.app.ui.theme

import androidx.compose.ui.graphics.Color

val ForestGreen = Color(0xFF2E5339)
val MossGreen = Color(0xFF5C8A5E)
val Mushroom = Color(0xFFC97B3D)
val Cream = Color(0xFFEDE3D0)
val Bark = Color(0xFF3B2E24)

/**
 * Dark theme's background/surface. Replaces [Bark] there: a warm brown reads as a colored tint,
 * not the neutral gray Android's own dark theme convention uses, which is what dark mode was
 * asked to match. [Bark] stays in use for light-theme text/containers, where a warm dark tone is
 * the point.
 */
val NeutralGray900 = Color(0xFF1B1B1B)

/** Dark theme's surfaceVariant — one step lighter than [NeutralGray900], same gray family. */
val NeutralGray800 = Color(0xFF2C2C2C)

/** Dark theme's onSurfaceVariant: dimmer than [Cream], for secondary text on [NeutralGray800]. */
val NeutralGray200 = Color(0xFFCFC9BE)

/**
 * primaryContainer/surfaceVariant roles below exist because Compose fills any role a
 * [androidx.compose.material3.lightColorScheme]/[androidx.compose.material3.darkColorScheme] call
 * doesn't name with Material3's own baseline (purple-tinted) default — which several roles this
 * app actually renders with (`surfaceVariant`, `onSurfaceVariant`, `primaryContainer`) were
 * silently doing, clashing with the brown/green/orange palette everywhere else. These are muted
 * steps of the brand hues instead, not new colors: a tonal-container convention Material3 itself
 * follows, applied to this app's own palette rather than the default one.
 */
val MossGreenContainerDark = Color(0xFF3D5A40)
val MossGreenContainerLight = Color(0xFFD9E8D2)
val WarmOnSurfaceVariantLight = Color(0xFF5B5347)

/**
 * Light theme's background/surface. [Cream] used to fill this role directly — a saturated warm
 * tan is fine as an accent but, spread across the entire screen with nothing lighter above it,
 * reads as a flat, dated wash rather than the near-white neutral modern Material 3 light theme
 * actually uses ("cigarette stain yellow", reported against the previous version). [Cream] moved
 * to `surfaceVariant` instead — a tonal step, not the dominant surface.
 */
val NearWhite = Color(0xFFFAF8F3)
