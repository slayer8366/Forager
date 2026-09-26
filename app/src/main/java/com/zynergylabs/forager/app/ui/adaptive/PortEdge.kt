package com.zynergylabs.forager.app.ui.adaptive

import android.content.res.Configuration
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.log.currentDisplayRotation

/**
 * Which window edge the device's charger-port edge is on — the one rotation-to-edge mapping in
 * the app, used by the in-app camera ([com.zynergylabs.forager.app.ui.log.cameraArrangement]
 * calls this) and by the main window's navigation rail (`docs/plans/landscape-phone-design.md`,
 * P2 and Resolution R16).
 *
 * The mapping and the assumption in it are the camera's, moved here unchanged rather than written
 * a second time: Android exposes no API for where the port is, only the display's rotation from
 * the natural orientation, so this maps the **natural-orientation bottom edge** and assumes the
 * port is on it — true of a portrait-natural phone, which is what this app targets. At
 * `ROTATION_90` that edge is the window's right, at `ROTATION_270` its left. A landscape-natural
 * device would invert it. The camera's doc comment (`CameraArrangement.kt`) has the device-check
 * history behind it. The capture of 2026-09-26 found the system 3-button bar on this same edge at
 * both landscape rotations (`docs/audits/assets/2026-09-26-landscape-capture/r1-` and
 * `r3-window-displays.txt`), which is why the rail goes here.
 *
 * A window that is not landscape has its port at the bottom. Unrecognised rotations in a
 * landscape window fall to [ScreenEdge.Right], as the camera's mapping always has.
 */
internal fun portEdgeFor(windowIsLandscape: Boolean, displayRotation: Int): ScreenEdge = when {
    !windowIsLandscape -> ScreenEdge.Bottom
    displayRotation == Surface.ROTATION_270 -> ScreenEdge.Left
    else -> ScreenEdge.Right
}

/** The punch-hole edge: the port edge's opposite, by the device anatomy [portEdgeFor] assumes. */
internal fun punchHoleEdgeFor(windowIsLandscape: Boolean, displayRotation: Int): ScreenEdge =
    portEdgeFor(windowIsLandscape, displayRotation).opposite

/**
 * [portEdgeFor] for the current window: landscape is `LocalConfiguration.orientation ==
 * ORIENTATION_LANDSCAPE`, the camera's own test (Resolution R15), and the rotation is the
 * display's, read the way the camera reads it ([currentDisplayRotation]).
 */
@Composable
internal fun currentWindowPortEdge(): ScreenEdge = portEdgeFor(
    windowIsLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
    displayRotation = currentDisplayRotation(),
)
