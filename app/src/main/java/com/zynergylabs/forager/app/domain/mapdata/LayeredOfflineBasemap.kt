package com.zynergylabs.forager.app.domain.mapdata

/** STUB (dispatch 2026-11-04-09, tests first): MapLibre's store alone, as today. The read order is built in the next commit. */
class LayeredOfflineBasemap(
    private val mapLibre: OfflineBasemap,
    @Suppress("unused") private val copy: DropOffTileCopy,
    @Suppress("unused") private val log: (String, Throwable?) -> Unit,
) : OfflineBasemap {
    override suspend fun tileJson(): Result<String?> = mapLibre.tileJson()

    override suspend fun tile(id: TileId): Result<ByteArray?> = mapLibre.tile(id)
}
