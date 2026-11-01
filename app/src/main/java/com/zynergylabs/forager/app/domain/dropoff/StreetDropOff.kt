package com.zynergylabs.forager.app.domain.dropoff

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.mapdata.BuildingOutline
import com.zynergylabs.forager.app.domain.mapdata.Coverage
import com.zynergylabs.forager.app.domain.mapdata.NearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileFeatureReader
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay

/**
 * Whether an end of the way to a waypoint is served by a street or by a trail, and for a street end, where on the street
 * to drop the walker off (way-to dispatch 2026-10-28, PR 4; the owner's ruling of 10-28, "drop off at the street in front
 * of the pin, the way Google does it", and go of ~15:10; thresholds 15, 25, 150 and 300 m).
 */
object StreetDropOff {
    const val BUILDING_METERS = 15.0
    const val STREET_METERS = 150.0

    /** Built-up land: a pin in it with a public street near is a street end even with no building mapped at it. */
    val BUILT_UP = setOf("residential", "commercial", "retail", "industrial", "school")

    sealed interface End {
        /** Served by a street: [building] is the target's own, when one is within [BUILDING_METERS] of the end. */
        data class Street(val building: BuildingOutline?) : End

        data object Trail : End

        /** The tile the end is in is not stored, so whether a building stands there cannot be told. */
        data class Unknown(val reason: String) : End
    }

    /**
     * Street or trail at [point]. A building within [BUILDING_METERS] (or round it) makes it a street end. So does
     * built-up land use round it with a public street within [STREET_METERS]. Anything else is a trail end: the route's
     * own end on the nearest way stands, and only its straight leg is gated.
     */
    fun classify(point: LatLng, data: NearbyMapData, ways: List<RouteWay>): End {
        (data.buildings(TileFeatureReader.tileOf(point, 15)) as? Coverage.NotInTileSet)?.let { return End.Unknown(it.reason) }
        val g = LegGeometry(point)
        val building = data.features.buildings
            .map { it to g.distanceToArea(point, it.rings) }
            .filter { it.second <= BUILDING_METERS }
            .minByOrNull { it.second }?.first
        if (building != null) return End.Street(building)
        val builtUp = data.features.landuse.any { it.kind in BUILT_UP && it.rings.any { r -> g.inside(point, r) } }
        val streetNear = ways.any { AccessWayMouths.isPublicStreet(it.tags) && g.nearestOn(point, it.points).second <= STREET_METERS }
        return if (builtUp && streetNear) End.Street(null) else End.Trail
    }

    enum class Rank { ACCESS_WAY, ADDRESS_STREET, FRONTAGE }

    /** A drop-off tried: where, by which rank, the leg's length, and what the gates said. */
    data class Tried(val at: LatLng, val rank: Rank, val legMeters: Double, val gates: GateResult)

    sealed interface Choice {
        data class Found(val dropOff: Tried) : Choice

        /** No drop-off passed every gate. [tried] says what each one met, so the note can say why. */
        data class NoneQualifies(val tried: List<Tried>) : Choice
    }

    /**
     * Where to drop the walker off for [pin], a street end ([building] from [classify]). It tries the ranks in order and
     * takes the first drop-off whose straight leg to the pin passes every gate ([LegGates]):
     * 1. **Access way:** where a private way from the target meets a public street ([AccessWayMouths]), nearest along it
     *    first. The walker is never routed up it.
     * 2. **Address street:** the nearest point, within [STREET_METERS], of the street the target's address names. Only
     *    where the tile set carries street names.
     * 3. **Frontage:** the nearest point of each public street within [STREET_METERS], shortest leg first. A building in
     *    the way fails the gate, which in effect picks the street the target faces.
     * Never the nearest way in a straight line just for being nearest.
     */
    fun choose(pin: LatLng, building: BuildingOutline?, data: NearbyMapData, ways: List<RouteWay>): Choice {
        val g = LegGeometry(pin)
        val own = listOfNotNull(building)
        val tried = mutableListOf<Tried>()
        fun attempt(at: LatLng, rank: Rank): Tried? {
            if (tried.any { it.rank == rank && GeoDistance.metersBetween(it.at, at) < 1.0 }) return null
            val t = Tried(at, rank, GeoDistance.metersBetween(at, pin), LegGates.check(at, pin, data, own))
            tried += t
            return t.takeIf { it.gates == GateResult.Passes }
        }

        val target = building?.rings ?: listOf(listOf(pin))
        for (mouth in AccessWayMouths.find(target, ways)) attempt(mouth.at, Rank.ACCESS_WAY)?.let { return Choice.Found(it) }

        addressStreet(pin, building, data)?.let { street ->
            data.features.roads
                .filter { it.name == street && it.kind != "path" && it.access != "private" && it.access != "no" }
                .flatMap { road -> road.lines.map { g.nearestOn(pin, it) } }
                .filter { it.second <= STREET_METERS }
                .minByOrNull { it.second }
                ?.let { (at, _) -> attempt(at, Rank.ADDRESS_STREET)?.let { return Choice.Found(it) } }
        }

        ways.filter { AccessWayMouths.isPublicStreet(it.tags) }
            .map { g.nearestOn(pin, it.points) }
            .filter { it.second <= STREET_METERS }
            .sortedBy { it.second }
            .forEach { (at, _) -> attempt(at, Rank.FRONTAGE)?.let { return Choice.Found(it) } }

        return Choice.NoneQualifies(tried)
    }

    /** The street the target's address names: the address point inside its building, or within [BUILDING_METERS] of the pin with none. */
    private fun addressStreet(pin: LatLng, building: BuildingOutline?, data: NearbyMapData): String? {
        if (data.addressStreets(TileFeatureReader.tileOf(pin, 15)) !is Coverage.Checked) return null
        val g = LegGeometry(pin)
        val candidates = data.features.addresses.filter { it.street != null }
        val chosen = if (building != null) {
            candidates.firstOrNull { a -> building.rings.any { g.inside(a.at, it) } }
        } else {
            candidates.map { it to GeoDistance.metersBetween(it.at, pin) }
                .filter { it.second <= BUILDING_METERS }.minByOrNull { it.second }?.first
        }
        return chosen?.street
    }
}
