package com.zynergylabs.forager.app.ui.log

import android.view.Surface

/**
 * Where the device's **punch-hole (top) edge** lands on screen for a window at [displayRotation],
 * written out as a table independent of [punchHoleEdge], which is what the strip tests check against.
 *
 * Source: the platform itself, not this app's code. On the API 36 AVD (`pixel_7` profile, a centred
 * punch-hole on the natural top edge) the system reported the cut-out's bounding rect at
 * (480, 0)–(625, 136) at rotation 0, (0, 455)–(136, 600) at rotation 1, and (2264, 480)–(2400, 625)
 * at rotation 3, read from `dumpsys window displays` on 2026-09-18. So the natural top is the
 * screen's top, left and right respectively. Rotation 2 follows from the other three, since the
 * AVD never put a window there to read.
 */
internal fun deviceTopEdgeOnScreen(displayRotation: Int): ScreenEdge = when (displayRotation) {
    Surface.ROTATION_0 -> ScreenEdge.Top
    Surface.ROTATION_90 -> ScreenEdge.Left
    Surface.ROTATION_180 -> ScreenEdge.Bottom
    Surface.ROTATION_270 -> ScreenEdge.Right
    else -> error("not a Surface.ROTATION_* value: $displayRotation")
}
