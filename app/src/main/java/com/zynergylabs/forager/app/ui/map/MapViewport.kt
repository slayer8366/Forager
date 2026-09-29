package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Calls [onResized] with the new size each time this node's laid-out size changes after its first
 * measurement (the first is not a change, and a recomposition at the same size reports nothing).
 *
 * Why it exists (Part 2 follow-ups F1 item 1; Part 2 Session 1, item 5): `SightingsMap` re-anchors an open
 * bubble to its glyph in `addOnCameraIdleListener`, and a device rotation resizes the map view without any
 * camera idle, so the bubble stayed at its portrait place until the next pan and overlapped the cluster.
 * The map container carries this modifier and re-anchors when it fires. The signal is the map's own size
 * rather than a configuration value, so a fold, a split-screen change or a window resize re-anchors too.
 */
@Composable
internal fun Modifier.onViewportResized(onResized: (IntSize) -> Unit): Modifier {
    var last by remember { mutableStateOf<IntSize?>(null) }
    val current by rememberUpdatedState(onResized)
    return this.onSizeChanged { size ->
        val previous = last
        last = size
        if (previous != null && previous != size) current(size)
    }
}
