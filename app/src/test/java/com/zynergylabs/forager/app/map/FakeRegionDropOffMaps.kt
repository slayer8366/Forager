package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.dropoffmap.RegionDropOffMaps
import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.model.Region
import java.io.IOException

/** [RegionDropOffMaps] as plain state, for the repository's decisions (dispatch 2026-11-04-09), as [FakeRegionRouteFolders] is for routes. */
internal class FakeRegionDropOffMaps(
    val states: MutableMap<Long, RegionDropOffMap> = mutableMapOf(),
    private val deleteFails: Boolean = false,
) : RegionDropOffMaps {
    val deleted = mutableListOf<Long>()
    val keptByCleanup = mutableListOf<Set<Long>>()

    override suspend fun fetchFor(regionId: Long, region: Region): DropOffMapPass = DropOffMapPass.NotRun("not exercised here")

    override suspend fun status(regionId: Long): RegionDropOffMap = states[regionId] ?: RegionDropOffMap.NONE

    override suspend fun delete(regionId: Long) {
        if (deleteFails) throw IOException("rename refused")
        deleted += regionId
        states.remove(regionId)
    }

    override suspend fun cleanOrphans(keepIds: Set<Long>) {
        keptByCleanup += keepIds
    }
}
