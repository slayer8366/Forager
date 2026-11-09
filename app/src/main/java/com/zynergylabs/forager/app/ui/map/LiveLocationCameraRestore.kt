package com.zynergylabs.forager.app.ui.map

import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.OnLocationCameraTransitionListener

/**
 * dot-lift-after-recreate (RECORD -2183, -2192; the S22 on build 4990): the camera mode an activation sets, as
 * `locationComponent.cameraMode = mode` set it (the same call: `setCameraMode(mode, listener)` over MapLibre's default transition),
 * with [onTransitionEnded] told when the location component's own transition for it has ended, finished or cancelled; at once when
 * it runs none.
 *
 * Why it is needed: from not following with a known location, a tracking mode runs that transition (an animated move, 750 ms),
 * and while it runs MapLibre refuses `paddingWhileTracking` and `tiltWhileTracking` (13.5.0 bytecode). A recreated map whose first
 * style load restores following applies the navigation view inside it, so the view's padding and tilt are refused; the navigation
 * view applies them again when told the transition has ended (`NavigationModeChange.onLocationTransitionEnded`). The listener is
 * called after MapLibre has marked the transition over, so what it asks for is not refused for the same reason.
 */
internal fun setLiveLocationCameraMode(component: LocationComponent, mode: Int, onTransitionEnded: () -> Unit) {
    component.setCameraMode(
        mode,
        object : OnLocationCameraTransitionListener {
            override fun onLocationCameraTransitionFinished(cameraMode: Int) = onTransitionEnded()

            override fun onLocationCameraTransitionCanceled(cameraMode: Int) = onTransitionEnded()
        },
    )
}
