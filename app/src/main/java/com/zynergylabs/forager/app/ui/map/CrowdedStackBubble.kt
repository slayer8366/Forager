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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The crowded stack's list, its bubble's card, for tests. */
internal const val CROWDED_LIST_TAG = "crowded-stack-list"

/** The crowded stack's bubble's close button, for tests (dispatch 2026-11-04-02). */
internal const val CROWDED_LIST_CLOSE_TAG = "crowded-stack-list-close"

/** A row of the crowded stack's list, for tests. */
internal fun crowdedListRowTag(key: FanKey): String = "crowded-stack-row-${key.layerId}-${key.featureId}"

/** The list's heading: how many records share the spot. */
internal fun crowdedListHeading(count: Int): String = if (count == 1) "1 record at this spot" else "$count records at this spot"

/**
 * The tallest the crowded stack's bubble's card is (dispatch 2026-11-04-02, S1, RECORD -2040: "the tallest standard bubble,
 * measured and kept as a constant, never more than the room on screen"). The standard bubble has no height limit of its own,
 * so this is the Maps tab's waypoint bubble with every line and action it can carry there, measured at font 1.0 on the S22's
 * portrait window, under native graphics, in the app's theme: 272.67 dp in CI run 41782753215 (`CrowdedBubbleCapTest`, which
 * fails if the two part).
 */
internal val CROWDED_BUBBLE_MAX_CARD_HEIGHT = 272.67.dp

/**
 * The list a crowded stack becomes (dispatch 2026-10-27-07, step 4; List mode, 2026-10-27-14), as a floating bubble since
 * dispatch 2026-11-04-02 (the owner: "The list mode for map icons is a screen hog. Can it be a floating bubble that follows the
 * icon cluster on the map, and adjust its height to the standard bubble size to have it fit easier?").
 *
 * The standard bubble's own card ([MapBubbleShell]): its 80% fill, its tail to the stack ([tipInBubble]), its X ([onDismiss]),
 * at most 280 dp wide. The heading, "N records at this spot", beside the X, then the rows, each the record's map icon, its name
 * and its date ([CrowdedListRow]); a tap on a row is [onRow]. The card is no taller than [maxCardHeight], and the rows scroll
 * inside it when they do not fit. Bounded to its own size, so it takes touches only where it is drawn (CLAUDE.md, the Surface
 * pitfall); the shell consumes its own taps, so a touch on it never reaches the map.
 */
@Composable
internal fun CrowdedStackBubble(
    rows: List<CrowdedListRow>,
    night: Boolean,
    tipInBubble: State<Offset?>,
    maxCardHeight: Dp,
    onRow: (FanKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current.density
    val icons: Map<MarkerIcon, ImageBitmap> = remember(night, density) {
        val palette = MapPalette.forMode(night)
        CROWDED_LIST_ICONS.associateWith { markerIconImage(it, palette, density).bitmap.asImageBitmap() }
    }
    MapBubbleShell(
        tipInBubble = tipInBubble,
        onDismiss = onDismiss,
        cardTag = CROWDED_LIST_TAG,
        closeTag = CROWDED_LIST_CLOSE_TAG,
        // The shell pads its card by the tail's length on every side; the cap is the card's.
        modifier = Modifier.heightIn(max = maxCardHeight + BUBBLE_TAIL_LENGTH * 2),
    ) {
        Text(crowdedListHeading(rows.size), style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(rows, key = { "${it.key.layerId}/${it.key.featureId}" }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onRow(row.key) }
                        .testTag(crowdedListRowTag(row.key))
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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

/** The icons a row can show: the four kinds that fan ([com.zynergylabs.forager.app.ui.map.fanout.fanOutLayerIds]). */
private val CROWDED_LIST_ICONS = listOf(MarkerIcon.FIND, MarkerIcon.PHOTO, MarkerIcon.WAYPOINT, MarkerIcon.PLANNED_TRIP)
