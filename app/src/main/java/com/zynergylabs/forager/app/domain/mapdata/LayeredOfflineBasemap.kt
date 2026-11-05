package com.zynergylabs.forager.app.domain.mapdata

/**
 * The offline basemap the way-to reads (dispatch 2026-11-04-09): MapLibre's store, and where its tile lacks the fence
 * mark, the app's own drop-off map copy ([DropOffTileCopy]). Per tile, in this order (B1 P3, the planner's call on the
 * Phase 1 report's section 5: "MapLibre's marked tile first, then the copy's marked tile, then exactly today's"):
 *
 * 1. MapLibre's tile. A store that cannot be read fails the read, as today.
 * 2. MapLibre's tile carries the mark ([BarrierMark]): MapLibre's. This is today's read wherever it already works, and it
 *    is the copy online viewing keeps fresh.
 * 3. MapLibre's tile does not decode: MapLibre's bytes, so the reader fails on them exactly as today.
 * 4. MapLibre's tile is absent, or decodes without the mark: the copy's tile, when the copy holds it and it decodes with
 *    the mark. A copy's tile that does not, is discarded so the next pass fetches it again.
 * 5. Otherwise MapLibre's bytes or nothing: exactly today's. So where neither store holds the tile marked, the reader's
 *    "has no barrier mark" reason, and dispatch 2026-11-04-04 A1's by-trail fallback with its log line, are untouched
 *    (A10).
 *
 * The TileJSON is MapLibre's, as today; nothing reads the tile set's schema since the mark went per tile. MapLibre's tiles
 * are decoded here and again by the reader: a few milliseconds a search, for a reach of four to nine tiles an end.
 */
class LayeredOfflineBasemap(
    private val mapLibre: OfflineBasemap,
    private val copy: DropOffTileCopy,
    private val log: (String, Throwable?) -> Unit,
) : OfflineBasemap {
    override suspend fun tileJson(): Result<String?> = mapLibre.tileJson()

    override suspend fun tile(id: TileId): Result<ByteArray?> {
        val stored = mapLibre.tile(id).getOrElse { return Result.failure(it) }
        if (stored != null && BarrierMark.of(stored) != TileMark.Unmarked) return Result.success(stored)
        val copied = copy.tile(id).getOrElse { error ->
            // The copy is a help, not a source of failure: reading MapLibre's store alone is exactly today's read.
            log("The drop-off map copy could not be read for tile ${id.z}/${id.x}/${id.y}; reading MapLibre's store alone.", error)
            return Result.success(stored)
        } ?: return Result.success(stored)
        when (val mark = BarrierMark.of(copied)) {
            TileMark.Marked -> return Result.success(copied)
            TileMark.Unmarked -> copy.discard(id, "it has no barrier mark")
            is TileMark.Undecodable -> copy.discard(id, "it does not decode (${mark.reason})")
        }
        return Result.success(stored)
    }
}
