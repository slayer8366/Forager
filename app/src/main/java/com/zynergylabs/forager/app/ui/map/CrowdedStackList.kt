package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.MapPalette

/** The crowded stack's list, for tests. */
internal const val CROWDED_LIST_TAG = "crowded-stack-list"

/** A row of the crowded stack's list, for tests. */
internal fun crowdedListRowTag(key: FanKey): String = "crowded-stack-row-${key.layerId}-${key.featureId}"

/** The list's heading: how many records share the spot. */
internal fun crowdedListHeading(count: Int): String = if (count == 1) "1 record at this spot" else "$count records at this spot"

/**
 * The list a crowded stack becomes when it is still on one spot at the closest zoom (dispatch 2026-10-27-07, step 4): each
 * row the record's map icon, its name and its date ([CrowdedListRow]); a tap on a row is [onRow].
 *
 * **Map chrome**, so its fill is the map's 80% ([MAP_CHROME_OVER_MAP_ALPHA], the bubble's and the icon bar's colour), one
 * surface with nothing filled under or over it, and its content opaque (the owner: "nothing should fully obstruct the map
 * view"). **Bounded** to its own size (at most [CROWDED_LIST_MAX_WIDTH] wide and [CROWDED_LIST_MAX_HEIGHT] tall, the rows
 * scrolling past that), so it takes touches only where it is drawn and a long-press on the map beside it still reaches the
 * map (CLAUDE.md, the Surface pitfall).
 */
@Composable
internal fun CrowdedStackList(rows: List<CrowdedListRow>, night: Boolean, onRow: (FanKey) -> Unit, modifier: Modifier = Modifier) {
    val fill = if (LocalForagerDarkTheme.current) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    val content = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current.density
    val icons: Map<MarkerIcon, ImageBitmap> = remember(night, density) {
        val palette = MapPalette.forMode(night)
        CROWDED_LIST_ICONS.associateWith { markerIconImage(it, palette, density).bitmap.asImageBitmap() }
    }
    Surface(
        modifier = modifier
            .widthIn(max = CROWDED_LIST_MAX_WIDTH)
            .heightIn(max = CROWDED_LIST_MAX_HEIGHT)
            .mapChromeContainerColor(fill)
            .testTag(CROWDED_LIST_TAG),
        shape = RoundedCornerShape(16.dp),
        color = fill,
        contentColor = content,
    ) {
        Column(Modifier.mapChromeContentColor(content)) {
            Text(
                crowdedListHeading(rows.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            LazyColumn {
                items(rows, key = { "${it.key.layerId}/${it.key.featureId}" }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onRow(row.key) }
                            .testTag(crowdedListRowTag(row.key))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        icons[row.icon]?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                        Column {
                            Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.date?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

/** The icons a row can show: the four kinds that fan ([com.zynergylabs.forager.app.ui.map.fanout.fanOutLayerIds]). */
private val CROWDED_LIST_ICONS = listOf(MarkerIcon.FIND, MarkerIcon.PHOTO, MarkerIcon.WAYPOINT, MarkerIcon.PLANNED_TRIP)

/** The list's widest. */
internal val CROWDED_LIST_MAX_WIDTH = 360.dp

/** The list's tallest: about four rows and the heading, so most of the map stays in view. */
internal val CROWDED_LIST_MAX_HEIGHT = 300.dp
