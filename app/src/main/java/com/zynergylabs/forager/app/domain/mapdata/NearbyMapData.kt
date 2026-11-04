package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The offline basemap tiles the phone holds, behind an interface Forager owns (way-to dispatch 2026-10-28, PR 2). The
 * real one reads MapLibre's own offline store (`map/MapLibreOfflineBasemap`).
 */
interface OfflineBasemap {
    /** The stored TileJSON of the basemap source, or null when none is stored. A failure is a store that could not be read. */
    suspend fun tileJson(): Result<String?>

    /** The stored tile [id], decompressed MVT bytes, or null when it is not stored. */
    suspend fun tile(id: TileId): Result<ByteArray?>
}

/**
 * Whether a kind of data can be relied on in the stored tiles. [Checked] means the tile set carries it, so its absence
 * from a tile means there is none. [NotInTileSet] means the tile set was built before it was carried, or the tile set's
 * description could not be read. Then absence proves nothing, and a gate reading it must say it could not check, never
 * pass. (The planner, 10-28: "A stored tile set with no barriers layer reports 'fences not checked', never a pass.")
 */
sealed interface Coverage {
    data object Checked : Coverage

    data class NotInTileSet(val reason: String) : Coverage
}

/**
 * What the stored tile set says it carries, from its TileJSON's `vector_layers`. All three are new in basemap patch
 * 0003, so a tile set from before it has none of them:
 * - fences: the `barriers` layer is listed;
 * - address streets: `addr_street` is among the buildings layer's fields;
 * - culverts: the `barriers` layer is listed again, as the mark of patch 0003. The water layer's fields cannot show it:
 *   upstream already sets `tunnel` on water areas, so `tunnel` is listed before 0003 too, while lines lack it
 *   (seen in the one-area proof's two TileJSONs, `src/test/resources/mapdata/`).
 */
data class TileSetSchema(val fences: Coverage, val addressStreets: Coverage, val culverts: Coverage) {
    companion object {
        fun fromTileJson(json: String?): TileSetSchema {
            if (json == null) return none("no TileJSON is stored for the basemap")
            val layers = try {
                (Json.parseToJsonElement(json) as? JsonObject)?.get("vector_layers") as? JsonArray
            } catch (e: IllegalArgumentException) {
                return none("the stored TileJSON is not JSON: ${e.message}")
            } ?: return none("the stored TileJSON lists no vector_layers")
            val byId = layers.mapNotNull { it as? JsonObject }.associateBy { it["id"]?.jsonPrimitive?.content }
            fun fields(layer: String): Set<String> = (byId[layer]?.get("fields") as? JsonObject)?.keys.orEmpty()
            fun covered(present: Boolean, what: String) = if (present) Coverage.Checked else Coverage.NotInTileSet("the tile set has no $what")
            return TileSetSchema(
                fences = covered(TileFeatureReader.BARRIERS in byId, "barriers layer"),
                addressStreets = covered("addr_street" in fields(TileFeatureReader.BUILDINGS), "addr_street field"),
                culverts = covered(TileFeatureReader.BARRIERS in byId, "tunnel on water lines (no barriers layer: built before patch 0003)"),
            )
        }

        private fun none(reason: String) = Coverage.NotInTileSet(reason).let { TileSetSchema(it, it, it) }
    }
}

/**
 * The map data around a point: the features of every zoom-15 tile the circle touches ([tiles]), the tiles that are not
 * stored ([missingTiles]: their buildings and water could not be checked), and what the tile set carries ([schema]).
 *
 * What can be relied on is answered per tile ([buildings], [fences], [culverts], [addressStreets]), so a gate asks about
 * exactly the tiles its leg crosses. Fences, culverts and address streets come per tile from [fencesByTile], which the
 * reader fills from the tile itself: basemap patch 0004 puts a mark in the barriers layer of every zoom-15 tile (the
 * owner's choice (b), 10-28). A tile with the layer was built with barriers, so no fence line in it means none mapped;
 * a tile without it was built before, so none of the three can be relied on there. The tile set's TileJSON ([schema])
 * no longer decides any of them.
 */
data class NearbyMapData(
    val features: MapFeatures,
    val tiles: List<TileId>,
    val missingTiles: List<TileId>,
    val schema: TileSetSchema,
    val fencesByTile: Map<TileId, Coverage>,
) {
    fun buildings(tile: TileId): Coverage = when {
        tile in missingTiles -> Coverage.NotInTileSet("tile ${tile.z}/${tile.x}/${tile.y} is not stored")
        tile !in tiles -> Coverage.NotInTileSet("tile ${tile.z}/${tile.x}/${tile.y} was not read")
        else -> Coverage.Checked
    }

    /** Fences in [tile]: only where the tile is stored and carries the barrier mark. */
    fun fences(tile: TileId): Coverage =
        buildings(tile).takeIf { it is Coverage.NotInTileSet } ?: fencesByTile[tile] ?: Coverage.NotInTileSet("tile ${tile.z}/${tile.x}/${tile.y} was not read")

    /** Culverts in [tile]: tunnel on water lines came with the barriers (patch 0003), so the same mark answers. */
    fun culverts(tile: TileId): Coverage = fences(tile)

    /** Address streets in [tile]: addr_street came with the barriers too. */
    fun addressStreets(tile: TileId): Coverage = fences(tile)
}

/**
 * Reads [NearbyMapData] from the [OfflineBasemap]. A store that cannot be read, or a stored tile that is not a valid
 * tile, fails with the reason. It never hands back what it read before the failure as if it were the whole.
 */
class ReadNearbyMapData(private val basemap: OfflineBasemap) {

    suspend operator fun invoke(around: LatLng, radiusMeters: Double): Result<NearbyMapData> {
        val schema = TileSetSchema.fromTileJson(basemap.tileJson().getOrElse { return Result.failure(it) })
        var features = MapFeatures()
        val missing = mutableListOf<TileId>()
        val marks = HashMap<TileId, Coverage>()
        for (id in tilesAround(around, radiusMeters)) {
            val bytes = basemap.tile(id).getOrElse { return Result.failure(it) }
            if (bytes == null) {
                missing += id
                continue
            }
            val mvt = try {
                MvtDecoder.decode(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MvtFormatException) {
                return Result.failure(MvtFormatException("tile ${id.z}/${id.x}/${id.y}: ${e.message}"))
            }
            features += TileFeatureReader.read(id, mvt)
            marks[id] = if (mvt.layer(TileFeatureReader.BARRIERS) != null) {
                Coverage.Checked
            } else {
                Coverage.NotInTileSet("tile ${id.z}/${id.x}/${id.y} has no barrier mark: it was built before fences were carried")
            }
        }
        return Result.success(NearbyMapData(features, tilesAround(around, radiusMeters), missing, schema, marks))
    }

    companion object {
        const val ZOOM = 15

        /** The zoom-15 tiles a circle of [radiusMeters] round [around] touches, by its bounding box. */
        fun tilesAround(around: LatLng, radiusMeters: Double): List<TileId> {
            val perDegree = GeoDistance.metersPerDegree(around.lat)
            val dLat = radiusMeters / perDegree.lat
            val dLng = radiusMeters / perDegree.lng
            val nw = TileFeatureReader.tileOf(LatLng(around.lat + dLat, around.lng - dLng), ZOOM)
            val se = TileFeatureReader.tileOf(LatLng(around.lat - dLat, around.lng + dLng), ZOOM)
            return (nw.x..se.x).flatMap { x -> (nw.y..se.y).map { y -> TileId(ZOOM, x, y) } }
        }
    }
}
