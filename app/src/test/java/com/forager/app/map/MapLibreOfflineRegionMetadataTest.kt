package com.forager.app.map

import com.forager.app.domain.model.Region
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
        region = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
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
}
