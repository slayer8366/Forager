package com.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import com.forager.app.domain.CompassProvider
import com.forager.app.domain.ComputeTrueHeadingUseCase
import com.forager.app.domain.HeadingSmoother
import com.forager.app.domain.LocationFix

/**
 * What the compass strip and the navigation HUD both read — navigation HUD stage one. One value,
 * so the two can never disagree.
 */
sealed interface TrueHeadingReading {
    /** Smoothed heading in degrees clockwise from **true** north, `[0, 360)`. */
    data class Available(val degrees: Float) : TrueHeadingReading

    /** The device has no usable rotation sensor — [CompassProvider.heading] emitted `null`. */
    data object NoSensor : TrueHeadingReading

    /**
     * The sensor works but no fix exists yet, and true heading needs declination, which needs a
     * position. Deliberately not "show magnetic until then" (owner decision): that would jump by
     * local declination — about 15° in the Pacific Northwest — the moment the first fix landed.
     */
    data object NeedsFix : TrueHeadingReading
}

/**
 * The one smoothed, true-north heading both compasses read.
 *
 * **Returns a [State], and the caller must hand the `State` object itself down — never read
 * `.value` at the call site.** This runs at sensor rate (about 16 Hz). Compose tracks snapshot
 * reads at the scope that performs them, so the compass strip and the HUD each reading `.value`
 * inside their own leaf composable recomposes only those two leaves; a `.value` read in
 * `CompactMapTab`'s own body would recompose the whole map tab — the map's own overlays, the icon
 * cluster, every drag clamp — on every sensor event, the exact isolation the strip was built as a
 * leaf to protect. A future reader will be tempted to `by` this at the top; that is why this
 * comment exists.
 *
 * Pipeline, per emission: [CompassProvider.heading] → [HeadingSmoother] (one instance, so the
 * strip and the HUD's needle share one filter — the needle is heading-relative, so smoothing the
 * heading smooths it) → [ComputeTrueHeadingUseCase] against the latest [liveFix], read through
 * [rememberUpdatedState] so a new fix never restarts the producer. The producer *is* restarted
 * when a fix first appears or disappears ([hasFix] is a key): a compass source that has already
 * emitted its latest value would otherwise leave the reading at [TrueHeadingReading.NeedsFix]
 * until the next sensor event, and the restart re-registers the sensor listener once per such
 * transition, not per fix.
 */
@Composable
fun rememberTrueHeading(
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    liveFix: LocationFix.Update?,
): State<TrueHeadingReading> {
    val currentFix by rememberUpdatedState(liveFix)
    val hasFix = liveFix != null
    return produceState<TrueHeadingReading>(initialValue = TrueHeadingReading.NeedsFix, compassProvider, computeTrueHeading, hasFix) {
        val smoother = HeadingSmoother()
        compassProvider.heading.collect { magnetic ->
            val fix = currentFix
            value = when {
                magnetic == null -> {
                    smoother.reset()
                    TrueHeadingReading.NoSensor
                }
                fix == null -> {
                    smoother.reset()
                    TrueHeadingReading.NeedsFix
                }
                else -> TrueHeadingReading.Available(
                    computeTrueHeading(smoother.next(magnetic), fix.lat, fix.lng, fix.altitude, fix.timestampEpochMillis),
                )
            }
        }
    }
}
