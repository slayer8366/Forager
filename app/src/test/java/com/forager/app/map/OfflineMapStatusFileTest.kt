package com.forager.app.map

import com.forager.app.domain.OfflineBasemapStyle
import com.forager.app.domain.OfflineMapInfo
import com.forager.app.domain.model.Region
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The sidecar-file format `OsmdroidOfflineMapRepository.getStatus()` reads, pulled out as pure
 * [Properties] conversion so it's testable without the File I/O around it (which is Android-facing
 * and unverifiable headlessly — see this task's README notes).
 */
class OfflineMapStatusFileTest {

    private val info = OfflineMapInfo(
        region = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
        style = OfflineBasemapStyle.TOPO,
        tileCount = 234,
        sizeBytes = 4_200_000L,
        downloadedAtEpochMillis = 1_755_000_000_000L,
    )

    @Test
    fun `round-trips every field through Properties`() {
        val roundTripped = info.toProperties().toOfflineMapInfo()

        assertEquals(info, roundTripped)
    }

    @Test
    fun `round-trips the imagery style too`() {
        val imageryInfo = info.copy(style = OfflineBasemapStyle.IMAGERY)

        assertEquals(imageryInfo, imageryInfo.toProperties().toOfflineMapInfo())
    }

    @Test
    fun `an empty Properties bag is not a downloaded region`() {
        assertNull(Properties().toOfflineMapInfo())
    }

    @Test
    fun `a Properties bag missing one field reads as nothing downloaded, not a crash`() {
        val incomplete = info.toProperties().apply { remove("sizeBytes") }

        assertNull(incomplete.toOfflineMapInfo())
    }

    @Test
    fun `an unrecognised style value reads as nothing downloaded, not a crash`() {
        val corrupted = info.toProperties().apply { setProperty("style", "NOT_A_REAL_STYLE") }

        assertNull(corrupted.toOfflineMapInfo())
    }

    @Test
    fun `a non-numeric field reads as nothing downloaded, not a crash`() {
        val corrupted = info.toProperties().apply { setProperty("tileCount", "not-a-number") }

        assertNull(corrupted.toOfflineMapInfo())
    }
}
