package com.forager.app.domain

import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting

/**
 * Groups already-fetched sightings into dense "foraging areas" and puts them in a suggested
 * visiting order.
 *
 * This is a pure transform of observations the Map tab has already loaded — it makes no network
 * call of any kind. Where pins bunch together is the strongest signal in the dataset (that spot
 * produced repeatedly, across multiple years) and drawing every observation as an identical pin
 * throws it away.
 *
 * ### What this deliberately does not do
 *
 * It does not produce a walking path. There is no trail data, terrain data, land-ownership data,
 * or path graph anywhere in this project — only scattered coordinates over raster map tiles. A
 * line drawn between observation points would look authoritative while potentially crossing a
 * river, a motorway, a cliff, or private land. Per CLAUDE.md, an unsupported capability is
 * reported as unsupported rather than given a fabricated plausible value, so what ships is an
 * *order* (which area to head for next) and not a route (how to get there). The UI must render
 * the connectors between areas as dashed lines with a label saying exactly that.
 *
 * Pure and Android-framework-free so it's unit-testable headless.
 */
class ClusterForagingAreasUseCase {

    /**
     * @param region the search area the [sightings] were fetched for; its centre is the starting
     *   point for the visiting order.
     * @param sightings observations already loaded for the map, in any order.
     */
    operator fun invoke(region: Region, sightings: List<Sighting>): ForagingAreas {
        if (sightings.isEmpty()) {
            return ForagingAreas.None(ForagingAreas.Reason.NO_OBSERVATIONS, observationsConsidered = 0)
        }
        if (sightings.size < MIN_OBSERVATIONS_PER_AREA) {
            // Can't possibly clear the threshold; say so specifically rather than reporting the
            // generic "nothing was dense enough" for what is really "there's almost no data here".
            return ForagingAreas.None(
                ForagingAreas.Reason.TOO_FEW_OBSERVATIONS,
                observationsConsidered = sightings.size,
            )
        }

        val result = Dbscan.cluster(
            points = sightings.map { LatLng(it.lat, it.lng) },
            epsilonMeters = NEIGHBORHOOD_RADIUS_METERS,
            minPoints = MIN_OBSERVATIONS_PER_AREA,
        )
        if (result.clusters.isEmpty()) {
            // Every observation was noise. The threshold is not relaxed to manufacture an area.
            return ForagingAreas.None(
                ForagingAreas.Reason.NO_GROUP_MET_THRESHOLD,
                observationsConsidered = sightings.size,
            )
        }

        val groups = result.clusters.map { indices -> indices.map { sightings[it] } }
        return ForagingAreas.Found(
            areas = orderByProximity(from = LatLng(region.lat, region.lng), groups = groups),
            ungroupedObservationCount = result.noise.size,
        )
    }

    /**
     * Greedy nearest-neighbour ordering: from the search centre, repeatedly hop to the nearest
     * area not yet taken, measuring straight-line distance between area centres.
     *
     * This is proximity order, not an optimal or shortest tour — greedy nearest-neighbour is
     * known to be neither, and calling it "optimised" would overstate what it is. Ties (two
     * areas exactly equidistant) resolve to whichever came first out of clustering, which keeps
     * the output deterministic for a given input.
     */
    private fun orderByProximity(from: LatLng, groups: List<List<Sighting>>): List<ForagingArea> {
        val remaining = groups
            .map { group -> group to centroidOf(group.map { LatLng(it.lat, it.lng) }) }
            .toMutableList()
        val ordered = mutableListOf<ForagingArea>()
        var cursor = from

        while (remaining.isNotEmpty()) {
            // Remove by index, not by value: two structurally identical groups must not collapse.
            val nearestIndex = remaining.indices.minBy { index ->
                GeoDistance.metersBetween(cursor, remaining[index].second)
            }
            val (members, center) = remaining.removeAt(nearestIndex)
            ordered.add(toArea(members, center, visitOrder = ordered.size + 1))
            cursor = center
        }
        return ordered
    }

    private fun toArea(members: List<Sighting>, center: LatLng, visitOrder: Int): ForagingArea {
        // Undated observations contribute to the counts (their location is real) but can never
        // contribute a year — no year is inferred for them. maxOrNull is null only when every
        // observation here is undated, which the UI reports as "no dated observations".
        val years = members.mapNotNull { it.observedOn?.year }
        return ForagingArea(
            visitOrder = visitOrder,
            center = center,
            sightings = members,
            distinctSpeciesCount = members.map { it.taxonId }.distinct().size,
            mostRecentYear = years.maxOrNull(),
            undatedObservationCount = members.count { it.observedOn == null },
        )
    }

    companion object {
        /**
         * Labeled, adjustable assumption (not data-derived): the DBSCAN neighbourhood radius, in
         * metres. Roughly "how far apart two finds can be and still be the same patch of woodland
         * you'd wander around in an afternoon" — a few hundred metres, not kilometres. Set wider
         * and separate woods merge into one blob; set narrower and a single hillside splits into
         * three. Recorded here rather than left implicit, the same treatment the significant-rain
         * threshold gets in `OpenMeteoWeatherProvider`.
         */
        const val NEIGHBORHOOD_RADIUS_METERS = 400.0

        /**
         * Labeled, adjustable assumption (not data-derived): how many observations must fall
         * within [NEIGHBORHOOD_RADIUS_METERS] of each other before the group counts as a spot
         * rather than a coincidence. Counts the point itself, per DBSCAN's standard formulation,
         * so this means one observation plus three neighbours. Two observations near each other
         * happens by chance in any decently-observed wood; four starts to look like a place
         * people keep going back to.
         */
        const val MIN_OBSERVATIONS_PER_AREA = 4
    }
}

/**
 * Mean position of [points].
 *
 * Longitudes are averaged relative to the first point with each difference wrapped into
 * [-180, 180], rather than averaged directly: a group straddling the antimeridian (e.g. 179.99°
 * and -179.99°, which are 2 km apart) would otherwise average to ~0° and drop the area's marker
 * in the Atlantic. Rare, but a plainly wrong coordinate is worse than a rare code path.
 */
private fun centroidOf(points: List<LatLng>): LatLng {
    val referenceLng = points.first().lng
    val meanLat = points.sumOf { it.lat } / points.size
    val meanLngOffset = points.sumOf { wrapLongitudeDegrees(it.lng - referenceLng) } / points.size
    return LatLng(lat = meanLat, lng = wrapLongitudeDegrees(referenceLng + meanLngOffset))
}

/** Normalises a longitude (or longitude difference) in degrees into [-180, 180). */
private fun wrapLongitudeDegrees(degrees: Double): Double {
    val shifted = (degrees + 180.0) % 360.0
    return (if (shifted < 0) shifted + 360.0 else shifted) - 180.0
}
