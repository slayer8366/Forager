package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor

internal const val RETURN_TO_ROUTE_TAG = "return-to-route"

/** The owner's words (dispatch 2026-09-28-430): "Have a "Return to Route" button appear when panning away". */
internal const val RETURN_TO_ROUTE_TEXT = "Return to Route"

/** The pill's least height: the minimum touch target, laid out, so what a finger reaches is what is drawn. */
internal val RETURN_TO_ROUTE_PILL_HEIGHT: Dp = 48.dp

/**
 * How far the map's snackbars rise while the pill shows, so a trip-start or network notice sits
 * above it rather than over it (continuation 2026-09-28-432): the pill's own clearance above the
 * nav, its height, and a gap.
 */
internal val RETURN_TO_ROUTE_SNACKBAR_LIFT: Dp = LEGEND_ATTRIBUTION_CLEARANCE + RETURN_TO_ROUTE_PILL_HEIGHT + Spacing.sm

/**
 * "Return to Route" (dispatch 2026-09-28-430, plan task T22): shown while navigating once the user
 * has moved the map away from the navigation view (a drag, or "Reset orientation"); a tap brings
 * the tilted, facing-up view back and it follows again. Content-width at bottom centre, so it
 * covers only what it draws (CLAUDE.md, the `Surface` pitfall), with its fill at
 * [com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA] and its content opaque, as every
 * surface over the map ("Nothing fully obstructs the map view"). The snackbar's colour roles, the
 * map's other bottom chrome.
 */
@Composable
internal fun ReturnToRoutePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val content = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = fill,
        contentColor = content,
        modifier = modifier
            .heightIn(min = RETURN_TO_ROUTE_PILL_HEIGHT)
            .testTag(RETURN_TO_ROUTE_TAG)
            .mapChromeContainerColor(fill)
            .mapChromeContentColor(content),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = RETURN_TO_ROUTE_TEXT, style = MaterialTheme.typography.labelLarge)
        }
    }
}
