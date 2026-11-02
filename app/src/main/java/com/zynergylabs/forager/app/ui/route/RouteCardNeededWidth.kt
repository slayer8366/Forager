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
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * How wide the route card's lines want to be on one line each (dispatch 2026-10-30-08, Amendment 16, RECORD -1932). The owner: "only
 * give enough room to display the info".
 *
 * Measured from the words, not by letting the card wrap its content, because the card must keep one width while the handle slides
 * the rows up and down, so the total line stays put (Amendment 12): the rows count at peek, where they are not composed and an
 * intrinsic width could not see them. For the same reason the total line always counts its progress mark, which comes and goes
 * with every leg.
 *
 * Each line here mirrors one in `RouteCard.kt`, with the same style and gaps. `RouteCardContentWidthTest` holds the two together
 * against what is drawn, under native graphics.
 */

@Composable
internal fun rememberRouteCardNeededWidth(content: RouteCardContent, distanceUnit: DistanceUnit): Dp {
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(content, distanceUnit, density, type) {
        fun px(text: String, style: TextStyle): Int = measurer.measure(text, style).size.width
        fun buttonPx(label: String): Int = with(density) {
            val padding = ButtonDefaults.TextButtonContentPadding
            val sides = padding.calculateStartPadding(LayoutDirection.Ltr) + padding.calculateEndPadding(LayoutDirection.Ltr)
            maxOf(px(label, type.labelLarge) + sides.roundToPx(), ButtonDefaults.MinWidth.roundToPx())
        }
        val gapPx = with(density) { Spacing.sm.roundToPx() }
        val markPx = with(density) { ROUTE_CARD_PROGRESS_SIZE.roundToPx() }
        val unitSystem = UnitSystem.forDistanceUnit(distanceUnit)
        val lines = buildList {
            when (content) {
                is RouteCardContent.Saved -> {
                    add(px(content.name, type.titleSmall))
                    add(buttonPx("Directions"))
                }
                is RouteCardContent.Plotting -> add(buttonPx("Undo") + buttonPx("Cancel") + buttonPx("Save"))
            }
            content.legs.forEachIndexed { i, leg ->
                add(px(routeLegText(content.stops[i].name, content.stops[i + 1].name), type.bodyMedium) + gapPx + px(formatDistanceMeters(leg.meters, distanceUnit), type.bodyMedium))
                content.legElevations.getOrNull(i)?.let { add(px(routeElevationText(it, unitSystem), type.bodySmall)) }
                if (leg is RouteLeg.Straight) add(px(NO_TRAIL_DATA_NOTE, type.bodySmall))
            }
            if (content.legInFlight) {
                val i = content.legs.size
                add(px(routeLegText(content.stops[i].name, content.stops[i + 1].name), type.bodyMedium) + gapPx + markPx)
            }
            if (content.legs.isNotEmpty()) {
                val total = routeTotalDistanceText(content.legs.map { it.meters }, distanceUnit)
                add(px(waypointsRoutedText(content.legs.size + 1), type.titleSmall) + gapPx + px(total, type.titleSmall) + gapPx + markPx)
                routeTotalElevationText(content.legElevations, unitSystem)?.let { add(px(it, type.bodySmall)) }
            } else if (!content.legInFlight) {
                add(px(ONE_WAYPOINT_SELECTED, type.bodyLarge))
            }
        }
        with(density) { (lines.max() + (ROUTE_CARD_SIDE_PADDING * 2).roundToPx()).toDp() }
    }
}
