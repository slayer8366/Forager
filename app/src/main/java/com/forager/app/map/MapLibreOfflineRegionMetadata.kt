package com.forager.app.map

import com.forager.app.domain.model.Region
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * What [MapLibreOfflineMapRepository] stashes in an `OfflineRegion`'s opaque metadata bytes.
 * `OfflineManager`'s own store already persists tile count/size (read live via
 * `OfflineRegion.getStatus`) and its own native id (`OfflineRegion.getId`), so this only needs to
 * carry what neither of those know: the region's user-facing [name], its [Region] and zoom range,
 * and when the download finished. `Properties`-over-bytes, the same format
 * `OfflineMapInfo`'s old sidecar file used, so a corrupt or foreign-written metadata blob reads as
 * unparseable rather than crashing — see [ByteArray.toRegionMetadata].
 *
 * This is also the recovery source `MapLibreOfflineMapRepository.listRegions` reads from when a
 * region's Room row is missing (app data partially cleared, a migration bug) but `OfflineManager`
 * still has the region — see [com.forager.app.data.local.OfflineRegionEntity]'s doc comment for why
 * the blob and the Room table both carry this, not just one.
 */
internal data class RegionMetadata(
    val name: String,
    val region: Region,
    val minZoom: Double,
    val maxZoom: Double,
    val downloadedAtEpochMillis: Long,
)

internal fun RegionMetadata.toBytes(): ByteArray {
    val properties = Properties().apply {
        setProperty(KEY_NAME, name)
        setProperty(KEY_LAT, region.lat.toString())
        setProperty(KEY_LNG, region.lng.toString())
        setProperty(KEY_RADIUS_KM, region.radiusKm.toString())
        setProperty(KEY_MIN_ZOOM, minZoom.toString())
        setProperty(KEY_MAX_ZOOM, maxZoom.toString())
        setProperty(KEY_DOWNLOADED_AT, downloadedAtEpochMillis.toString())
    }
    val out = ByteArrayOutputStream()
    properties.store(out, null)
    return out.toByteArray()
}

/** `null` for anything unparseable — a foreign or corrupt metadata blob reads as "no region", never a crash or a guessed value. */
internal fun ByteArray.toRegionMetadata(): RegionMetadata? = try {
    val properties = Properties().apply { load(inputStream()) }
    RegionMetadata(
        name = properties.getProperty(KEY_NAME)!!,
        region = Region(
            lat = properties.getProperty(KEY_LAT)!!.toDouble(),
            lng = properties.getProperty(KEY_LNG)!!.toDouble(),
            radiusKm = properties.getProperty(KEY_RADIUS_KM)!!.toInt(),
        ),
        minZoom = properties.getProperty(KEY_MIN_ZOOM)!!.toDouble(),
        maxZoom = properties.getProperty(KEY_MAX_ZOOM)!!.toDouble(),
        downloadedAtEpochMillis = properties.getProperty(KEY_DOWNLOADED_AT)!!.toLong(),
    )
} catch (e: NullPointerException) {
    null
} catch (e: NumberFormatException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

private const val KEY_NAME = "region.name"
private const val KEY_LAT = "region.lat"
private const val KEY_LNG = "region.lng"
private const val KEY_RADIUS_KM = "region.radiusKm"
private const val KEY_MIN_ZOOM = "region.minZoom"
private const val KEY_MAX_ZOOM = "region.maxZoom"
private const val KEY_DOWNLOADED_AT = "downloadedAtEpochMillis"
