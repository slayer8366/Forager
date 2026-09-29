package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import com.zynergylabs.forager.app.ui.map.fanout.FanRect
import com.zynergylabs.forager.app.ui.map.fanout.FanSpace
import com.zynergylabs.forager.app.ui.map.fanout.MapKeepOuts

/*
 * How the map learns where the controls over it are (dispatch 2026-09-28-197, continuation -208): each
 * control reports its own measured bounds, in root px, into a registry the screen provides; the map reads
 * the registry when a stack is tapped, in the map view's own px. Measured, so no layout constant is copied;
 * a plain map read at tap time, so nothing recomposes when a control moves.
 */

/** The registry the controls over a map write to; `null` (no registry) where a map has no such controls to keep clear of. */
val LocalMapKeepOuts = staticCompositionLocalOf<MapKeepOuts?> { null }

/** The names the registered controls go under. */
internal object MapKeepOutIds {
    const val CLUSTER = "icon-cluster"
    const val LEGEND = "legend"
    const val CHIPS = "chip-row"
    const val BOTTOM_NAV = "bottom-nav"
    const val RAIL = "navigation-rail"
    const val TOP_STRIP = "compass-strip-or-hud"
    const val SEARCH_BAR = "search-bar"
}

/**
 * Reports this element's visible bounds in the root, as [id], to [LocalMapKeepOuts] each time it is laid
 * out, and takes it back out when it leaves the composition (a minimised cluster, a legend that is gone).
 */
fun Modifier.mapKeepOut(id: String): Modifier = composed {
    val registry = LocalMapKeepOuts.current
    DisposableEffect(registry, id) { onDispose { registry?.remove(id) } }
    onGloballyPositioned { coordinates ->
        val b = coordinates.boundsInRoot()
        registry?.set(id, FanRect(b.left, b.top, b.right, b.bottom))
    }
}

/**
 * The room a fan has on the map view it is laid over: the view's own size, and the registered controls'
 * bounds moved from root px into the view's px. `SightingsMap` hands one to its tap handler; a test map
 * stub does the same, so both read the registry the same way.
 */
internal class MapFanSpace(private val keepOuts: MapKeepOuts?) : FanSpace {
    var rootOffset: Offset = Offset.Zero
    var size: IntSize = IntSize.Zero

    override fun boundsPx(): FanRect? = if (size == IntSize.Zero) null else FanRect(0f, 0f, size.width.toFloat(), size.height.toFloat())

    override fun keepOutsPx(): List<FanRect> =
        keepOuts?.snapshot()?.values.orEmpty().map { FanRect(it.left - rootOffset.x, it.top - rootOffset.y, it.right - rootOffset.x, it.bottom - rootOffset.y) }
}

@Composable
internal fun rememberMapFanSpace(): MapFanSpace {
    val keepOuts = LocalMapKeepOuts.current
    return remember(keepOuts) { MapFanSpace(keepOuts) }
}

/** Keeps [space] told where the map view is and how big. */
internal fun Modifier.trackMapFanSpace(space: MapFanSpace): Modifier = onGloballyPositioned {
    space.rootOffset = it.positionInRoot()
    space.size = it.size
}
