package com.zynergylabs.forager.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Whether the app holds either runtime location permission, fine or coarse. The one copy of this
 * check (dispatch 2026-09-28-658, scout item R5): `MainActivity`, [AndroidLocationProvider],
 * [AndroidLocationTracker], [AndroidLastKnownLocationSource], `TrackRecordingService` and the map's
 * locate path each had a hand-copied version, line for line the same. One copy changing alone
 * could let the service start without permission, which is the crash below.
 *
 * It lives in the Android layer because it needs a [Context] and [Manifest]; the domain owns
 * neither, which is why it was never in `domain/`.
 *
 * **Why the recording service checks it at all, and not only `MainActivity`** (the reason the
 * service's own copy carried): as of `targetSdk` 34+, starting a `FOREGROUND_SERVICE_TYPE_LOCATION`
 * service without either location permission throws a `SecurityException` from
 * `startForeground`, a confirmed crash (captured stack trace: `startForegroundWithLocationType` ->
 * `startRecording` -> `onStartCommand`, an uncaught `RuntimeException: Unable to start service` on
 * the main thread). `MainActivity` gates on this before it ever sends the start, and again inside
 * the effect that sends it, but the service must never crash however it is told to start, so it
 * checks again for itself.
 */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}
