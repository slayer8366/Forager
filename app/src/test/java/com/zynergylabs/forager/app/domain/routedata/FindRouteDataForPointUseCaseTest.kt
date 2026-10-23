package com.zynergylabs.forager.app.domain.routedata

import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Routes for the area containing X" (dispatch 2026-10-23-03, item 5), through the use case the trail router will call. */
class FindRouteDataForPointUseCaseTest {

    private class Regions(private val regions: List<OfflineRegionSummary>) : OfflineMapRepository {
        override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
        override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions)
    }

    private fun find(point: LatLng, vararg regions: OfflineRegionSummary) = runBlocking { FindRouteDataForPointUseCase(Regions(regions.toList()))(point) }.getOrThrow()

    private fun region(id: Long, routeData: RegionRouteData, createdAt: Long = 1_000L, centre: LatLng = CENTRE, radiusKm: Int = 10) =
        OfflineRegionSummary(id, "R$id", Region(centre.lat, centre.lng, radiusKm), 10.0, 15.0, 100, 1_000L, createdAt, routeData = routeData)

    private fun stored(id: Long) = RegionRouteData.Stored("run-$id", "t", 10L, File("/routes/$id"))

    @Test
    fun `a point inside a region with stored routes finds that region's folder`() {
        assertEquals(RouteDataForPoint.Found(1, File("/routes/1"), "run-1"), find(CENTRE, region(1, stored(1))))
    }

    @Test
    fun `a point in the rectangle's corner, outside the drawn circle, is covered: the routes were cut to the rectangle`() {
        val box = routeAreaFor(Region(CENTRE.lat, CENTRE.lng, 10))
        val corner = LatLng(box.north - 0.001, box.east - 0.001)

        assertTrue(find(corner, region(1, stored(1))) is RouteDataForPoint.Found)
    }

    @Test
    fun `a point outside every region is covered by none`() {
        assertEquals(RouteDataForPoint.NoRegionCovers, find(LatLng(40.0, -100.0), region(1, stored(1))))
    }

    @Test
    fun `when regions overlap, the newest download with stored routes wins`() {
        val found = find(CENTRE, region(1, stored(1), createdAt = 1_000), region(2, stored(2), createdAt = 3_000), region(3, RegionRouteData.NoneHere, createdAt = 5_000))

        assertEquals(RouteDataForPoint.Found(2, File("/routes/2"), "run-2"), found)
    }

    @Test
    fun `covered only by regions without stored routes says so, with the newest one's state`() {
        val missing = RegionRouteData.Missing(RouteDataMissingReason.NETWORK)

        assertEquals(RouteDataForPoint.NotStored(2, missing), find(CENTRE, region(1, RegionRouteData.NotFetched, createdAt = 1_000), region(2, missing, createdAt = 2_000)))
    }

    @Test
    fun `a failed listing is a failure, never no region`() {
        val failing = object : OfflineMapRepository {
            override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
            override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
            override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.failure(java.io.IOException("read failed"))
        }

        assertTrue(runBlocking { FindRouteDataForPointUseCase(failing)(CENTRE) }.isFailure)
    }

    private companion object {
        val CENTRE = LatLng(45.0, -120.0)
    }
}
