package com.forager.app.domain

import com.forager.app.domain.model.DerivedTrip
import com.forager.app.domain.model.LatLng

/**
 * The offline regions Cartography's trip report shows for one day — Journal Stage 2b, owner decision
 * #2: "offline regions shown for a day should be those the day's data actually sits on," tested via
 * [isCoordinateWithinRegionTiles] at **each region's own [OfflineRegionSummary.maxZoom]**, not a fixed
 * constant — the owner's own reasoning: "accuracy is relied on where memory fades." This is
 * deliberately a *different* set from [DerivedTrip.offlineRegions] (Stage 2a's own field, left
 * unchanged): that one is "regions *downloaded* on this day," a fact about when a download happened;
 * this is "regions whose downloaded tiles cover something this day actually produced," a fact about
 * spatial coverage, checked against **every** stored region regardless of when it was downloaded.
 *
 * A region counts as covering the day the moment *any* one coordinate the day produced — a find's
 * location, a track point, a waypoint — falls within its tile footprint; tile membership at a
 * region's own max zoom implies membership at every coarser zoom the same region covers (the grid is
 * nested), so testing at max zoom alone is the strict, correct test, not an approximation of a
 * multi-zoom check.
 */
class GetTripReportOfflineRegionsUseCase(
    private val offlineMapRepository: OfflineMapRepository,
) {
    suspend operator fun invoke(trip: DerivedTrip): Result<List<OfflineRegionSummary>> =
        offlineMapRepository.listRegions().map { regions ->
            val points = trip.candidateCoordinates()
            regions.filter { region -> points.any { point -> isCoordinateWithinRegionTiles(point, region.region, region.maxZoom.toInt()) } }
        }
}

/** Every coordinate this day produced — a find's location (when it has one), every recorded track point, and every waypoint. */
private fun DerivedTrip.candidateCoordinates(): List<LatLng> = buildList {
    finds.forEach { find -> find.foundAt?.let(::add) }
    tracks.forEach { track -> track.points.forEach { point -> add(LatLng(point.lat, point.lng)) } }
    waypoints.forEach { waypoint -> add(LatLng(waypoint.lat, waypoint.lng)) }
}
