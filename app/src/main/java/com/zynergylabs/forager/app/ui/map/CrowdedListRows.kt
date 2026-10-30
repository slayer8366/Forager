package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.log.findTitle
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The rows of a crowded stack's list (dispatch 2026-10-27-07, step 4; the owner: "Icon, name, date (Recommended)"). Plain
 * data, looked up in the lists the Maps tab's bubbles already read ([MapRecordSources]), so no new read: the fan knows only a
 * record's layer, id and place, and this is where its name and date come from. Headless (`CrowdedListRowsTest`).
 */

/** One row: the record's map icon, its name and its date ([date] `null` when the record has none). */
internal data class CrowdedListRow(val key: FanKey, val icon: MarkerIcon, val name: String, val date: String?, val sortDate: LocalDate?)

/** The name a find with no identification and no found time shows, as the find's own map icon reads. */
internal const val CROWDED_ROW_FIND_FALLBACK = "Find"

/** A photo's row name: a photo has no name of its own. */
internal const val CROWDED_ROW_PHOTO_NAME = "Photo"

/** A waypoint whose name is blank. */
internal const val CROWDED_ROW_WAYPOINT_FALLBACK = "Waypoint"

/**
 * [keys]' rows, newest first (a row with no date last), each looked up in [sources]. A key whose record is not there any
 * more (a delete landed, a list reloaded) gets no row, and [warn] is told: a row is never made up.
 */
internal fun crowdedListRows(
    keys: List<FanKey>,
    sources: MapRecordSources,
    is24HourClock: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    warn: (String) -> Unit = {},
): List<CrowdedListRow> {
    fun dayOf(epochMillis: Long?): LocalDate? = epochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val rows = keys.mapNotNull { key ->
        val id = key.featureId
        val row = when (key.layerId) {
            MapLayerIds.FINDS -> sources.finds.firstOrNull { it.id == id }?.let { find ->
                CrowdedListRow(key, MarkerIcon.FIND, findTitle(find, is24HourClock, zone) ?: CROWDED_ROW_FIND_FALLBACK, displayDate(find.foundOn), find.foundOn)
            }
            MapLayerIds.PHOTOS -> sources.galleryPhotos.firstOrNull { it.photo.id == id }?.let { gallery ->
                val day = dayOf(gallery.photo.createdAtEpochMillis)
                CrowdedListRow(key, MarkerIcon.PHOTO, CROWDED_ROW_PHOTO_NAME, day?.let(::displayDate), day)
            }
            MapLayerIds.WAYPOINTS -> sources.waypoints.firstOrNull { it.id == id }?.let { waypoint ->
                val day = dayOf(waypoint.createdAtEpochMillis)
                CrowdedListRow(key, MarkerIcon.WAYPOINT, waypoint.name.ifBlank { CROWDED_ROW_WAYPOINT_FALLBACK }, day?.let(::displayDate), day)
            }
            MapLayerIds.PLANNED_TRIPS -> sources.plannedTrips.firstOrNull { it.id == id }?.let { trip ->
                CrowdedListRow(key, MarkerIcon.PLANNED_TRIP, trip.name, trip.date?.let(::displayDate), trip.date)
            }
            else -> null
        }
        if (row == null) warn("Crowded list: no record for ${key.layerId}/$id; it has no row.")
        row
    }
    return rows.sortedWith(compareBy<CrowdedListRow, Long?>(nullsLast(reverseOrder())) { it.sortDate?.toEpochDay() }.thenBy { it.name })
}
