package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * The route card's one width (dispatch 2026-10-30-08, C16 with C19, RECORD -1946, -1953). C16, the owner: "One width, rows wrap
 * (Recommended)". C19: "Fits a long route, per text size (Recommended)", whose option read "Wide enough for '10 Waypoints routed' and a
 * two-digit mile total at the user's text size (about 246 dp at normal size), never wider than the room beside the bar. It doesn't
 * change while plotting, only if the user changes text size, and normal routes never wrap."
 *
 * So the width is measured from a reference card, never from what the card shows: its total line, "10 Waypoints routed" with a
 * two-digit mile total and the progress mark's room (the mark shows while a leg is in flight), and its buttons, at the current text
 * styles and font scale. It changes with the text size and nothing else. The host caps it at the room beside the bar
 * (`RouteCardPlacement.kt`), and a line wider than the card wraps (C13).
 *
 * The total is the widest a two-digit mile figure prints, 99.9 mi. In kilometres the same route prints "160.8 km", so a metric card
 * is measured on that figure: the same route, in the unit the user reads.
 *
 * Replaced Amendment 16's `rememberRouteCardNeededWidth`, which measured the card's own lines, and with it Amendment 17's held width
 * and easing (C16: "Replaces C13's content width and C14's easing").
 */

/** The reference card's stop count: "10 Waypoints routed". */
internal const val ROUTE_CARD_REFERENCE_STOPS = 10

/** The reference card's total, in metres: 99.9 mi, the widest two-digit mile figure. */
internal const val ROUTE_CARD_REFERENCE_METRES = 99.9 * 1_609.344

@Composable
internal fun rememberRouteCardWidth(distanceUnit: DistanceUnit): Dp {
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(distanceUnit, density, type) {
        fun px(text: String, style: TextStyle): Int = measurer.measure(text, style).size.width
        fun buttonPx(label: String): Int = with(density) {
            val padding = ButtonDefaults.TextButtonContentPadding
            val sides = padding.calculateStartPadding(LayoutDirection.Ltr) + padding.calculateEndPadding(LayoutDirection.Ltr)
            maxOf(px(label, type.labelLarge) + sides.roundToPx(), ButtonDefaults.MinWidth.roundToPx())
        }
        val gapPx = with(density) { Spacing.sm.roundToPx() }
        val markPx = with(density) { ROUTE_CARD_PROGRESS_SIZE.roundToPx() }
        val total = formatDistanceMeters(ROUTE_CARD_REFERENCE_METRES, distanceUnit)
        val totalLine = px(waypointsRoutedText(ROUTE_CARD_REFERENCE_STOPS), type.titleSmall) + gapPx + px(total, type.titleSmall) + gapPx + markPx
        val buttons = buttonPx("Undo") + buttonPx("Cancel") + buttonPx("Save")
        with(density) { (maxOf(totalLine, buttons) + (ROUTE_CARD_SIDE_PADDING * 2).roundToPx()).toDp() }
    }
}
