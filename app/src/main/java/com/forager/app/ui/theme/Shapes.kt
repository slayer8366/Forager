package com.forager.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * `ForagerTheme`'s shape scale (understory-design-system.md, step 2). Rounder than 1.5.0-alpha26's
 * stock scale on the first five steps; the top three — [Shapes.extraLarge], [extraLargeIncreased],
 * [extraExtraLarge] — are unchanged, since the design doc's own table (§2S) only raises the steps
 * below where the app's own components (buttons, chips, cards, the map icon stack, drawers) sit.
 *
 * | Step | Forager | M3 stock |
 * |---|---|---|
 * | extraSmall | 6dp | 4dp |
 * | small | 10dp | 8dp |
 * | medium | 14dp | 12dp |
 * | large | 20dp | 16dp |
 * | largeIncreased | 24dp | 20dp |
 * | extraLarge | 28dp | 28dp |
 * | extraLargeIncreased | 32dp | 32dp |
 * | extraExtraLarge | 48dp | 48dp |
 *
 * The expressive read is the visible half of the reason; the field half, per the design doc, is
 * that larger radii come with larger, more separable touch targets, and this app is operated with
 * cold hands and gloves. Whether that holds with gloves is a device-gate question, not asserted
 * here as fact.
 */
val ForagerShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    largeIncreased = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)
