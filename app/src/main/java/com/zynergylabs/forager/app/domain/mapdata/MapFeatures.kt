package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/**
 * What the way to a waypoint's street drop-off and its gates read from the offline basemap (way-to dispatch 2026-10-28,
 * PR 2; the owner's go of 10-28): buildings, address points, water, land use and barriers, placed on the map. They come
 * from the Forager basemap's zoom-15 tiles in the layers and fields `src/test/resources/routing/dropoff/README.md`
 * ("The tile") lists. Each feature is a plain value. Nothing here decides anything; the gates do that later.
 */
data class MapFeatures(
    val buildings: List<BuildingOutline> = emptyList(),
    val addresses: List<AddressPoint> = emptyList(),
    val water: List<WaterFeature> = emptyList(),
    val landuse: List<LanduseArea> = emptyList(),
    val barriers: List<BarrierLine> = emptyList(),
) {
    operator fun plus(other: MapFeatures) = MapFeatures(
        buildings + other.buildings,
        addresses + other.addresses,
        water + other.water,
        landuse + other.landuse,
        barriers + other.barriers,
    )
}

/** A building's rings, outer and inner as the tile gives them, each closed (its last point is its first). */
data class BuildingOutline(val rings: List<List<LatLng>>)

/** An address point: upstream puts one at each addressed building's centroid. [street] is null in a tile from before the street was kept. */
data class AddressPoint(val at: LatLng, val housenumber: String?, val street: String?)

/** A water line (river, stream, ditch, canal, drain) or area. [tunnel] is OpenStreetMap's tunnel=*, such as `culvert`. */
data class WaterFeature(val kind: String?, val tunnel: String?, val isArea: Boolean, val parts: List<List<LatLng>>)

/** A land use area: its kind, such as `residential`, `forest`, `wood` or `wetland`, and its closed rings. */
data class LanduseArea(val kind: String?, val rings: List<List<LatLng>>)

/** A fence, wall, hedge or retaining wall, as lines. */
data class BarrierLine(val kind: String?, val lines: List<List<LatLng>>)

/** One map tile's position on the web-mercator grid. */
data class TileId(val z: Int, val x: Int, val y: Int)

/**
 * Turns one decoded tile into [MapFeatures], placing tile coordinates on the map by web-mercator. The layer names and
 * fields read are the Forager basemap's (`map-build/patches/protomaps-basemaps`, tiles 4.15.2 plus patch 0003). Any
 * other layer is ignored, and so is any feature whose geometry type its layer does not use.
 */
object TileFeatureReader {

    fun read(tile: TileId, mvt: MvtTile): MapFeatures {
        fun place(layer: MvtLayer, parts: List<List<TilePoint>>) = parts.map { part -> part.map { positionOf(tile, layer.extent, it) } }

        val buildings = mutableListOf<BuildingOutline>()
        val addresses = mutableListOf<AddressPoint>()
        mvt.layer(BUILDINGS)?.let { layer ->
            for (f in layer.features) {
                when {
                    f.type == MvtGeometryType.POLYGON && f.string("kind") != "address" -> buildings += BuildingOutline(place(layer, f.parts))
                    f.type == MvtGeometryType.POINT && f.string("kind") == "address" -> f.parts.forEach { part ->
                        addresses += AddressPoint(positionOf(tile, layer.extent, part.first()), f.string("addr_housenumber"), f.string("addr_street"))
                    }
                }
            }
        }
        val water = mvt.layer(WATER)?.let { layer ->
            layer.features.filter { it.type == MvtGeometryType.LINESTRING || it.type == MvtGeometryType.POLYGON }.map {
                WaterFeature(it.string("kind"), it.string("tunnel"), it.type == MvtGeometryType.POLYGON, place(layer, it.parts))
            }
        }.orEmpty()
        val landuse = mvt.layer(LANDUSE)?.let { layer ->
            layer.features.filter { it.type == MvtGeometryType.POLYGON }.map { LanduseArea(it.string("kind"), place(layer, it.parts)) }
        }.orEmpty()
        val barriers = mvt.layer(BARRIERS)?.let { layer ->
            layer.features.filter { it.type == MvtGeometryType.LINESTRING }.map { BarrierLine(it.string("kind"), place(layer, it.parts)) }
        }.orEmpty()
        return MapFeatures(buildings, addresses, water, landuse, barriers)
    }

    /** A point in [tile] at tile coordinates [p] of [extent], on the map (web-mercator, as the tile grid is). */
    fun positionOf(tile: TileId, extent: Int, p: TilePoint): LatLng {
        val n = (1L shl tile.z).toDouble()
        val x = tile.x + p.x.toDouble() / extent
        val y = tile.y + p.y.toDouble() / extent
        return LatLng(lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / n)))), lng = x / n * 360.0 - 180.0)
    }

    /** The zoom-[z] tile holding [point]. */
    fun tileOf(point: LatLng, z: Int): TileId {
        val n = (1L shl z).toDouble()
        val x = ((point.lng + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(point.lat)
        val y = ((1 - kotlin.math.ln(kotlin.math.tan(latRad) + 1 / kotlin.math.cos(latRad)) / PI) / 2 * n).toInt()
        return TileId(z, x, y)
    }

    const val BUILDINGS = "buildings"
    const val WATER = "water"
    const val LANDUSE = "landuse"
    const val BARRIERS = "barriers"
}
