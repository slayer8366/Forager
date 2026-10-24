package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.routedata.RegionRouteFolders
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import java.io.IOException

/** A [RegionRouteFolders] that records what it was asked and answers [states]; for the repository's decisions. */
internal class FakeRegionRouteFolders(
    val states: MutableMap<Long, RegionRouteData> = mutableMapOf(),
    private val deleteFails: Boolean = false,
) : RegionRouteFolders {
    val deleted = mutableListOf<Long>()
    val keptByCleanup = mutableListOf<Set<Long>>()

    override suspend fun fetchFor(regionId: Long, region: Region): RegionRouteData = states[regionId] ?: RegionRouteData.NotFetched

    override suspend fun status(regionId: Long): RegionRouteData = states[regionId] ?: RegionRouteData.NotFetched

    override suspend fun delete(regionId: Long) {
        if (deleteFails) throw IOException("rename refused")
        deleted += regionId
        states.remove(regionId)
    }

    override suspend fun cleanOrphans(keepIds: Set<Long>) {
        keptByCleanup += keepIds
    }
}
