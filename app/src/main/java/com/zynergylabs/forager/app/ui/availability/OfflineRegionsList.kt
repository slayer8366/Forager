package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityOfflineMapsUi.kt (dispatch 2026-10-19-19, UI split Phase 1, PR 10c), apart from private to internal on the declarations the panel calls.

import com.zynergylabs.forager.app.map.OLDER_MAP_STYLE_NOTICE
import com.zynergylabs.forager.app.map.regionHasOlderMapStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.TwoStageSwipeRow
import com.zynergylabs.forager.app.ui.log.opensRecordDetails
import com.zynergylabs.forager.app.ui.log.rememberSwipeRevealGroup
import com.zynergylabs.forager.app.ui.log.swipeRevealTouchWatcher
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.isOfflineRegionStale
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Every region currently on disk: name, centre, radius, size, download date, the zoom-readiness
 * note main's old single-region `Downloaded` branch used to carry (see [OfflineRegionRow]'s own
 * doc comment for where that text landed), and per-region delete. [errorMessage] surfaces a read
 * failure without clearing whatever was last successfully loaded — see
 * [AvailabilityViewModel.loadOfflineRegions][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.loadOfflineRegions].
 *
 * [errorMessage] renders with no error color, deliberately: per the error-presentation spec, a
 * region-list-load failure (or a failed delete, which surfaces through the same field) isn't
 * belief-changing the way a failed download is — the user isn't mid-action, they just want to see
 * what's on disk, so this matches the neutral "Rainfall data unavailable"-style treatment other
 * read failures in this screen already use, not [OfflineDownloadStatusContent]'s error-red.
 *
 * The tile-budget line and the "sizes don't add up" caveat: [OfflineMapRepository.TILE_COUNT_LIMIT]
 * is the ceiling this app sets deliberately (see that constant's doc comment), and the caveat
 * exists because the resource table dedupes tiles across overlapping regions, so summed per-region
 * tile counts overstate real disk usage and a delete can free far less than its region's own
 * reported size — this text deliberately never promises a specific amount reclaimed.
 *
 * Deleting a downloaded region is not reversible without re-downloading it. It used to confirm
 * through a dialog behind each row's "Delete" button; since journal redesign J4 a row swipes (a
 * [TwoStageSwipeRow] since J4b L6: a short swipe reveals Delete, a full swipe deletes) and [onDeleteOfflineRegion] asks for a *pending* delete
 * (`AvailabilityViewModel.requestDeleteOfflineRegion`): the row hides, the Undo snackbar shows with
 * the reference warning the dialog carried, and MapLibre's tile delete runs only when the snackbar
 * ends without Undo — the only way Undo can be exact for a region.
 *
 * [tilesUsed] is passed separately from [regions] because a pending region is not a row but its
 * tiles still count against the budget until they are deleted.
 */
@Composable
internal fun OfflineRegionsSection(
    regions: List<OfflineRegionSummary>,
    tilesUsed: Int,
    errorMessage: String?,
    staleThresholdDays: Int,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onDeleteOfflineRegion: (Long) -> Unit,
    onDownloadAgain: (Long) -> Unit = {},
    onOpenRegionDetails: ((Long) -> Unit)?,
) {

    // No scroll/height cap of its own: OfflineMapsPanel's whole Column scrolls as one unit (see
    // its doc comment), so this section just renders at its natural height as the last thing in
    // that scroll.
    // J4b L6: one open row at a time, and a touch elsewhere in this section closes it.
    val swipeGroup = rememberSwipeRevealGroup()
    // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"; motion/ListMotion.kt; scout R15): a deleted region's row
    // fades and shrinks while the rows below close up, and Undo brings it back the way it went.
    val rows = rememberListRows(regions, key = { it.id })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .swipeRevealTouchWatcher(swipeGroup)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Downloaded Maps", style = MaterialTheme.typography.titleSmall)

        // RECORD -1245 (the owner: "Keep the figure, drop the explanation (Recommended)"): was followed by "Sizes don't add up to
        // total disk usage — overlapping regions share tiles, so deleting one may free less than its own size suggests."
        Text(
            "Tile budget: $tilesUsed / ${OfflineMapRepository.TILE_COUNT_LIMIT}",
            style = MaterialTheme.typography.bodySmall,
        )

        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall)
        }

        if (rows.isEmpty()) {
            Text("No regions downloaded yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            rows.forEach { row ->
                key(row.key) { ListRowMotion(row) { region ->
                    // J4b L6: two-stage swipe. No Edit: nothing in the app edits a downloaded region
                    // (OfflineMapRepository has download, deleteRegion and listRegions only).
                    TwoStageSwipeRow(
                        testTag = swipeToDeleteTag(RecordType.OFFLINE_MAPS, region.id.toString()),
                        rowKey = region.id,
                        group = swipeGroup,
                        onDelete = { onDeleteOfflineRegion(region.id) },
                        onEdit = null,
                    ) {
                        OfflineRegionRow(
                            region = region,
                            isStale = isOfflineRegionStale(region.createdAtEpochMillis, nowEpochMillis, staleThresholdDays),
                            distanceUnit = distanceUnit,
                            nowEpochMillis = nowEpochMillis,
                            onClick = onOpenRegionDetails?.let { open -> { open(region.id) } },
                            onDownloadAgain = { onDownloadAgain(region.id) },
                        )
                    }
                } }
            }
        }
    }
}

/**
 * One region's row, in [OfflineRegionsSection] and in the All logbook: one plain line (RECORD -870, the owner: "Yes
 * please. Good catch"; the wording RECORD -871, "Yes, that wording (Recommended)"), built by [offlineRegionRowParts]:
 * "Region 5 · 10 mi radius · 18.8 MB · downloaded 11 hours ago". A region not yet downloaded takes the same short
 * form, "Region 5 · 10 mi radius · Not downloaded", with its "Download again" button under it. The "Stale" label
 * stays beside the name.
 *
 * **What left the row.** Until -870 the row also printed the centre's coordinates, the tile count and the
 * zoom-readiness paragraph ([offlineRegionZoomNote]). All three are on the region's own page, the details sheet
 * (`RecordDetailsSheet`'s `OfflineRegionDetails`: Centre, Tiles and the paragraph), which a tap on the row opens, so
 * nothing was dropped. A place name ("10 mi around <town>") is not offered: the app stores none for a region, and the
 * ruling allows one only when it is already on the phone, never from a new network lookup.
 *
 * **One `Text` per part, with a hidden dot between parts**, in a [FlowRow]: a narrow window wraps between parts
 * rather than inside one, each part stays its own text (the name, "Not downloaded"), and a screen reader reads the
 * parts without the dots.
 *
 * No delete control of its own since journal redesign J4 (the text "Delete" button is gone): its
 * callers wrap it in a [TwoStageSwipeRow] (J4b L6).
 *
 * [onClick] (journal redesign J5c) is what a tap on the row opens, the region's details sheet, or
 * `null` for no row tap. The All logbook passes `null` and puts the tap on its badged row instead,
 * so the type badge takes it too.
 */
@Composable
internal fun OfflineRegionRow(
    region: OfflineRegionSummary,
    isStale: Boolean,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onClick: (() -> Unit)? = null,
    /** What "Download again" does on a region restored from a backup ([OfflineRegionSummary.isDownloaded] `false`); unused for a downloaded one. */
    onDownloadAgain: (() -> Unit)? = null,
) {
    val parts = offlineRegionRowParts(region, distanceUnit, nowEpochMillis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.opensRecordDetails(region.name, onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                modifier = Modifier.testTag(offlineRegionRowLineTag(region.id)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(parts.first(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
                if (isStale && region.isDownloaded) {
                    Text("Stale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.alignByBaseline())
                }
                parts.drop(1).forEach { part ->
                    Text("·", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alignByBaseline().clearAndSetSemantics {})
                    Text(
                        part,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (part == OFFLINE_REGION_NOT_DOWNLOADED) MaterialTheme.colorScheme.error else Color.Unspecified,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            // RECORD -1294: a region built against an older map style keeps drawing, and says so in one line. Nothing is
            // re-downloaded for the user. Shown on every region downloaded before Forager Trail Map (dispatch 2026-10-22-01).
            if (regionHasOlderMapStyle(region.styleUrl, region.isDownloaded)) {
                Text(
                    OLDER_MAP_STYLE_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(offlineRegionOlderStyleTag(region.id)),
                )
            }
            // A region restored from a backup onto a phone that never downloaded it (owner, "1 B"): a way to get its tiles.
            if (!region.isDownloaded && onDownloadAgain != null) {
                OutlinedButton(onClick = onDownloadAgain) { Text("Download again") }
            }
        }
    }
}

/**
 * The parts of [OfflineRegionRow]'s one line, in order; joined with " · " they are the line (RECORD -870, -871). The
 * name, the radius, then the size and when it downloaded; or [OFFLINE_REGION_NOT_DOWNLOADED] for a region whose tiles
 * are not on this phone, which has no size or download time it can honestly claim. Pure, so the wording is tested
 * without a screen.
 */
internal fun offlineRegionRowParts(region: OfflineRegionSummary, distanceUnit: DistanceUnit, nowEpochMillis: Long): List<String> {
    val radius = "${formatDistanceKm(region.region.radiusKm, distanceUnit)} radius"
    return if (region.isDownloaded) {
        listOf(region.name, radius, offlineRegionSizeLabel(region), "downloaded ${relativeTimeLabel(region.createdAtEpochMillis, nowEpochMillis)}")
    } else {
        listOf(region.name, radius, OFFLINE_REGION_NOT_DOWNLOADED)
    }
}

/** The last part of a not-downloaded region's line (owner, "1 B"). */
internal const val OFFLINE_REGION_NOT_DOWNLOADED = "Not downloaded"

/** [OfflineRegionRow]'s older-map-style notice (RECORD -1294), for tests. */
internal fun offlineRegionOlderStyleTag(regionId: Long): String = "offline-region-older-style-$regionId"

/** [OfflineRegionRow]'s one line, for tests. */
internal fun offlineRegionRowLineTag(regionId: Long): String = "offline-region-line-$regionId"

/** A region's size on disk as its row prints it, "12.3 MB" (J5c: the details sheet prints the same). */
internal fun offlineRegionSizeLabel(region: OfflineRegionSummary): String = "${"%.1f".format(region.sizeBytes / 1_000_000.0)} MB"

/**
 * A region's zoom-readiness paragraph, shown on its details sheet. It came from main's old single-region
 * `OfflineMapStatusContent` (Workstream B moved it onto each region's row) and was one function from journal redesign
 * J5c so that the row and the sheet showed identical text; since RECORD -870 the row no longer shows it, and the sheet
 * is its one reader.
 */
internal fun offlineRegionZoomNote(region: OfflineRegionSummary): String =
    "Ready to zoom ${region.maxZoom.toInt()}: zoom ${region.minZoom.toInt()}–${region.maxZoom.toInt() - 1} " +
        "from the archive, zoom ${region.maxZoom.toInt()} detail fetched live from Protomaps when this " +
        "region downloaded — a region that shows here has both, since a zoom-${region.maxZoom.toInt()} " +
        "fetch failure fails the whole download rather than silently completing without it."
