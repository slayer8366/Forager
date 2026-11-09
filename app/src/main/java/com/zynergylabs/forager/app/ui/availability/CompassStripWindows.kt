package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.text.TextStyle
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading

/*
 * Dispatch 2026-11-05-10, Amendment 1, H7 (RECORD -2181), the owner, verbatim: "Info that changes and shares a line with other text
 * should get its own window so it doesn't shift all text on the same line." Asked where it applies now: "Card and strip now, rest
 * after (Recommended)": "The navigation card and the compass strip (heading, altitude, grid reference) in #498." Then: "They fit now,
 * even at the fullest length of all text."
 *
 * So the strip's readout line gives each changing value a window of its own, sized for the widest it can hold, its content centred
 * in it, and the line is laid out from the windows, never from the values: a heading, a height or a form of the coordinates
 * changing never moves its neighbours, and never changes which readouts the line keeps (readoutsFitBeside decides on the windows).
 * No text is shrunk or cut (C1). The strip is the same in and out of navigation (H1), so the windows are too.
 */

/**
 * The heading's window: the widest heading value ("000°" with the widest compass point, tabular figures: [landscapeHeadingValueWidthPx]),
 * the dash while the compass waits, and the compass words where they are drawn as words ("Compass unavailable", "Compass
 * unreliable"). In a short landscape window those are the crossed-out compass ([iconPx], RECORD -735), which fits within the value.
 */
internal fun stripHeadingWindowPx(widthOf: (String, TextStyle) -> Int, readoutStyle: TextStyle, iconPx: Int?): Int {
    val words = iconPx ?: listOf(TrueHeadingReading.NoSensor, TrueHeadingReading.Unreliable).maxOf { widthOf(stripHeadingText(it), readoutStyle) }
    return maxOf(landscapeHeadingValueWidthPx(widthOf, readoutStyle), widthOf(NO_HEADING_TEXT, readoutStyle), words)
}

/**
 * The widest heights the strip writes in [unitSystem] ([com.zynergylabs.forager.app.domain.model.formatWholeLength]), in tabular
 * figures, where every digit is as wide as any other: five digits of feet (the highest summit is 29 032 ft) or four of metres, and
 * the deepest shores below sea level, one digit fewer with the minus sign.
 */
internal fun widestStripHeights(unitSystem: UnitSystem): List<String> = when (unitSystem) {
    UnitSystem.IMPERIAL -> listOf("88888 ft", "-8888 ft")
    UnitSystem.METRIC -> listOf("8888 m", "-888 m")
}

/** The heading's widest value alone, with no compass words ("000°" and the widest compass point, or the dash): its window's second level. */
internal fun stripHeadingValuesPx(widthOf: (String, TextStyle) -> Int, readoutStyle: TextStyle): Int =
    maxOf(landscapeHeadingValueWidthPx(widthOf, readoutStyle), widthOf(NO_HEADING_TEXT, readoutStyle))

/**
 * The altitude's window, its label included where the line keeps it ([labelPx], "Alt" and its gap; 0 without): [widestPx] holds the
 * widest height and "Alt unavailable" (a status that names itself and wears no label), [valuesPx] the widest height alone.
 */
internal data class StripAltitudeWindow(val widestPx: Int, val valuesPx: Int)

internal fun stripAltitudeWindow(unitSystem: UnitSystem, widthOf: (String, TextStyle) -> Int, readoutStyle: TextStyle, labelPx: Int): StripAltitudeWindow {
    val value = labelPx + widestStripHeights(unitSystem).maxOf { widthOf(it, readoutStyle) }
    return StripAltitudeWindow(widestPx = maxOf(value, widthOf(ELEVATION_UNAVAILABLE_TEXT, readoutStyle)), valuesPx = value)
}

/**
 * One value's window on the strip's line, in pixels: [widestPx], everything it can hold, its compass words or "Alt unavailable" too;
 * [valuesPx], its widest value alone; [actualPx], what it shows now. Never narrower than [actualPx].
 */
internal data class StripWindow(val widestPx: Int, val valuesPx: Int, val actualPx: Int)

/** The windows' widths as laid out, and the gap either side of each dot between them. */
internal data class StripRowLayout(val windowsPx: List<Int>, val gapPx: Int)

/**
 * H7's squeeze, the owner: "If it's too small, then have them squeeze together to make room. It fit before, so it should still fit
 * now." The planner's reading: where the line ([availablePx]) is too short for every window at its widest, each window gives up its slack
 * toward its own text's actual width, sharing the shortfall, and the gaps either side of the dots ([gapPx], Spacing.sm) tighten too,
 * never below [minGapPx] and no window below its text. In two steps, so the line moves as little as it can:
 * 1. the windows give up what only the statuses need (the compass words, "Alt unavailable") down to their widest values, and the gaps
 *    tighten to [minGapPx], all in proportion. Down to here nothing depends on the values shown, so a heading or a height changing
 *    still moves nothing;
 * 2. only where that is still too long, the windows give up the rest toward the values shown, in proportion. Here a value changing
 *    does move its neighbours, as before H7 (the planner: "The heading moves the others only when the strip is short of room").
 * What the line keeps is decided first, on the values as drawn (RECORD -713, [readoutsFitBeside]), so this never drops anything.
 */
internal fun squeezeStripWindows(availablePx: Int, windows: List<StripWindow>, dotPx: Int, gapPx: Int, minGapPx: Int): StripRowLayout {
    val separators = (windows.size - 1).coerceAtLeast(0)
    fun need(widths: List<Int>, gap: Int) = widths.sum() + separators * (dotPx + 2 * gap)
    val widest = windows.map { maxOf(it.widestPx, it.valuesPx, it.actualPx) }
    val values = windows.map { maxOf(it.valuesPx, it.actualPx) }
    val actual = windows.map { it.actualPx }
    fun between(from: List<Int>, to: List<Int>, fromGap: Int, toGap: Int): StripRowLayout {
        val shortfall = need(from, fromGap) - availablePx
        val room = need(from, fromGap) - need(to, toGap)
        val f = if (room <= 0) 1.0 else minOf(1.0, shortfall.toDouble() / room)
        fun cut(a: Int, b: Int) = a - kotlin.math.ceil((a - b) * f).toInt()
        return StripRowLayout(from.zip(to, ::cut), cut(fromGap, toGap))
    }
    return when {
        availablePx == Int.MAX_VALUE || need(widest, gapPx) <= availablePx -> StripRowLayout(widest, gapPx)
        need(values, minGapPx) <= availablePx -> between(widest, values, gapPx, minGapPx)
        else -> between(values, actual, minGapPx, minGapPx)
    }
}

/** H7: the strip's windows, so a test can read where each sits. */
internal const val COMPASS_STRIP_HEADING_WINDOW_TAG = "compass-strip-heading-window"
internal const val COMPASS_STRIP_ELEVATION_WINDOW_TAG = "compass-strip-elevation-window"
internal const val COMPASS_STRIP_COORDINATES_WINDOW_TAG = "compass-strip-coordinates-window"
