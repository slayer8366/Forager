package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * The quick menu's "Battery saver" switch (dispatch 2026-09-28-767, plan task T17; the owner's answers,
 * RECORD -765, -766 and -768). On, the phone is asked for a position every
 * [BATTERY_SAVER_FIX_INTERVAL_MILLIS] instead of every [FIX_INTERVAL_MILLIS], by every part of the app
 * that asks (the owner: "Slow everything, every 5 s (Recommended)"). Off by default.
 *
 * **Why everything, not only the recording service.** The platform serves a provider at the fastest
 * interval any registration asks for, and the recording screen's own registration lasts the whole
 * recording, screen off included (`TrackRecordingViewModel.onLeftForeground`'s doc comment). A slower
 * rate in the service alone would change nothing on the phone while the app was open. Rejected:
 * releasing that registration in the background, a structural change ruled out of scope before.
 *
 * **What is kept is unchanged.** Recording still samples at
 * [com.zynergylabs.forager.app.domain.model.TrackRecordingMode.HIGH_ACCURACY]; the mode enum's own
 * `BATTERY_SAVER` value, which changes what is kept and the accuracy limit and not the request rate,
 * is deliberately not used.
 *
 * **The cost, accepted by the owner** ("Accept up to 4 s (Recommended)", RECORD -768): the off-track
 * alert needs at least 3 GPS readings over at least 15 s ([OFF_TRACK_MIN_READINGS],
 * [OFF_TRACK_HOLD_MILLIS]), so at 5 s it can come up to about 4 s later than at 1 s; arrival is noticed
 * up to 5 s later. 5 s is the slowest rate that keeps three readings inside the 15 s hold, and well
 * under [STALE_AFTER_MILLIS], so nothing reads as stale between fixes.
 *
 * **No saving is claimed here.** Whether 5 s saves battery on a given phone depends on its GNSS chip;
 * the S22 measurement is in `docs/navigation/2026-10-09-battery-saver-report.md`.
 *
 * DataStore, not Room: a flat preference nothing joins to.
 */
interface BatterySaverPreferenceRepository {

    /**
     * The switch as last read or stored. Off until the store has been read. The location tracker
     * follows it, re-registering at the new interval the moment it changes, mid-recording included.
     */
    val enabled: StateFlow<Boolean>

    suspend fun getEnabled(): Result<Boolean>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}

/** How often the phone is asked for a position with Battery saver off. */
const val FIX_INTERVAL_MILLIS = 1_000L

/** How often the phone is asked for a position with Battery saver on. The owner: "every 5 s". */
const val BATTERY_SAVER_FIX_INTERVAL_MILLIS = 5_000L

/** The interval the phone is asked at, for the switch's state. */
fun fixIntervalMillis(batterySaverOn: Boolean): Long =
    if (batterySaverOn) BATTERY_SAVER_FIX_INTERVAL_MILLIS else FIX_INTERVAL_MILLIS
