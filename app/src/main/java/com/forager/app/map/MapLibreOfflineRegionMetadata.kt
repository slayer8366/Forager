package com.forager.app.map

import com.forager.app.domain.model.Region
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * What [MapLibreOfflineMapRepository] stashes in an `OfflineRegion`'s opaque metadata bytes —
 * `OfflineManager`'s own store already persists tile count/size (read live via
 * `OfflineRegion.getStatus`), so this only needs to carry what that store doesn't know: which
 * [Region] the download was for, and when it finished. `Properties`-over-bytes, the same format
 * [OfflineMapInfo][com.forager.app.domain.OfflineMapInfo]'s old sidecar file used, so a corrupt or
 * foreign-written metadata blob reads as unparseable rather than crashing — see
 * [ByteArray.toRegionMetadata].
 */
internal data class RegionMetadata(val region: Region, val downloadedAtEpochMillis: Long)

internal fun RegionMetadata.toBytes(): ByteArray {
    val properties = Properties().apply {
        setProperty(KEY_LAT, region.lat.toString())
        setProperty(KEY_LNG, region.lng.toString())
        setProperty(KEY_RADIUS_KM, region.radiusKm.toString())
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
        region = Region(
            lat = properties.getProperty(KEY_LAT)!!.toDouble(),
            lng = properties.getProperty(KEY_LNG)!!.toDouble(),
            radiusKm = properties.getProperty(KEY_RADIUS_KM)!!.toInt(),
        ),
        downloadedAtEpochMillis = properties.getProperty(KEY_DOWNLOADED_AT)!!.toLong(),
    )
} catch (e: NullPointerException) {
    null
} catch (e: NumberFormatException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

private const val KEY_LAT = "region.lat"
private const val KEY_LNG = "region.lng"
private const val KEY_RADIUS_KM = "region.radiusKm"
private const val KEY_DOWNLOADED_AT = "downloadedAtEpochMillis"
