package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.DropOffTileCopy
import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.mapdata.TileId
import java.io.File

/** STUB (dispatch 2026-11-04-09, tests first): the copy's folders. Built in the next commit; nothing is stored yet. */
internal class DropOffMapStore(
    @Suppress("unused") private val root: File,
    @Suppress("unused") private val log: (String, Throwable?) -> Unit,
) : DropOffTileCopy {
    fun beginPass(regionId: Long): Boolean = true

    fun endPass(regionId: Long) = Unit

    fun isWhole(regionId: Long): Boolean = false

    fun holds(regionId: Long, tile: TileId): Boolean = false

    fun write(regionId: Long, tile: TileId, mvt: ByteArray) = Unit

    fun markEmpty(regionId: Long, tile: TileId) = Unit

    fun markWhole(regionId: Long) = Unit

    fun status(regionId: Long): RegionDropOffMap = RegionDropOffMap.NONE

    fun delete(regionId: Long) = Unit

    fun cleanOrphans(keepIds: Set<Long>) = Unit

    override suspend fun tile(id: TileId): Result<ByteArray?> = Result.success(null)

    override suspend fun discard(id: TileId, reason: String) = Unit
}
