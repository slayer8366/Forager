package com.forager.app.domain

import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint

/**
 * The read-seam rule that keeps network-provider fixes out of what a track shows — the
 * timestamp-filter dispatch, replacing Part A of the two-data-corrections dispatch (held).
 *
 * **The evidence, in two sentences.** Four tracks exported from one device on one day
 * (`docs/audits/2026-09-06-starburst-gpx-findings.md`,
 * `docs/audits/2026-09-06-timestamp-discriminator-findings.md`): every point whose stored timestamp
 * had non-zero milliseconds carried the same stored ~111 m elevation and sat 8–36 m off the real
 * position, every whole-second point varied like a measurement, and the airplane-mode control track
 * — network provider suppressed — had zero sub-second stamps and no excursions at all. Removing the
 * sub-second points collapsed 130 m of "movement" while sitting still to 4 m.
 *
 * **Why the clock discriminates.** GPS time is second-aligned, derived from the satellite epoch at
 * a 1 Hz cadence; the network provider stamps with a clock that lands on arbitrary milliseconds.
 * The value the store holds is `Location.getTime()`, unchanged from the fix to the row
 * (`location/AndroidLocationTracker.kt`, the service, `RoomTrackRepository` — confirmed in the
 * second findings document), so `millis == 0` on the stored value is the whole test: no
 * threshold, no tuning, no accuracy arithmetic, and it applies to every track ever recorded.
 *
 * **Where it runs, and where it deliberately does not.** At the one mapping in
 * `RoomTrackRepository`, which every read of `track_points` passes through — so it is
 * retroactive, reversible, and writes nothing. **Not at the source**: the sampler's interval and
 * distance rules compare against the last *stored* point, so excluding network fixes before storage
 * would change which GPS fixes get accepted and produce a different track, not the same track by
 * another route (owner decision). Nothing stored is deleted or modified; excluded points are simply
 * not in the list a read returns, and their count travels with the track as
 * [Track.excludedPointCount] so the exclusion can be seen when it is large.
 *
 * **The limit this rule is built to be honest about.** Second-aligned GPS time is a property of a
 * device's GNSS stack, not a guarantee. On a phone whose GPS fixes carry milliseconds this rule
 * would exclude most or all of a track — which is why [isMostlyNetworkFixes] and
 * [hasNoUsablePoints] exist and why the surfaces that read them never stay silent about it. Four
 * tracks, one phone; the earlier radios-off ~40 m excursion is not explained by this rule.
 *
 * **The instrument for checking this against other hardware** is the GPX full-record export
 * (dispatch, format B1): [com.forager.app.domain.TrackRepository.getFullRecord] carries every
 * stored point, tagged with the verdict this predicate gives it, out to a tester's GPX file where
 * it can be read against whatever device actually recorded it — the only way a non-second-aligned
 * GNSS clock elsewhere would ever surface. Superseded as the beta's own instrument for this rule:
 * the trip-report question `docs/beta/trip-report.md` asks (see that file and its README) is no
 * longer the only one.
 */
fun TrackPoint.isNetworkProviderFix(): Boolean = timestampEpochMillis % 1_000L != 0L

/**
 * The identifier [isNetworkProviderFix] is named by, in a GPX full record's `rule` provenance
 * attributes — GPX rule-provenance dispatch (owner ruling, 2026-09-09). A stable literal, not
 * display copy and not derived from anything: once a file carrying it is on a tester's phone the
 * value can never be restated, so renaming this constant would silently re-point every exported
 * file's provenance at a rule that no longer means what the file meant. Read only by
 * [NETWORK_FIX_EXCLUSION_RULES] and, per excluded point, by
 * [com.forager.app.data.repository.RoomTrackRepository.getFullRecord].
 */
const val TIMESTAMP_MILLIS_NON_ZERO_RULE: String = "timestampMillisNonZero"

/**
 * The exclusion rule set this build's read seam applies, in force for every full record it
 * produces — one rule today. [com.forager.app.domain.GpxCodec] writes it on the record block so a
 * file states which rules ran, not only which points they caught: without it, a file's **kept**
 * points are the ambiguous ones the day a second rule ships (a point kept under one rule and a
 * point kept under two are different claims, and no per-point attribute can distinguish them after
 * the fact). Provenance cannot be added to a file retroactively, which is why this lands before the
 * first tester walks rather than when a second rule actually arrives.
 *
 * A second rule is added here **and** at whatever seam applies it, together: this list is the
 * declaration, [com.forager.app.domain.model.TrackPointRecord.excludedByRule] is what each point actually met, and a file whose
 * points name a rule this list omits is self-evidently inconsistent — which is the property that
 * makes the two attributes worth carrying separately rather than deriving one from the other.
 */
val NETWORK_FIX_EXCLUSION_RULES: List<String> = listOf(TIMESTAMP_MILLIS_NON_ZERO_RULE)

/** Every stored point that is not a network-provider fix, in stored order. */
fun excludeNetworkProviderFixes(points: List<TrackPoint>): List<TrackPoint> = points.filterNot { it.isNetworkProviderFix() }

/** How many rows the store held for this track before the rule ran. */
val Track.storedPointCount: Int get() = points.size + excludedPointCount

/**
 * The rule did a lot: more than [MOSTLY_NETWORK_FIXES_FRACTION] of at least
 * [MOSTLY_NETWORK_FIXES_MIN_STORED_POINTS] stored points were excluded. Sized against the four
 * evidence tracks, which excluded 47 %, 31 % and 25 % (and 0 % for the control) — the rule working
 * correctly — and against the mechanism: on the owner's device the network provider delivers roughly
 * one fix in three to four (a ~20 s cadence against GPS's ~5 s), so a correctly-behaving device does
 * not approach three in four, while a device whose GNSS clock is not second-aligned excludes every
 * GPS fix and lands at or near 100 %. The minimum stored count stops a three-point track that caught
 * two network fixes from flagging itself, and — for the live recording — stops a cold start, where
 * the network provider typically answers before GPS does, from tripping this on the first poll of
 * every ordinary recording. Both numbers have this reasoning behind them and no data from a
 * non-aligned device yet, which is the point of surfacing it.
 */
fun Track.isMostlyNetworkFixes(): Boolean =
    storedPointCount >= MOSTLY_NETWORK_FIXES_MIN_STORED_POINTS &&
        excludedPointCount.toDouble() / storedPointCount > MOSTLY_NETWORK_FIXES_FRACTION

/** The empty-track case that must never be silent: one or no survivors from at least two stored points. */
fun Track.hasNoUsablePoints(): Boolean = storedPointCount >= 2 && points.size <= 1

/** Either condition under which a surface says what the rule did — see [isMostlyNetworkFixes] and [hasNoUsablePoints]. */
fun Track.networkFixExclusionIsLarge(): Boolean = isMostlyNetworkFixes() || hasNoUsablePoints()

/**
 * The row-state note for a stored track, or `null` in the ordinary case — which is every track on a
 * device whose GPS clock is second-aligned, including the ones the rule quietly cleaned. Appears
 * only when [networkFixExclusionIsLarge]: the ordinary user never sees the words "network fixes".
 */
fun networkFixExclusionNote(track: Track): String? = when {
    track.points.isEmpty() && track.hasNoUsablePoints() ->
        "No usable points — all ${track.excludedPointCount} fixes were from the network provider"
    track.networkFixExclusionIsLarge() -> "${track.excludedPointCount} more not shown (network fixes)"
    else -> null
}

const val MOSTLY_NETWORK_FIXES_FRACTION: Double = 0.75
const val MOSTLY_NETWORK_FIXES_MIN_STORED_POINTS: Int = 10

/**
 * The once-per-recording notice for the live case (owner-corrected copy): true at 76 % excluded,
 * where a quarter of the points still draw, and at 100 %, where none do. Shown through the map's
 * existing Snackbar host, never as a dialog, never repeated within a recording.
 */
const val NETWORK_FIXES_RECORDING_NOTICE: String =
    "Most of this track's fixes look like network fixes rather than GPS, so little or none of it is being drawn. It is still being recorded."
