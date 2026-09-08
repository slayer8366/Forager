package com.forager.app.domain

import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackRecordingMode

/**
 * One [MovingPace] evaluation as a single machine-readable line — Pass 2 of the return-estimate
 * device checks, the Doppler-versus-differencing comparison observed on a real track for the first
 * time. Emitted by `TrackRecordingViewModel`'s 15 s poll through [PaceLog]; the format, the replay
 * contract and the dedupe keys are in
 * `docs/audits/2026-09-08-pass2-speed-comparison-log-line-prebuild-report.md`, §D and §E.
 *
 * **Grammar.** Space-separated `key=value` pairs in a fixed order, `v=1` first, the same shape as
 * the `ForagerFix` per-fix log so one parser serves both with a tag filter. No prose. Doubles are
 * printed by `Double.toString()` — locale-independent, and the shortest digits that round-trip, so a
 * replay of the same points through [movingPace] reproduces every numeric field **exactly**; a
 * fixed-decimal format would be lossy and locale-dependent, and a tolerance in the parser would hide
 * a different point set. The literal `null` appears on exactly the fields listed below and nowhere
 * else; the instrument walk's first parser rejected every `speed=null` line and "confirmed" a
 * correlation on the survivors (CLAUDE.md, Testing), so which fields may be `null` is stated here,
 * not left to be discovered.
 *
 * **Fields, in order.** `v`; `track` ([Track.id]); `call` (per-recording counter from 1, the caller's);
 * `at` (the caller's clock at emission); `mode` (the active [TrackRecordingMode]); `points` /
 * `stored` / `excluded` (the filtered list, the rows the store held, the difference — the check that
 * the record saw the read seam's output and not the raw rows); `first` / `last` / `wallMs` (the
 * kept points' first and last timestamps and their difference — **`null` with no points**, never a
 * zero standing in for a timestamp); `examined` / `moving` ([MovingPace.intervalsExamined] /
 * [MovingPace.intervalsMoving]); `movingMs` / `movingM` / `diffAll` (all moving intervals: duration,
 * differenced distance, and [MovingPace.differencingSpeed], **`null` with no moving interval**);
 * `dopplerMs` / `dopplerM` (the Doppler window's duration and differenced distance); `doppler` /
 * `diffSame` / `ratio` ([MovingPace.comparison]: both instruments over that same window and their
 * ratio — **`null`, all three together, until one counted sample exists**); `counted` / `nullSpeed` /
 * `belowFloor` (the three sample counts, which sum to `moving`); `dopplerBar` / `diffBar` (each
 * instrument's own moving time against [MEASURED_PACE_MIN_MOVING_MILLIS] — both, explicitly,
 * because [MovingPace.source] collapses the disagreement case the comparison exists to surface);
 * `source` ([MovingPace.source], in scope by the owner's ruling of 2026-09-08); `floor` / `barMs`
 * (the two constants, so a record from a later tuning is self-describing).
 *
 * **Identities a parser checks before trusting a record:** `points - 1 = examined` (or both `0`),
 * `moving = counted + nullSpeed + belowFloor`, `examined - moving` = intervals skipped as stopped or
 * non-positive. A record that does not balance is a parser bug or a replay on the wrong point set.
 */
fun MovingPace.toLogRecord(track: Track, call: Int, atEpochMillis: Long, mode: TrackRecordingMode): String {
    val points = track.points
    val first = points.firstOrNull()?.timestampEpochMillis
    val last = points.lastOrNull()?.timestampEpochMillis
    val wallMillis = if (first != null && last != null) last - first else null
    val comparison = comparison
    return listOf(
        "v" to "1",
        "track" to track.id,
        "call" to call.toString(),
        "at" to atEpochMillis.toString(),
        "mode" to mode.name,
        "points" to points.size.toString(),
        "stored" to track.storedPointCount.toString(),
        "excluded" to track.excludedPointCount.toString(),
        "first" to first.toString(),
        "last" to last.toString(),
        "wallMs" to wallMillis.toString(),
        "examined" to intervalsExamined.toString(),
        "moving" to intervalsMoving.toString(),
        "movingMs" to movingMillis.toString(),
        "movingM" to movingMeters.toString(),
        "diffAll" to differencingSpeed.toString(),
        "dopplerMs" to dopplerMovingMillis.toString(),
        "dopplerM" to dopplerMovingMeters.toString(),
        "doppler" to comparison?.dopplerSpeed.toString(),
        "diffSame" to comparison?.differencingSpeed.toString(),
        "ratio" to comparison?.ratio.toString(),
        "counted" to pointsCounted.toString(),
        "nullSpeed" to pointsWithoutSpeed.toString(),
        "belowFloor" to pointsBelowFloor.toString(),
        "dopplerBar" to (dopplerMovingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS).toString(),
        "diffBar" to (movingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS).toString(),
        "source" to source.name,
        "floor" to MOVING_SPEED_FLOOR_METERS_PER_SECOND.toString(),
        "barMs" to MEASURED_PACE_MIN_MOVING_MILLIS.toString(),
    ).joinToString(" ") { (key, value) -> "$key=$value" }
}
