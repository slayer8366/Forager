package com.zynergylabs.forager.app.domain.dropoff

import com.zynergylabs.forager.app.domain.mapdata.BuildingOutline
import com.zynergylabs.forager.app.domain.mapdata.Coverage
import com.zynergylabs.forager.app.domain.mapdata.NearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileFeatureReader
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The gates on a straight leg of the way to a waypoint (way-to dispatch 2026-10-28, PR 4; the owner's ruling of 10-28
 * and go of ~15:10). The leg must not cross a building, a fence or a river:
 * - **Buildings:** any outline in the stored tiles, except [ownBuildings], the target's own. The leg ends at its door.
 * - **Fences:** the barriers layer's fences, walls, hedges and retaining walls. They stand in for property lines (the
 *   owner).
 * - **Rivers:** the owner's "river, stream, ditch, canal and wetland". That means water lines of those kinds, water
 *   areas, and wetland land use. A line with tunnel=culvert passes ("culverts pass").
 *
 * There is no gate for elevation: the owner's go ships without one, and nothing is said about drops.
 *
 * What the stored data cannot vouch for is [GateResult.Unchecked], never a pass, tile by tile along the leg
 * ([NearbyMapData.buildings], [NearbyMapData.fences], [NearbyMapData.culverts]). A crossing found is a fail whatever
 * else could not be checked.
 */
object LegGates {

    /** River, stream, ditch, canal: the owner's list for water lines. Drains are not on it. */
    val RIVER_LINE_KINDS = setOf("river", "stream", "ditch", "canal")

    /** How close together the leg is sampled for the tiles it crosses: well under a zoom-15 tile's ~800 m. */
    private const val TILE_STEP_METERS = 25.0

    fun check(from: LatLng, to: LatLng, data: NearbyMapData, ownBuildings: List<BuildingOutline> = emptyList()): GateResult {
        val g = LegGeometry(to)
        val crossed = mutableListOf<Crossing>()
        val f = data.features
        for (b in f.buildings) {
            if (ownBuildings.any { it === b || it == b }) continue
            if (b.rings.any { g.crossesLine(from, to, it) } || b.rings.any { g.inside(from, it) }) crossed += Crossing.BUILDING
        }
        for (barrier in f.barriers) if (barrier.lines.any { g.crossesLine(from, to, it) }) crossed += Crossing.FENCE
        val tiles = g.along(from, to, TILE_STEP_METERS).map { TileFeatureReader.tileOf(it, TILE_ZOOM) }.distinct()
        // A stream crossed in a tile from before culverts were marked may be in one: unknown, not a fail.
        val culvertsUnmarked = tiles.firstNotNullOfOrNull { t -> (data.culverts(t) as? Coverage.NotInTileSet)?.let { Unchecked(Crossing.WATER, t, "a stream is crossed and ${it.reason}") } }
        var streamMaybeInCulvert: Unchecked? = null
        for (w in f.water) {
            if (!(w.isArea || w.kind in RIVER_LINE_KINDS) || w.parts.none { g.crossesLine(from, to, it) }) continue
            when {
                w.isArea -> crossed += Crossing.WATER
                w.tunnel == "culvert" -> Unit // culverts pass
                w.tunnel == null && culvertsUnmarked != null -> streamMaybeInCulvert = culvertsUnmarked
                else -> crossed += Crossing.WATER
            }
        }
        for (area in f.landuse) if (area.kind == "wetland" && area.rings.any { g.crossesLine(from, to, it) || g.inside(from, it) || g.inside(to, it) }) crossed += Crossing.WATER

        if (crossed.isNotEmpty()) return GateResult.Fails(crossed.distinct().sorted())

        val unchecked = buildList {
            streamMaybeInCulvert?.let(::add)
            tiles.firstNotNullOfOrNull { (data.buildings(it) as? Coverage.NotInTileSet)?.let { c -> Unchecked(Crossing.BUILDING, it, c.reason) } }?.let(::add)
            tiles.firstNotNullOfOrNull { (data.fences(it) as? Coverage.NotInTileSet)?.let { c -> Unchecked(Crossing.FENCE, it, c.reason) } }?.let(::add)
            // Water itself is there in any stored tile; only whether a stream is in a culvert can be unknown, and that
            // matters only where the leg crosses one. Here none was crossed, so only a missing tile leaves water unknown.
            tiles.firstNotNullOfOrNull { (data.buildings(it) as? Coverage.NotInTileSet)?.let { c -> Unchecked(Crossing.WATER, it, c.reason) } }?.let(::add)
        }
        return if (unchecked.isEmpty()) GateResult.Passes else GateResult.Unchecked(unchecked)
    }

    private const val TILE_ZOOM = 15

    enum class Crossing { BUILDING, FENCE, WATER }

    data class Unchecked(val what: Crossing, val tile: TileId, val reason: String)
}

sealed interface GateResult {
    data object Passes : GateResult

    /** The leg crosses these. */
    data class Fails(val crossings: List<LegGates.Crossing>) : GateResult

    /** Nothing was crossed in what the data holds, but the data could not vouch for these. Not a pass. */
    data class Unchecked(val what: List<LegGates.Unchecked>) : GateResult
}
