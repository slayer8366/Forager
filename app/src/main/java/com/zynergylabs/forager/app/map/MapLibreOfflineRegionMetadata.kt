package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.model.Region
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
 * still has the region — see [com.zynergylabs.forager.app.data.local.OfflineRegionEntity]'s doc comment for why
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

/** `null` for anything unparseable — a foreign or corrupt metadata blob reads as "no region", never a crash or a guessed value. [readRegionMetadata] says why. */
internal fun ByteArray.toRegionMetadata(): RegionMetadata? = (readRegionMetadata() as? RegionMetadataRead.Parsed)?.metadata

/** What reading a region's metadata blob found: the metadata, or why it could not be read (dispatch 2026-09-28-658, M1). */
internal sealed interface RegionMetadataRead {
    data class Parsed(val metadata: RegionMetadata) : RegionMetadataRead

    /** [reason] is for the log: which key was missing or which value would not parse. */
    data class Unreadable(val reason: String) : RegionMetadataRead
}

/**
 * Reads the blob [toBytes] wrote. Never throws for bad input: a missing key, a value that is not a
 * number, or bytes that are not a properties file come back as [RegionMetadataRead.Unreadable]
 * naming what was wrong, so a caller that logs can say why a region was not rebuilt.
 */
internal fun ByteArray.readRegionMetadata(): RegionMetadataRead {
    val properties = try {
        Properties().apply { load(inputStream()) }
    } catch (e: IllegalArgumentException) {
        return RegionMetadataRead.Unreadable("not a properties file (${e.message})")
    }
    fun text(key: String): String = properties.getProperty(key) ?: throw MissingKey(key)
    fun <T> number(key: String, parse: (String) -> T): T {
        val raw = text(key)
        return try {
            parse(raw)
        } catch (e: NumberFormatException) {
            throw BadValue(key, raw)
        }
    }
    return try {
        RegionMetadataRead.Parsed(
            RegionMetadata(
                name = text(KEY_NAME),
                region = Region(
                    lat = number(KEY_LAT, { it.toDouble() }),
                    lng = number(KEY_LNG, { it.toDouble() }),
                    radiusKm = number(KEY_RADIUS_KM, { it.toInt() }),
                ),
                minZoom = number(KEY_MIN_ZOOM, { it.toDouble() }),
                maxZoom = number(KEY_MAX_ZOOM, { it.toDouble() }),
                downloadedAtEpochMillis = number(KEY_DOWNLOADED_AT, { it.toLong() }),
            ),
        )
    } catch (e: MissingKey) {
        RegionMetadataRead.Unreadable("no ${e.key}")
    } catch (e: BadValue) {
        RegionMetadataRead.Unreadable("${e.key} is not a number: '${e.raw}'")
    } catch (e: IllegalArgumentException) {
        RegionMetadataRead.Unreadable("the values do not make a region (${e.message})")
    }
}

private class MissingKey(val key: String) : Exception()

private class BadValue(val key: String, val raw: String) : Exception()

private const val KEY_NAME = "region.name"
private const val KEY_LAT = "region.lat"
private const val KEY_LNG = "region.lng"
private const val KEY_RADIUS_KM = "region.radiusKm"
private const val KEY_MIN_ZOOM = "region.minZoom"
private const val KEY_MAX_ZOOM = "region.maxZoom"
private const val KEY_DOWNLOADED_AT = "downloadedAtEpochMillis"
