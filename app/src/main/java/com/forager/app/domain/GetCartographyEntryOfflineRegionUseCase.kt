package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.LatLng

/**
 * Which downloaded offline region, if any, a [CartographyEntry] can serve its map from while
 * offline — Journal Stage 2e-i. Deliberately scoped to the entry's own **kept**
 * [CartographyEntry.offlineRegionDecisions], not every region on the device
 * [GetTripReportOfflineRegionsUseCase] searches for its different question ("what covers this
 * day's records," asked of a day the user hasn't curated yet). Withholding is first-class in this
 * design (see [CartographyEntry]'s own doc comment on kept vs. withheld) — a region the user didn't
 * keep is a region they excluded, and falling back to an arbitrary uncovering-but-downloaded region
 * would serve unlabelled offline tiles for a day the user never associated with it, undoing that
 * exclusion silently.
 *
 * Tested via [isCoordinateWithinRegionTiles] at [OfflineMapRepository.MAX_ZOOM] — a single fixed
 * app-wide constant every downloaded region shares (unlike [GetTripReportOfflineRegionsUseCase],
 * which reads each region's own [OfflineRegionSummary.maxZoom] because per-region zoom mattered for
 * that use case's own reasoning; here every candidate already shares one constant, so there is
 * nothing per-region to read).
 *
 * **Tolerant of absence throughout, never a failure** — no kept region decisions, [points] entirely
 * empty (an entry made only of photos with no coordinates), a kept region deleted from the device
 * since (present in [CartographyEntry.offlineRegionDecisions] but absent from
 * [OfflineMapRepository.listRegions]'s live result), or [OfflineMapRepository.listRegions] itself
 * failing, all resolve to `null` — "no offline region available for this entry," a real, reachable,
 * unremarkable state, not an error. Same tolerant-of-a-dangling-reference philosophy Stage 2d's
 * [GetCartographyEntryMapDataUseCase] already established for kept tracks/finds.
 */
class GetCartographyEntryOfflineRegionUseCase(
    private val offlineMapRepository: OfflineMapRepository,
) {
    suspend operator fun invoke(entry: CartographyEntry, points: List<LatLng>): OfflineRegionSummary? {
        if (points.isEmpty()) return null
        val keptRegionIds = entry.offlineRegionDecisions.filter { it.kept }.map { it.offlineRegionId }.toSet()
        if (keptRegionIds.isEmpty()) return null

        val downloadedRegions = offlineMapRepository.listRegions().getOrNull().orEmpty()
        return downloadedRegions
            .filter { it.id in keptRegionIds }
            .firstOrNull { region -> points.any { point -> isCoordinateWithinRegionTiles(point, region.region, OfflineMapRepository.MAX_ZOOM.toInt()) } }
    }
}
