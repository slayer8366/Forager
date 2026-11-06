package com.zynergylabs.forager.app.ui.availability

// Moved from AvailabilityCompactScaffold.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens

/** The compact scaffold's snackbar, for tests (map chrome at 80%, dispatch 2026-09-28-56 as amended by -58). */
internal const val COMPACT_SNACKBAR_TAG = "compact-snackbar"

/**
 * The margin Material3's `Snackbar(snackbarData, modifier)` adds inside the modifier it is given, around its own surface: 12 dp,
 * read from the material3 1.5.0-alpha26 bytecode (`modifier.padding(12.dp)` before the surface). The snackbar's tap, tag and
 * press highlight sit this far in, on the drawn surface (motion Part 1, scout S7; Amendment 1, RECORD -657).
 */
internal val SNACKBAR_SURFACE_MARGIN = 12.dp

/**
 * The Maps tab snackbar's bottom offset, gliding to [target] on [MotionTokens.navigationMotionSpec], the attribution insets' own
 * spec (motion Part 2, item 7). When [tab] changes it jumps to the new [target] in the same frame instead, because the
 * Scaffold's own placement of the snackbar changes at once then and a glide would move it on screen. Instant under reduced
 * motion. Never negative: a spatial spring overshoots, and a negative padding throws (animatedTopInset's comment).
 */
@Composable
internal fun glidingSnackbarBottom(target: Dp, tab: CompactTab): Dp {
    val reduceMotion = LocalReduceMotion.current
    val spec = MotionTokens.navigationMotionSpec<Dp>()
    val animatable = remember { Animatable(target, Dp.VectorConverter) }
    var settledTab by remember { mutableStateOf(tab) }
    val jump = settledTab != tab || reduceMotion
    LaunchedEffect(target, tab, reduceMotion) {
        if (settledTab != tab || reduceMotion) {
            animatable.snapTo(target)
            settledTab = tab
        } else {
            animatable.animateTo(target, spec)
        }
    }
    return if (jump) target else animatable.value.coerceAtLeast(0.dp)
}
