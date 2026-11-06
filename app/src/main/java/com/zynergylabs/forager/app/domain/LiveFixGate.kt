package com.zynergylabs.forager.app.domain

/**
 * The live fix's accuracy gate — location-accuracy dispatch, item 1.
 *
 * The recording path has always refused fixes worse than its mode's ceiling before they become
 * track points ([LocationSampler]). The *live* fix — the one the navigation HUD, the compass strip
 * and the coordinates readout all derive from — had no gate at all, so a 60 m fix under canopy was
 * accepted, displayed, and used for distance and bearing, yanking the readout around when the
 * previous, better fix was more useful. This is the missing gate: applied once, in
 * `AvailabilityViewModel`'s live-fix collector, and nowhere else. **Recording keeps its own rule.**
 *
 * ## The threshold
 *
 * [LIVE_FIX_MAX_ACCURACY_METERS] is 50 m — the same number as
 * [com.zynergylabs.forager.app.domain.model.TrackRecordingMode.BALANCED]'s ceiling, **deliberately not a
 * reference to it.** The two gates answer different questions: what is worth persisting, and what
 * is worth showing. The recording mode is the user's battery/density choice and changes per track;
 * the display gate must not move when the user picks BATTERY_SAVER (100 m). Why 50 and not
 * tighter: below 50 m everything the HUD derives is already degraded past use — the "Approaching"
 * band is twice accuracy, the needle at 100 m from the target swings through most of a quadrant,
 * and the coordinates row would print a 1 m MGRS square for a position known to 60 m — while a
 * tighter number (30 m) would blank the HUD under exactly the canopy the owner is testing in. Why
 * not looser: showing positions the track would refuse is the inconsistency this exists to remove.
 *
 * ## `null` accuracy passes
 *
 * Null means "not reported", not "bad". Rejecting it would treat a missing number as worse than
 * 50 m, which is a fabricated judgement — the same rule [LocationSampler.shouldAccept] applies and
 * the same reading [isApproaching] gives a missing accuracy ("no basis for the word").
 *
 * ## The held fix ages, and that is intended (owner decision)
 *
 * A rejected fix is dropped and the previous accepted fix is held. A held fix ages: under canopy
 * delivering only 60–80 m fixes, the HUD reads "Last fix 45 s ago" from 30 s and withholds the
 * distance with "No fix for 5 min" from five minutes — **while the radio is alive and fixes are
 * arriving.** That is the honest reading: the app genuinely has not had a usable position in that
 * time, and saying so beats showing a 60 m fix as current.
 *
 * **The alternative was considered and refused.** Letting a rejected fix through once the held one
 * has gone stale ("the best fix in the last 30 s") looks more helpful and someone will propose it
 * again. It trades an honest silence for a confident lie: a 60 m position drawn with the same
 * needle, the same distance, the same 1 m grid square as a 6 m one, with nothing on screen to say
 * it is worse. That is the pattern this app has been removing everywhere else — "Approaching"
 * rather than "arrived", the needle suppressed near the target rather than smoothed into a stable
 * wrong direction. Do not add it here without field data showing the silence is the bigger harm.
 *
 * A future Kalman filter, if one is ever built, goes **behind** this gate, never in front of it: a
 * filter fed rejected fixes would smooth a bad position into a confident one.
 *
 * ## Only a GPS fix passes (dispatch 2026-09-28-527)
 *
 * The gate tested accuracy alone until -527, so a network fix of 50 m or better became the live fix:
 * "Arrived", the waypoint's line and a new find's location all read it. The S22 gave network fixes as
 * good as 15.2 m at a desk. Now a fix passes only if its [LocationFix.Update.provider] is GPS
 * ([mayAct]) **and** its accuracy clears the threshold; a network or unknown fix is refused whatever its
 * accuracy, and is shown as -510's approximate position, never acted on. The owner (RECORD -519):
 * "removing reliance on network data, but not total removal of collection at all." The threshold's
 * value is unchanged. Judging GPS fixes by satellite status is not here (RECORD -526).
 *
 * ## What the accuracy field carries on the owner's device
 *
 * **Superseded in part.** The instrument walk of 2026-09-07 found every GPS fix reporting `3.7900925`,
 * 289 of 289 (`docs/audits/2026-09-07-fix-log-walk-findings.md`), and this comment then said the field
 * "carries no signal on that device's GPS path" and that "this gate never rejects a GPS fix". Both are
 * wrong indoors. Counted from the S22's own `ForagerFix` logs (distinct fixes, not log lines; the
 * counts are `docs/navigation/2026-10-05-fix-provider-report.md`, item 5):
 * - **Outdoors**, three walks on 2026-10-03: 1,735 GPS fixes, 1,615 of them at exactly 3.79 m, the rest
 *   up to 20.1 m. So 3.79 m is that device's best case outdoors, not a constant.
 * - **Indoors**, one desk session on 2026-10-04: 1,514 GPS fixes from 18.7 m to 267.7 m, 1,292 of them
 *   worse than 50 m, so this gate refuses most GPS fixes there. 252 network fixes, 15.2 m to 100 m.
 *
 * The limits: one phone, one desk, three walks. The field does vary on the GPS path, but whether it is
 * calibrated (a fix claiming 20 m being within 20 m) is not known, so the advice stands not to build an
 * uncertainty calibration on it. The same field feeds `formatDistanceWithAccuracy`'s "within" circle,
 * `LocationSampler`'s recording ceiling and `isApproaching`'s threshold, which *decides* from it.
 */
const val LIVE_FIX_MAX_ACCURACY_METERS = 50f

/** `true` if [candidate] may become the live fix — see the file's doc comment for every rule and the reasoning. */
fun acceptLiveFix(candidate: LocationFix.Update, maxAccuracyMeters: Float = LIVE_FIX_MAX_ACCURACY_METERS): Boolean {
    if (!candidate.provider.mayAct) return false
    val accuracy = candidate.accuracyMeters ?: return true
    return accuracy <= maxAccuracyMeters
}
