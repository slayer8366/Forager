package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember

/*
 * A saved route's selected stop in an open fan (dispatch 2026-11-06-05, Amendment 6 B). The owner: "When fanning out, the highlighted
 * icon stays put while the icon it represents is in the fan. Add the highlight to the fan without any duplicates". While a stack is
 * fanned, the selected stop's accent (its pin or flag in cream by day, rust at night: PlottedRouteLayers.kt) follows its pin to its
 * fanned place, and none is left at the stack's centre; when the fan closes, it is back on the pin in its place.
 *
 * How: the fan's copy of that marker is drawn with the accent image instead of its own ([fanCopyIcon]), and the accent at the centre is
 * hidden by the same filter, at the same moment, as the fanned originals ([fanHiddenAccentKeys], applied in `applyFanOutHiding`), so it
 * goes and comes back with the pin it stands on. Rejected: moving the accent's own point with the fan at every frame, which would draw
 * the accent from a second source beside the copy, so for a frame either could be ahead of the other (the blink the hide gate exists
 * for, amendment -371). Plain functions, no map object, so a headless test reads exactly what the map is given.
 */

/** The accent feature's property naming the marker it stands on, as [fanAccentKeyValue] writes it. */
internal const val FAN_ACCENT_KEY_PROPERTY = "fanKey"

/** [key] as one string, its layer and its id: a waypoint and a trip may share an id, so the id alone would not do. */
internal fun fanAccentKeyValue(key: FanKey): String = "${key.layerId}/${key.featureId}"

/** The accent keys the accent layer hides while [members] are fanned (Amendment 6 B): every member's, empty when nothing is fanned. */
internal fun fanHiddenAccentKeys(members: List<FanMember>): List<String> = emptyList()

/** The image a fanned copy of [key] on [layerId] draws: its layer's own, or the accent's when it is the selected stop's [accent]. */
internal fun fanCopyIcon(layerId: String, key: FanKey, accent: FanKey?): MarkerIcon? = markerIconForLayer(layerId)
