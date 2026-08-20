package com.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting

/**
 * Everything [MapSlot] draws on top of the basemap, bundled into one value rather than one
 * [MapSlot] parameter per list.
 *
 * This exists for a real compiler limit, not just tidiness: a `@Composable` *function type*
 * (unlike a directly-declared `@Composable fun`) hits an internal Compose compiler crash —
 * `IllegalArgumentException: Function with 11 params had 1 changed params but expected 20`,
 * thrown from `ComposableFunctionBodyTransformer` while lowering [SightingsMapSlot]'s lambda —
 * once the type's declared parameter count reaches 10; [MapSlot] was at 9 before
 * [breadcrumbPoints] below needed to become its 10th, which is what surfaced this. Bundling the
 * content lists here instead keeps [MapSlot] itself at 7 declared parameters with room for
 * whatever Phase 1c's waypoint markers add next, without hitting the same wall again.
 */
data class MapOverlayContent(
    val sightings: List<Sighting> = emptyList(),
    val areas: List<ForagingArea> = emptyList(),
    val plannedTrips: List<PlannedTrip> = emptyList(),
    /**
     * The active track's recorded points, oldest first, drawn as a growing trail — empty whenever
     * nothing is being recorded. See [SightingsMap]'s own doc comment for how this differs from
     * the dashed visiting-order connector: a breadcrumb trail is where the device actually walked,
     * not a suggested order between areas.
     */
    val breadcrumbPoints: List<LatLng> = emptyList(),
)

/**
 * The map, as a slot the screen fills rather than a call the screen makes.
 *
 * This is the same seam [com.forager.app.domain.MushroomRepository] puts in front of iNaturalist,
 * applied to the UI layer: an external integration — a `View` that starts render threads, writes a
 * filesystem cache under `cacheDir` and fetches tiles over the network the moment it is composed —
 * sits behind an interface this project owns, and the caller depends on the interface.
 * [com.forager.app.ui.availability.AvailabilityScreen] previously named [SightingsMap] directly,
 * so there was no way to compose the screen without also standing up the whole tile stack.
 *
 * The vendor behind this seam changed once already, from osmdroid to MapLibre Native
 * (`docs/plans/maplibre-migration.md`), and neither [AvailabilityScreen] nor this typealias's own
 * shape had to change for it — that is the seam doing its job, not a coincidence. [SightingsMap] is
 * the only implementation either renderer's types ever touched.
 *
 * The parameters are exactly what the screen knows and the map needs. [content] is every list the
 * map draws over the basemap — see [MapOverlayContent]'s own doc comment for why those are bundled
 * rather than one parameter apiece. [onLongPress] is how the map reports a trip-planning gesture
 * back up without knowing anything about dates or persistence — the screen owns the date picker and
 * the save call, the map only reports where the finger was. [Basemap] crosses this seam as this
 * project's own type, not a vendor tile-source type, for the same reason the rest of the seam
 * exists: the screen names the basemap it wants and stays ignorant of which vendor supplies the
 * tiles. [modifier] is last because it is the slot's *size contract* — the screen decides how much
 * room the map gets, which is the one thing about this arrangement the screen is actually
 * responsible for.
 */
typealias MapSlot = @Composable (
    region: Region,
    content: MapOverlayContent,
    basemap: Basemap,
    /**
     * When non-null, pans the camera here instead of [region]'s own centre — the map redesign's
     * GPS/locate-me icon (distinct from [region], which stays the search centre; see that button's
     * call site in `AvailabilityScreen.kt`'s `CompactMapTab` for why this doesn't touch the search
     * itself).
     */
    focusOverride: LatLng?,
    onLongPress: (LatLng) -> Unit,
    /**
     * Fires on a plain tap anywhere on the map — the map redesign's "tap the map to restore
     * chrome" while fullscreen (see `CompactMapTab`'s call site). `{}` by every caller that has no
     * fullscreen chrome to restore, same default-ignoring shape as [onLongPress] gets from callers
     * with nothing to plan.
     */
    onTap: () -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * The real map. This is the default every production call path gets, so introducing the seam
 * changed no caller: `MainActivity` passes nothing new.
 */
val SightingsMapSlot: MapSlot = { region, content, basemap, focusOverride, onLongPress, onTap, modifier ->
    SightingsMap(
        region = region,
        sightings = content.sightings,
        areas = content.areas,
        plannedTrips = content.plannedTrips,
        basemap = basemap,
        focusOverride = focusOverride,
        onLongPress = onLongPress,
        onTap = onTap,
        breadcrumbPoints = content.breadcrumbPoints,
        modifier = modifier,
    )
}
