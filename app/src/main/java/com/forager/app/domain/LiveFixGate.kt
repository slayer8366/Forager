package com.forager.app.domain

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
 * [com.forager.app.domain.model.TrackRecordingMode.BALANCED]'s ceiling, **deliberately not a
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
 * ## What the accuracy field carries on the owner's device (instrument walk, 2026-09-07)
 *
 * On the owner's phone every GPS-provider fix reports the same horizontal accuracy, `3.7900925`,
 * 289 times out of 289 in a 4.8-minute walk under a 1 Hz cadence — identical to seven decimal
 * places, not clustered. That is a placeholder, not a measurement: the field carries no signal
 * on that device's GPS path. **So on that device this gate never rejects a GPS fix**, whatever
 * the sky, and it is not doing for GPS the work the sections above describe. It still does real
 * work on network-provider fixes, whose accuracy there genuinely varies (12.5–71.7 m on the
 * same walk), which is why it stays. The same field feeds `formatDistanceWithAccuracy`'s
 * "within" circle and `isApproaching`'s threshold, and `LocationSampler`'s recording ceiling —
 * all four read a constant on that hardware. Confirmed on one device only; whether it is the
 * chipset, the vendor's GNSS stack or that build is the beta's question (the trip report asks
 * it). Do not tune this threshold, or build an uncertainty calibration on reported accuracy,
 * until a device is known to report a varying value. Record:
 * `docs/audits/2026-09-07-fix-log-walk-findings.md`.
 */
const val LIVE_FIX_MAX_ACCURACY_METERS = 50f

/** `true` if [candidate] may become the live fix — see the file's doc comment for every rule and the reasoning. */
fun acceptLiveFix(candidate: LocationFix.Update, maxAccuracyMeters: Float = LIVE_FIX_MAX_ACCURACY_METERS): Boolean {
    val accuracy = candidate.accuracyMeters ?: return true
    return accuracy <= maxAccuracyMeters
}
