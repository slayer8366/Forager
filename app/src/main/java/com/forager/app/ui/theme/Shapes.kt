package com.forager.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Understory step 2 / tag 03: the app had no [Shapes] before this, so every corner in the UI was
 * Material's own default scale. This one raises the first five steps and keeps the top three,
 * which are already large enough that the expressive scale and the field reason below agree:
 *
 * | Step                 | Understory | M3 1.5.0 stock |
 * |----------------------|-----------:|---------------:|
 * | extraSmall            |        6dp |            4dp |
 * | small                 |       10dp |            8dp |
 * | medium                |       14dp |           12dp |
 * | large                 |       20dp |           16dp |
 * | largeIncreased        |       24dp |           20dp |
 * | extraLarge            |       28dp |           28dp |
 * | extraLargeIncreased   |       32dp |           32dp |
 * | extraExtraLarge       |       48dp |           48dp |
 *
 * The expressive read is the visible half of the reason; the field half is that larger radii come
 * with larger, more separable touch targets, and this app is operated one-handed, outdoors, with
 * cold hands and gloves. Whether that actually helps with gloves is a device-gate question (see
 * docs/plans/understory-design-system.md §5S, Gate G) — this is the value, not a claim it reads
 * better in practice yet.
 */
val ForagerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    largeIncreased = RoundedCornerShape(24.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)
