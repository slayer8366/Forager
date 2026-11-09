package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `OfflineRegion` metadata-bytes format [MapLibreOfflineMapRepository] reads and writes,
 * pulled out as pure byte conversion so the format itself is unit-testable without an
 * `OfflineManager`/`OfflineRegion`, neither of which is constructible off a real device — the same
 * split [OfflineMapStatusFileTest] used for the osmdroid-era sidecar file this replaces.
 */
class MapLibreOfflineRegionMetadataTest {

    private val metadata = RegionMetadata(
        name = "Chanterelle Ridge",
        region = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
        minZoom = 10.0,
        maxZoom = 15.0,
        downloadedAtEpochMillis = 1_755_000_000_000L,
    )

    @Test
    fun `round-trips every field through bytes`() {
        val roundTripped = metadata.toBytes().toRegionMetadata()

        assertEquals(metadata, roundTripped)
    }

    @Test
    fun `empty bytes are not a valid region`() {
        assertNull(ByteArray(0).toRegionMetadata())
    }

    @Test
    fun `bytes missing one field read as no region, not a crash`() {
        val incompleteProperties = String(metadata.toBytes())
            .lineSequence()
            .filterNot { it.startsWith("downloadedAtEpochMillis") }
            .joinToString("\n")

        assertNull(incompleteProperties.toByteArray().toRegionMetadata())
    }

    @Test
    fun `garbage bytes read as no region, not a crash`() {
        assertNull(byteArrayOf(-1, 0, 1, 2, 3).toRegionMetadata())
    }

    /** Dispatch 2026-09-28-658 (M1): the reader says which key was missing, so the caller's log can. */
    @Test
    fun `a missing field is named in the reason`() {
        val incompleteProperties = String(metadata.toBytes())
            .lineSequence()
            .filterNot { it.startsWith("downloadedAtEpochMillis") }
            .joinToString("\n")

        assertEquals(
            RegionMetadataRead.Unreadable("no downloadedAtEpochMillis"),
            incompleteProperties.toByteArray().readRegionMetadata(),
        )
    }

    @Test
    fun `a value that is not a number is named, with what it held`() {
        val badLatitude = String(metadata.toBytes()).replace(Regex("region\\.lat=.*"), "region.lat=north")

        assertEquals(
            RegionMetadataRead.Unreadable("region.lat is not a number: 'north'"),
            badLatitude.toByteArray().readRegionMetadata(),
        )
    }

    @Test
    fun `good bytes read as the metadata`() {
        assertEquals(RegionMetadataRead.Parsed(metadata), metadata.toBytes().readRegionMetadata())
    }
}
