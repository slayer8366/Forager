package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The map's lifecycle and teardown: one `DisposableEffect`, moved whole from [SightingsMap] by UI split Phase 2.2, PR 9
 * (dispatch 2026-10-19-29), called from the place it stood there and keyed exactly as it was, on [lifecycleOwner].
 * Its dispose stops location updates, destroys the location component and destroys the MapView (`tearDownMap`), and
 * still runs after [MapListeners]'s dispose and before the nudge's, because [SightingsMap] calls this just before
 * [MapListeners] and after the nudge's `DisposableEffect`, as the code stood (Compose forgets in reverse order of
 * composition). The map handle is read through [state] when an event or the dispose runs, as before.
 */
@Composable
internal fun MapLifecycle(
    state: SightingsMapState,
    lifecycleOwner: LifecycleOwner,
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    state.mapView.onResume()
                    state.mapLibreMap?.locationComponent?.onStart()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    state.mapView.onPause()
                    state.mapLibreMap?.locationComponent?.onStop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tearDownMap(object : MapTeardownTarget {
                override fun stopLocationUpdates() {
                    state.mapLibreMap?.locationComponent?.onStop()
                }

                override fun destroyLocationComponent() {
                    state.mapLibreMap?.locationComponent?.onDestroy()
                }

                override fun destroyMapView() = state.mapView.onDestroy()
            })
        }
    }
}
