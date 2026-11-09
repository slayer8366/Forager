package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
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
    memory: HeadingMemory? = null,
    nowMillis: () -> Long = { 0L },
): State<TrueHeadingReading> = rememberTrueHeading(compassProvider, computeTrueHeading, rememberUpdatedState(liveFix), memory, nowMillis)

/**
 * The same, with the fix handed over as a [State] (dispatch 2026-09-28-430): a map makes its own
 * heading from [MapCompass] without reading the fix in its composition, which would recompose the
 * whole map once a second. Keyed, as before, on whether there is a fix, not on each one.
 */
@Composable
fun rememberTrueHeading(
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    liveFix: State<LocationFix.Update?>,
    /** RECORD -761: where the last reading is kept across a tab change, and the clock it is aged by. `null` keeps none. */
    memory: HeadingMemory? = null,
    nowMillis: () -> Long = { 0L },
): State<TrueHeadingReading> {
    val hasFix by remember(liveFix) { derivedStateOf { liveFix.value != null } }
    // RECORD -761: a strip coming back with the Maps tab starts from the last reading, when there is a fix and that reading is
    // fresh (HeadingMemory.fresh), rather than "—" until the compass reports again (seen on the S22: about 200 ms of "—").
    val initial: TrueHeadingReading = remember { (if (liveFix.value != null) memory?.fresh(nowMillis()) else null) ?: TrueHeadingReading.NeedsFix }
    return produceState<TrueHeadingReading>(initialValue = initial, compassProvider, computeTrueHeading, hasFix) {
        val smoother = HeadingSmoother()
        val trust = CompassTrustJudge()
        compassProvider.heading.collect { reading ->
            val fix = liveFix.value
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
            memory?.record(value, nowMillis())
        }
    }
}

/**
 * RECORD -761: the last true heading a strip showed, and when, kept above the Maps tab ([MapCameraMemory.heading]). A plain
 * field written at sensor rate: nothing recomposes for it, and it is read once, when a strip is first composed.
 */
class HeadingMemory {
    private var last: TrueHeadingReading.Available? = null
    private var atMillis: Long = 0L

    /** Keeps [reading] if it is a heading; anything else (no fix, untrusted, no sensor) forgets the last one. */
    fun record(reading: TrueHeadingReading, nowMillis: Long) {
        last = reading as? TrueHeadingReading.Available
        atMillis = nowMillis
    }

    /**
     * The last heading if it was taken within [HEADING_KEPT_FOR_MS] of [nowMillis], else null. Stale, the owner's word, chosen
     * as 10 s: the compass reports about 16 times a second, so on the phone a new reading replaces the kept one within a frame
     * or two of a return; 10 s covers a quick look at another tab, while after longer the phone may well have turned, and "—"
     * until the compass reports is the honest answer.
     */
    fun fresh(nowMillis: Long): TrueHeadingReading.Available? =
        last?.takeIf { nowMillis - atMillis in 0..HEADING_KEPT_FOR_MS }
}

/** RECORD -761: how long a kept heading is shown again on a return to Maps. See [HeadingMemory.fresh]. */
const val HEADING_KEPT_FOR_MS = 10_000L
