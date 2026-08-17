package com.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting

/**
 * The map, as a slot the screen fills rather than a call the screen makes.
 *
 * This is the same seam [com.forager.app.domain.MushroomRepository] puts in front of iNaturalist,
 * applied to the UI layer: an external integration — osmdroid, a `View` that starts tile threads,
 * writes a filesystem cache under `cacheDir` and fetches tiles over the network the moment it is
 * composed — sits behind an interface this project owns, and the caller depends on the interface.
 * [com.forager.app.ui.availability.AvailabilityScreen] previously named [SightingsMap] directly,
 * so there was no way to compose the screen without also standing up the whole tile stack.
 *
 * The parameters are exactly what the screen knows and the map needs. [onLongPress] is how the map
 * reports a trip-planning gesture back up without knowing anything about dates or persistence —
 * the screen owns the date picker and the save call, the map only reports where the finger was.
 * [Basemap] crosses this seam as this project's own type, not osmdroid's `ITileSource`, for the same
 * reason the rest of the seam exists: the screen names the basemap it wants and stays ignorant of
 * which vendor supplies the tiles. [modifier] is last because it is the slot's *size contract* — the
 * screen decides how much room the map gets, which is the one thing about this arrangement the
 * screen is actually responsible for.
 */
typealias MapSlot = @Composable (
    region: Region,
    sightings: List<Sighting>,
    areas: List<ForagingArea>,
    plannedTrips: List<PlannedTrip>,
    basemap: Basemap,
    onLongPress: (LatLng) -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * The real map. This is the default every production call path gets, so introducing the seam
 * changed no caller: `MainActivity` passes nothing new.
 */
val SightingsMapSlot: MapSlot = { region, sightings, areas, plannedTrips, basemap, onLongPress, modifier ->
    SightingsMap(
        region = region,
        sightings = sightings,
        areas = areas,
        plannedTrips = plannedTrips,
        basemap = basemap,
        onLongPress = onLongPress,
        modifier = modifier,
    )
}
