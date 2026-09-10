package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassTrustJudge
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.HeadingSmoother
import com.zynergylabs.forager.app.domain.LocationFix

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

    /**
     * The sensor is present and reporting, and its reading is not to be trusted — compass-
     * reliability dispatch. The magnetometer is silently distorted by nearby metal and current (a
     * vehicle, a fence, a power line, rebar): it keeps reporting and the heading is tens of degrees
     * wrong. [com.zynergylabs.forager.app.domain.CompassTrustJudge] decides this from the sensor's own
     * uncertainty, with hysteresis. Carries no heading on purpose: a heading the app has just said
     * cannot be trusted is not a value to hand anyone. Distinct from [NoSensor], which is terminal
     * (the flow closes); this is live and reversible, and clears on its own when the reading has
     * held good.
     */
    data object Unreliable : TrueHeadingReading
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
 * Pipeline, per emission: [CompassProvider.heading] → [CompassTrustJudge] (the sensor's own
 * uncertainty, with hysteresis — an untrusted reading becomes [TrueHeadingReading.Unreliable] and
 * resets the smoother) → [HeadingSmoother] (one instance, so the
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
        val trust = CompassTrustJudge()
        compassProvider.heading.collect { reading ->
            val fix = currentFix
            // The judge sees every reading, fix or no fix, so its hysteresis state is right the
            // moment a fix lands — it is fed before the `when` decides what to show.
            val unreliable = reading != null && trust.next(reading)
            value = when {
                reading == null -> {
                    smoother.reset()
                    trust.reset()
                    TrueHeadingReading.NoSensor
                }
                // Position failure before compass failure — the same order the HUD's needle uses
                // (lost fix wins): without a fix there is no true heading to distrust, and the
                // strip already says one thing for one cause. The judge was still fed above.
                fix == null -> {
                    smoother.reset()
                    TrueHeadingReading.NeedsFix
                }
                // Reset, not pause, not carry (owner decision): reset is the only shape that never
                // shows a heading the sensor did not just report. Kept unseeded for the whole
                // unreliable stretch, so recovery re-seeds from the first good reading and shows it
                // as-is — a visible snap where the reading was wrong is the truth arriving; a blend
                // of distorted history into a recovered heading at alpha 0.3 would be a wrong
                // reading that looks healthy for the second the user is most likely watching.
                unreliable -> {
                    smoother.reset()
                    TrueHeadingReading.Unreliable
                }
                else -> TrueHeadingReading.Available(
                    computeTrueHeading(smoother.next(reading.magneticHeadingDegrees), fix.lat, fix.lng, fix.altitude, fix.timestampEpochMillis),
                )
            }
        }
    }
}
