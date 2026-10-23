package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * The record a region's route folder carries of itself, `routes.properties` (dispatch 2026-10-23-03): what the fetch
 * found, and for stored routes the run id and each file's size and sha256. Written last, into the folder before it is
 * moved into place, so a folder under a region's id always has one. A `Properties` file, the format the region's
 * MapLibre metadata already uses (`MapLibreOfflineRegionMetadata.kt`), so a damaged one reads as unreadable, never as
 * a crash.
 *
 * **Why a file in the folder and not a Room column** is in [RegionRouteStore]'s doc comment.
 */
internal sealed interface RegionRouteRecord {
    data class Stored(val runId: String, val osmTimestamp: String, val files: List<RecordedFile>) : RegionRouteRecord

    data object NoneHere : RegionRouteRecord

    data class Missing(val reason: RouteDataMissingReason) : RegionRouteRecord
}

internal data class RecordedFile(val name: String, val size: Long, val sha256: String)

internal fun RegionRouteRecord.toBytes(): ByteArray {
    val properties = Properties()
    when (this) {
        is RegionRouteRecord.Stored -> {
            properties.setProperty(KEY_STATE, STATE_STORED)
            properties.setProperty(KEY_RUN_ID, runId)
            properties.setProperty(KEY_OSM_TIMESTAMP, osmTimestamp)
            properties.setProperty(KEY_FILES, files.joinToString(",") { it.name })
            files.forEach { file ->
                properties.setProperty("file.${file.name}.size", file.size.toString())
                properties.setProperty("file.${file.name}.sha256", file.sha256)
            }
        }
        RegionRouteRecord.NoneHere -> properties.setProperty(KEY_STATE, STATE_NONE_HERE)
        is RegionRouteRecord.Missing -> {
            properties.setProperty(KEY_STATE, STATE_MISSING)
            properties.setProperty(KEY_REASON, reason.code)
        }
    }
    val out = ByteArrayOutputStream()
    properties.store(out, null)
    return out.toByteArray()
}

/** What reading a record found. */
internal sealed interface RegionRouteRecordRead {
    data class Parsed(val record: RegionRouteRecord) : RegionRouteRecordRead

    /** [reason] names the key that was missing or the value that would not parse, for the log. */
    data class Unreadable(val reason: String) : RegionRouteRecordRead
}

/** Never throws for bad input. */
internal fun ByteArray.readRegionRouteRecord(): RegionRouteRecordRead {
    val properties = try {
        Properties().apply { load(inputStream()) }
    } catch (e: IllegalArgumentException) {
        return RegionRouteRecordRead.Unreadable("not a properties file (${e.message})")
    }
    fun unreadable(why: String) = RegionRouteRecordRead.Unreadable(why)
    return when (val state = properties.getProperty(KEY_STATE)) {
        STATE_NONE_HERE -> RegionRouteRecordRead.Parsed(RegionRouteRecord.NoneHere)
        STATE_MISSING -> {
            val code = properties.getProperty(KEY_REASON) ?: return unreadable("no $KEY_REASON")
            val reason = RouteDataMissingReason.fromCode(code) ?: return unreadable("unknown reason '$code'")
            RegionRouteRecordRead.Parsed(RegionRouteRecord.Missing(reason))
        }
        STATE_STORED -> {
            val runId = properties.getProperty(KEY_RUN_ID) ?: return unreadable("no $KEY_RUN_ID")
            val osmTimestamp = properties.getProperty(KEY_OSM_TIMESTAMP) ?: return unreadable("no $KEY_OSM_TIMESTAMP")
            val names = properties.getProperty(KEY_FILES)?.split(",")?.filter { it.isNotEmpty() } ?: return unreadable("no $KEY_FILES")
            if (names.isEmpty()) return unreadable("$KEY_FILES is empty")
            val files = names.map { name ->
                if (!BROUTER_FILE_NAME.matches(name)) return unreadable("not a BRouter file name: '$name'")
                val size = properties.getProperty("file.$name.size")?.toLongOrNull() ?: return unreadable("no readable size for $name")
                val sha = properties.getProperty("file.$name.sha256") ?: return unreadable("no sha256 for $name")
                RecordedFile(name, size, sha)
            }
            RegionRouteRecordRead.Parsed(RegionRouteRecord.Stored(runId, osmTimestamp, files))
        }
        null -> unreadable("no $KEY_STATE")
        else -> unreadable("unknown state '$state'")
    }
}

internal const val RECORD_FILE_NAME = "routes.properties"

private const val KEY_STATE = "state"
private const val KEY_REASON = "reason"
private const val KEY_RUN_ID = "run_id"
private const val KEY_OSM_TIMESTAMP = "osm_timestamp"
private const val KEY_FILES = "files"
private const val STATE_STORED = "stored"
private const val STATE_NONE_HERE = "none_here"
private const val STATE_MISSING = "missing"
