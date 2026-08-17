package com.forager.app.map

import com.forager.app.domain.OfflineBasemapStyle
import com.forager.app.domain.OfflineMapInfo
import com.forager.app.domain.model.Region
import java.util.Properties

/**
 * Turns [OfflineMapInfo] into a [Properties] bag and back, so `getStatus()` can answer "what's
 * downloaded" from a small file next to the tiles instead of a Room table — per this task's own
 * reasoning, a whole table is unwarranted for a single record that only ever describes the one
 * region currently downloaded.
 *
 * The actual file read/write lives in [OsmdroidOfflineMapRepository] and is Android file I/O, so it
 * isn't unit-testable; this conversion is pulled out as pure functions specifically so the format
 * itself — round-tripping every field, and returning `null` rather than throwing on a corrupt or
 * missing file — is.
 */
internal fun OfflineMapInfo.toProperties(): Properties = Properties().apply {
    setProperty(KEY_LAT, region.lat.toString())
    setProperty(KEY_LNG, region.lng.toString())
    setProperty(KEY_RADIUS_KM, region.radiusKm.toString())
    setProperty(KEY_STYLE, style.name)
    setProperty(KEY_TILE_COUNT, tileCount.toString())
    setProperty(KEY_SIZE_BYTES, sizeBytes.toString())
    setProperty(KEY_DOWNLOADED_AT, downloadedAtEpochMillis.toString())
}

/** `null` for anything unparseable — a corrupt sidecar reads as "nothing downloaded", never a crash or a guessed value. */
internal fun Properties.toOfflineMapInfo(): OfflineMapInfo? = try {
    OfflineMapInfo(
        region = Region(
            lat = getProperty(KEY_LAT)!!.toDouble(),
            lng = getProperty(KEY_LNG)!!.toDouble(),
            radiusKm = getProperty(KEY_RADIUS_KM)!!.toInt(),
        ),
        style = OfflineBasemapStyle.valueOf(getProperty(KEY_STYLE)!!),
        tileCount = getProperty(KEY_TILE_COUNT)!!.toInt(),
        sizeBytes = getProperty(KEY_SIZE_BYTES)!!.toLong(),
        downloadedAtEpochMillis = getProperty(KEY_DOWNLOADED_AT)!!.toLong(),
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
private const val KEY_STYLE = "style"
private const val KEY_TILE_COUNT = "tileCount"
private const val KEY_SIZE_BYTES = "sizeBytes"
private const val KEY_DOWNLOADED_AT = "downloadedAtEpochMillis"
