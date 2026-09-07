# Pulse: what a Cartography plate can actually draw

**Type:** read-only survey (owner-directed pulse). **Nothing built; no product code, no tests, no dependency, no gradle change.**
**Date:** 2026-09-07. **Base:** `main` at `0ca2f55fe4f49a33aee296d95ee3004ebde231ca` — the merge of PR #73 (timestamp filter), confirmed with `git fetch origin main` at the start of this session; `main` had moved through a merge and a revert of PR #74 earlier the same day and this SHA is after both. Branch `claude/new-session-b7z9bg` was already at this SHA; this report is its only commit.

Every claim names a file and location on `0ca2f55`, or is marked inferred. The schematic this pulse refers to (`Journal-Atlas-Schematics`) is **not in the repository** — no file, commit message or doc mentions it (`grep -ri "atlas"` over `docs/` finds only unrelated hits) — so nothing below is checked against the schematic's text; only against the pulse's own description of it.

---

## The short version: which renderers survive

| Renderer | Verdict from the data | The fact that decides it |
|---|---|---|
| **1. Compose Canvas** from `CartographyEntryMapData` | **Viable as a technique, with two real caveats the pulse predicted.** The data is plain point lists with nothing pre-simplified and nothing lazy inside the type. | The type is five `List`s of `LatLng`/`Region` (`GetCartographyEntryMapDataUseCase.kt:81-107`). Getting it costs a Room round trip per kept track and per distinct find day, so the *fetch* is asynchronous even though the *type* is not (§1). Nothing bounds an entry's spatial extent (§2), so a multi-site day fits to a 50 km-clamped radius and a walk becomes a dot. |
| **2. `MapSnapshotter`** cached per entry | **Unreliable by default, on three separate counts.** | (a) The offline toggle that exists is inert — `MapRenderMode.useOfflineTiles` is "read by nothing yet" (`MapSlot.kt:66-85`), so no live map in this app has ever rendered from the offline store; whether a style load is served from it is recorded as unsettled without a device. (b) The coverage check is "any one point inside the region's tiles", not "all points" (§3). (c) `updatedAtEpochMillis` does not change when a committed entry is edited in place (§5) — the proposed cache key would serve stale plates. `MapSnapshotter` is not referenced anywhere in the repo or its docs; it does exist in the pinned SDK (verified by `javap`, §3), and what it draws when tiles are absent is unverified. |
| **3. Live `MapView` per tile** | **The doc comment the schematic cites does not say what it is cited for.** | `CartographyEntryReportScreen.kt:165-178` is about keeping the map to *one textual call site inside that screen* so `remember` survives a fullscreen toggle — not a rule against multiple instances. No code or doc in this repo forbids more than one `MapView`. What the repo *does* say is that each one "starts render threads, writes a filesystem cache … and fetches tiles over the network the moment it is composed" (`MapSlot.kt:200-203`), and that none of it can be composed under Robolectric (§4). The cost argument stands; the "single instance" citation does not. |

**Two premises of the pulse are wrong and change the sizing arithmetic (§4):** the grid is 2-up on compact and **3-up on both medium and expanded**, not 2-up on medium; and on medium/expanded the Journal lives in a **fixed 360 dp drawer sheet**, so a 3-up plate there is **104 dp** wide — smaller than the 160 dp compact plate, not larger.

**One finding that is not a renderer question:** a kept track whose every stored point is excluded by the timestamp filter yields an *empty polyline* that `CartographyEntryMapData.isEmpty` counts as content (§7). The report screen survives it by a second guard; a plate renderer keyed on `isEmpty` alone would not.

---

## 1. What `CartographyEntryMapData` actually contains

### The type, in full

`app/src/main/java/com/forager/app/domain/GetCartographyEntryMapDataUseCase.kt:81-107`:

```kotlin
/** Everything [CartographyEntryReportScreen]'s map needs, already resolved — see [GetCartographyEntryMapDataUseCase]'s own doc comment. */
data class CartographyEntryMapData(
    val trackPolylines: List<List<LatLng>>,
    val findMarkers: List<LatLng>,
    val waypointMarkers: List<LatLng>,
    val photoMarkers: List<LatLng>,
    val offlineRegionCircles: List<Region>,
) {
    /** `true` when nothing here resolved to a single drawable point or line — a real, reachable state (an entry made entirely of photos with null coordinates), not an error. */
    val isEmpty: Boolean
        get() = trackPolylines.isEmpty() && findMarkers.isEmpty() && waypointMarkers.isEmpty() && photoMarkers.isEmpty() && offlineRegionCircles.isEmpty()

    /**
     * Every resolved point, flattened — what [GeoDistance.boundingRegion] fits the camera to. An
     * offline region contributes only its centre here, not its full circle extent — a
     * simplification: a large kept region could in principle extend past this frame's own edge.
     * Accepted for now since the region-circle overlay itself is an open visual question the
     * dispatch that added it explicitly deferred to the owner's own judgement after seeing it.
     */
    val allPoints: List<LatLng>
        get() = trackPolylines.flatten() + findMarkers + waypointMarkers + photoMarkers + offlineRegionCircles.map { LatLng(it.lat, it.lng) }
}
```

`LatLng` is two `Double`s (`domain/model/LatLng.kt:10-13`); `Region` is `lat`, `lng`, `radiusKm: Int` (`domain/model/Region.kt:4-8`).

### The use case, in full

`GetCartographyEntryMapDataUseCase.kt:37-79` (the doc comment above it, lines 8-36, is quoted where it matters below):

```kotlin
class GetCartographyEntryMapDataUseCase(
    private val trackRepository: TrackRepository,
    private val mushroomLogRepository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: CartographyEntry, galleryPhotos: List<GalleryPhoto>): CartographyEntryMapData {
        val trackPolylines = entry.trackDecisions.filter { it.kept }.mapNotNull { decision ->
            trackRepository.getById(decision.trackId).getOrNull()?.points?.map { LatLng(it.lat, it.lng) }
        }

        val keptFinds = entry.findDecisions.filter { it.kept }
        val findsByDayKey = keptFinds.map { it.foundOn.toString() }.distinct().associateWith { dayKey ->
            mushroomLogRepository.getForDay(dayKey).getOrNull().orEmpty()
        }
        val findMarkers = keptFinds.mapNotNull { decision ->
            findsByDayKey[decision.foundOn.toString()]
                ?.firstOrNull { it.id == decision.findId }
                ?.foundAt
                ?.let { LatLng(it.lat, it.lng) }
        }

        val waypointMarkers = entry.waypointDecisions.filter { it.kept }.map { LatLng(it.lat, it.lng) }

        val photosById = galleryPhotos.associateBy { it.photo.id }
        val photoMarkers = entry.photos.mapNotNull { attachment ->
            val photo = photosById[attachment.photoId]?.photo ?: return@mapNotNull null
            val lat = photo.latitude ?: return@mapNotNull null
            val lng = photo.longitude ?: return@mapNotNull null
            LatLng(lat, lng)
        }

        val offlineRegionCircles = entry.offlineRegionDecisions.filter { it.kept }.map {
            Region(lat = it.lat, lng = it.lng, radiusKm = it.radiusKm)
        }

        return CartographyEntryMapData(
            trackPolylines = trackPolylines,
            findMarkers = findMarkers,
            waypointMarkers = waypointMarkers,
            photoMarkers = photoMarkers,
            offlineRegionCircles = offlineRegionCircles,
        )
    }
}
```

### Tracks: full point lists, nothing simplified

A polyline is `Track.points` mapped one-to-one to `LatLng` — the full recorded sequence after the read seam's network-fix exclusion (`RoomTrackRepository.kt:66-79`, `toDomain`: `val kept = excludeNetworkProviderFixes(stored)`). No Douglas-Peucker, no thinning, no cap. `Track`'s own doc says the design intent: "[points] is the full recorded sequence, loaded together rather than paginated — a multi-hour track is at most a few thousand points at any sane sampling interval" (`Track.kt:14-17`).

**Typical and worst case, from the sampler's own constants** (`TrackRecordingMode.kt:27-29`): a point is accepted only once *both* the interval and distance thresholds are met, so the interval is a ceiling on rate.

| Mode | `minIntervalMillis` | Max points/hour | 8-hour worst case, one track |
|---|---|---|---|
| `HIGH_ACCURACY` | 5 000 | 720 | 5 760 |
| `BALANCED` | 15 000 | 240 | 1 920 |
| `BATTERY_SAVER` | 60 000 | 60 | 480 |

An entry can keep more than one track (the day query returns every track overlapping the day — §2), so the worst case per entry is a multiple of the last column. **Real data seen in this repo is tiny by comparison:** the three exported tracks in `docs/audits/2026-09-06-timestamp-discriminator-findings.md` total 57 points; the starburst track was 17. No walk-length track has been exported and filed, so "typical" for a real outing is not established here. The snapshot does carry a count without loading the track: `TrackDecision.pointCount` (`CartographyEntry.kt:108`), recomputed on every entry open since PR #73 (`CartographyEntryEntity.kt:55-63`).

### Which kinds are present, and their geometry

| Kind | Geometry in the type | Where the coordinate comes from | Fetch at resolve time |
|---|---|---|---|
| Tracks | `List<List<LatLng>>`, one inner list per kept track, oldest first | `TrackRepository.getById` → `TrackDao.getTrackById` + `getPointsForTrack` (`RoomTrackRepository.kt:33-36`) | **2 queries per kept track** |
| Finds | `List<LatLng>` | `MushroomLogEntry.foundAt: LatLng?` (`MushroomLogEntry.kt:40`) via `getForDay(dayKey)` then filtered by id | **1 query per distinct `foundOn` day** among kept finds (normally 1: an entry is one day) |
| Waypoints | `List<LatLng>` | Snapshot: `WaypointDecision.lat/lng` (`CartographyEntry.kt:113-119`) | **None** |
| Photos | `List<LatLng>` | `LogPhoto.latitude/longitude` (`LogPhoto.kt:34-35`), looked up in the caller-supplied `galleryPhotos` | **None here**, but the caller must already hold the whole gallery list |
| Offline regions | `List<Region>` (centre + `radiusKm`) | Snapshot: `OfflineRegionDecision.lat/lng/radiusKm` (`CartographyEntry.kt:122-129`) | **None** |

Withheld decisions are excluded at every step (`.filter { it.kept }`). A dangling reference contributes nothing rather than failing — the use case's doc comment is explicit that this is "the expected steady state this whole method exists to tolerate" (`GetCartographyEntryMapDataUseCase.kt:26-35`).

### Bounding box: computed on demand, not stored

There is no stored bounding box. `allPoints` is a computed property (flattens everything, every call) and the camera region is `GeoDistance.boundingRegion(allPoints)` computed at render time in the report screen (`CartographyEntryReportScreen.kt:331`). `boundingRegion` is a full pass over the points (min/max lat/lng, then a max-distance pass for the radius — `GeoDistance.kt:132-142`, quoted in §2). Nothing caches it.

### Lazy or asynchronous? The type is neither; the fetch is both

Nothing inside `CartographyEntryMapData` is lazy — every field is a materialised `List`. But obtaining it is a `suspend` call that does the queries in the table above, and the report screen gets it in a `LaunchedEffect(entry.id)` and renders **no map section at all until it lands** (`CartographyEntryReportScreen.kt:278-282`, `330-332`):

```kotlin
    LaunchedEffect(entry.id) {
        val resolved = getMapData(entry, galleryPhotos)
        mapData = resolved
        coveringOfflineRegion = getCoveringOfflineRegion(entry, resolved.allPoints)
    }
    …
        val resolvedMapData = mapData
        val mapRegion = resolvedMapData?.takeUnless { it.isEmpty }?.let { GeoDistance.boundingRegion(it.allPoints) }
        if (resolvedMapData != null && mapRegion != null) {
```

For a grid of N plates that is N of these resolutions, each 2K + D queries (K kept tracks, D distinct find days), on top of what the entries list already costs: `RoomCartographyEntryRepository.toDomain` issues **five ref queries per entry** (`getFindRefs`, `getTrackRefs`, `getWaypointRefs`, `getOfflineRegionRefs`, `getPhotoRefs` — `RoomCartographyEntryRepository.kt:68-81`) when `getAll()` maps the list, so the entries grid is already 1 + 5N queries before any plate is drawn.

**What a plate could draw with zero further fetch, from what the grid already holds in memory:** waypoint positions and offline-region circles, because both are snapshotted into the decision lists that `CartographyEntry` carries. Tracks, finds and photos all need the fetch. Whether that split is something a plate should exploit is a design decision this pulse does not make (see "Anything I decided" below).

---

## 2. How spread out is a typical entry?

### What constrains an entry to one area: nothing

An entry is "the user's own account of one day" (`CartographyEntry.kt:6`); its candidates come from `GetDerivedTripUseCase(entry.date)` (`CartographyViewModel.kt:486`, via `loadTripReport`), which is four **time**-scoped reads on one `LocalDayRange` (`GetDerivedTripUseCase.kt:31-58`). None of the four asks where anything is:

- **Finds:** `MushroomLogRepository.getForDay(range.foundOnKey)` — by the find's `foundOn` date.
- **Tracks:** `TrackDao.getTracksForDay` (`TrackDao.kt:38-45`):
  ```sql
  SELECT * FROM tracks
  WHERE startedAtEpochMillis < :dayEndExclusive
  AND (endedAtEpochMillis IS NULL OR endedAtEpochMillis >= :dayStartInclusive)
  ```
  Every track that *overlaps* the day, wherever it is — including a track still running (`endedAtEpochMillis IS NULL`).
- **Waypoints:** `WaypointDao.getForDay` (`WaypointDao.kt:18`): `WHERE createdAtEpochMillis >= :dayStartInclusive AND createdAtEpochMillis < :dayEndExclusive`.
- **Offline regions:** the trip report's candidates are `GetTripReportOfflineRegionsUseCase` (`CartographyViewModel.kt:491`) — every downloaded region on the device whose tiles contain *any one* coordinate the day produced (`GetTripReportOfflineRegionsUseCase.kt:22-30`). A region can be up to `MAX_RADIUS_KM = 24` (`OfflineMapRepository.kt:188`) around a centre the user chose, which need not be where the day's points are.
- **Photos** are not day-scoped at all: `onToggleKeptPhoto(photoId)` attaches any photo in the library by id, with no date or location check (`CartographyViewModel.kt:289-299`). A photo's coordinate, when it has one, comes from the camera fix or the imported file's EXIF (`LogPhoto.kt:16-27`), so a photo marker can be anywhere on Earth and from any date.

So an entry is purely "things kept from this day" plus "any photos attached". Three sites 40 km apart with a drive between them is representable and, if the walk between sites was recorded, the drive is a track too.

### What fit-to-bounds produces

The camera is not a true bounds fit. `boundingRegion`'s own doc comment says why: it reuses "the same `region`-driven camera control [SightingsMap] already has (see that composable's own doc comment on why this app has no true bounds-fit camera API) rather than adding one" (`GeoDistance.kt:117-121`). The function (`GeoDistance.kt:132-142`):

```kotlin
    fun boundingRegion(points: List<LatLng>): Region? {
        if (points.isEmpty()) return null
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        val center = LatLng(lat = (minLat + maxLat) / 2, lng = (minLng + maxLng) / 2)
        val maxDistanceMeters = points.maxOf { metersBetween(center, it) }
        val radiusKm = Region.clampRadiusKm((maxDistanceMeters / 1_000.0).roundToInt().coerceAtLeast(Region.MIN_RADIUS_KM))
        return Region(lat = center.lat, lng = center.lng, radiusKm = radiusKm)
    }
```

`clampRadiusKm` is `coerceIn(1, 50)` (`Region.kt:10-11, 19`). The map then picks a zoom from that radius by a four-step table (`SightingsMap.kt:1108-1113`):

```kotlin
internal fun zoomForRadiusKm(radiusKm: Int): Double = when {
    radiusKm <= 5 -> 13.0
    radiusKm <= 15 -> 12.0
    radiusKm <= 30 -> 10.5
    else -> 9.0
}
```

and applies it with `CameraPosition.Builder().target(cameraTarget).zoom(zoomForRadiusKm(region.radiusKm))` (`SightingsMap.kt:453-456`). Consequences, read off those two functions:

- **A single wood** (everything within ~1 km): radius clamps *up* to 1 km, zoom 13. The frame is fixed at zoom 13 whether the walk spans 200 m or 5 km — so a short walk is small in a large frame even in the easy case.
- **Two sites 20 km apart:** radius ≈ 10 km → zoom 12. **Three sites across 40 km:** radius ≈ 20 km → zoom 10.5.
- **Anything with a half-extent over 50 km:** the radius clamps at 50 and zoom stays 9, so **the outermost points fall outside the frame**. The report screen has this behaviour today; it is a live map, so the user can pan to them. A static plate cannot.
- **A kept offline region adds only its centre** to `allPoints` (quoted in §1), so a 24 km circle around a centre 10 km from the walk pulls the frame's centre and radius toward the region's centre, while the circle itself can still overrun the frame edge — the doc comment acknowledges the simplification.

What "two dots and empty space" looks like in dp is worked in §4 from the zoom table. **Inferred, not measured:** at zoom 9 the ground resolution is on the order of 150 m per dp at mid-latitudes, so a 2 km walk is about 13 dp long on any plate — a dot, not a shape.

### Whether anything lets a renderer choose a primary area

Present in the entry snapshot, without a fetch:

- `TrackDecision.distanceMeters`, `durationMillis`, `pointCount` per kept track (`CartographyEntry.kt:103-110`) — enough to pick the *longest* track, but its position needs the point fetch.
- `WaypointDecision.lat/lng` and `OfflineRegionDecision.lat/lng/radiusKm` — positions in hand.
- `FindDecision` carries `foundOn`, `ownIdentification`, `hasPhotos` — **no coordinate**.

Present with a fetch: `Track.originWaypointId` (`Track.kt:43`), an explicit pointer to the waypoint marking where a track started, `null` for tracks recorded before the HUD created one; read back through `GetTrackOriginWaypointUseCase`.

Not present anywhere: any cluster, density or "primary site" computation. `GeoDistance` has `boundingBox`, `boundingRegion`, `metersBetween`, `initialBearingDegrees`, `circlePolygonPoints`; nothing groups points.

**The problem exists.** Whether it bites depends on how the owner's own outings are shaped, which no data in this repo records.

---

## 3. What tiles are available offline, per entry

### The coverage logic, quoted

The report screen's offline toggle is shown only when `getCoveringOfflineRegion` resolves non-null (`CartographyEntryReportScreen.kt:418-419`: `val availableOfflineRegion = coveringOfflineRegion; if (availableOfflineRegion != null) {`). That lambda is `GetCartographyEntryOfflineRegionUseCase` (`AppContainer.kt:255`), in full (`GetCartographyEntryOfflineRegionUseCase.kt:31-44`):

```kotlin
class GetCartographyEntryOfflineRegionUseCase(
    private val offlineMapRepository: OfflineMapRepository,
) {
    suspend operator fun invoke(entry: CartographyEntry, points: List<LatLng>): OfflineRegionSummary? {
        if (points.isEmpty()) return null
        val keptRegionIds = entry.offlineRegionDecisions.filter { it.kept }.map { it.offlineRegionId }.toSet()
        if (keptRegionIds.isEmpty()) return null

        val downloadedRegions = offlineMapRepository.listRegions().getOrNull().orEmpty()
        return downloadedRegions
            .filter { it.id in keptRegionIds }
            .firstOrNull { region -> points.any { point -> isCoordinateWithinRegionTiles(point, region.region, OfflineMapRepository.MAX_ZOOM.toInt()) } }
    }
}
```

Three properties of this, each relevant to a snapshot:

1. **Scoped to the entry's kept regions only**, by design — the doc comment: "falling back to an arbitrary uncovering-but-downloaded region would serve unlabelled offline tiles for a day the user never associated with it, undoing that exclusion silently" (`:9-14`). So an entry whose user withheld (or never had) a region decision gets `null` even if the device holds a region that covers the walk.
2. **"Covering" means any one point inside the region's tile box at zoom 15** — `points.any { … }`. `isCoordinateWithinRegionTiles` (`OfflineTileMembership.kt:28-43`) tests one point against the region's bounding-box tile range. A day whose track starts inside a region and leaves it counts as covered; a snapshot of the whole extent would have tiles for part of it.
3. **`listRegions()` is a live `OfflineManager` read** (`MapLibreOfflineMapRepository`), a native call — another asynchronous step per entry.

### No covering region and no network

Nothing in this app has ever rendered a map from the offline store. `MapRenderMode.useOfflineTiles` (`MapSlot.kt:66-85`):

> Deliberately inert as of Stage 2e-i: nothing reads this field yet. [SightingsMapSlot]/[SightingsMap] still always request tiles from [basemap] regardless of this value — … actually swapping to the offline vector style, `OFFLINE_STYLE_URL`, is Stage 2e-ii, and carries a risk — whether a live style load is actually served from the on-device offline cache rather than attempting a network fetch — that cannot be settled without a device.

So what a live map, or a snapshotter, draws for an entry with no covering region and no signal is **whatever MapLibre's ambient cache holds for that area, else nothing** — and the repo's own reports say that cache is exactly the trap the pulse names. `docs/qc/dispatches/reports/2026-08-28-raster-capture-path-report.md` (summarised in `docs/qc/README.md:36`): ordinary live browsing "writes real raster PNG tiles into the same shared `tiles` table as plain ambient cache — durable-*looking* but unbounded, evictable, and dependent on this device's own browsing history". The beta trip-report template already has to ask testers about it (`docs/beta/trip-report.md:47-48`: "A phone that has had signal keeps map pieces it already showed, so 'the map worked offline' …").

Two further facts bear on option 2's premise of "real tiles":

- **The live basemaps are raster** (`Basemap.kt:142-169`: `USGS_IMAGERY_ONLY` maxZoom 15, `OPEN_TOPO_MAP` 17, `OSM_STANDARD` 19; `BasemapStyles.kt:93-94` `RASTER_SOURCE_ID`), fetched from public servers over the network. **The offline download is vector** — Protomaps tiles via the project's Cloudflare Worker, using a label-stripped style: "real geometry (roads, water, landuse, buildings), zero `text-field`/glyph layers" (`MapLibreOfflineMapRepository.kt:53-58`). A snapshot "with the tiles the user has" is therefore a different-looking map from the one the report screen shows online, and the report screen's own caption already says so: `"Offline maps show shapes only — no place names, road names, or icons."` (`CartographyEntryReportScreen.kt:549`).
- **MapLibre's store is redirected out of `cacheDir`** into `filesDir/maplibre-offline` (`MapLibreStorage.kt:53-58`) precisely so the OS cannot evict downloaded regions. The ambient cache shares that database (raster-capture-path report, `tiles` table shared by "every region (and the ambient cache)"), so it is *also* no longer OS-evictable — which makes "the map worked because I had signal there once" more likely on this app than on a default MapLibre install, not less.

### `MapSnapshotter`

Not referenced anywhere in `app/`, `docs/` or the build files (`grep -rli snapshotter` over `*.kt`, `*.kts`, `*.md`: no hits). It does ship in the pinned `org.maplibre.gl:android-sdk:13.5.0` — checked with `javap` against the artifact's `classes.jar` in this session's Gradle cache, the same method this repo already uses for MapLibre API claims (`MapLibreStorage.kt`, `OfflineMapRepository.kt`). The surface, as `javap` prints it:

```
public class org.maplibre.android.snapshotter.MapSnapshotter {
  public org.maplibre.android.snapshotter.MapSnapshotter(android.content.Context, org.maplibre.android.snapshotter.MapSnapshotter$Options);
  public final void start(org.maplibre.android.snapshotter.MapSnapshotter$SnapshotReadyCallback, org.maplibre.android.snapshotter.MapSnapshotter$ErrorHandler);
  public final native void setSize(int, int);
  public final native void setCameraPosition(org.maplibre.android.camera.CameraPosition);
  public final native void setRegion(org.maplibre.android.geometry.LatLngBounds);
  public final native void setPadding(int, int, int, int);
  public final native void setStyleUrl(java.lang.String);
  public final native void setStyleJson(java.lang.String);
  public final void addImage(java.lang.String, android.graphics.Bitmap, boolean);
  public final void cancel();
  protected final native void nativeInitialize(org.maplibre.android.snapshotter.MapSnapshotter, org.maplibre.android.storage.FileSource, float, int, int, java.lang.String, java.lang.String, org.maplibre.android.geometry.LatLngBounds, org.maplibre.android.camera.CameraPosition, boolean, boolean, java.lang.String, float, float, float, float);
  …
}
```

Three things the signatures establish, and one they do not:

- **It takes a `FileSource`** in `nativeInitialize` — the same store `MapLibreStorage.kt` redirects into `filesDir/maplibre-offline`. So a snapshotter would see whatever a `MapView` sees: downloaded regions *and* the ambient cache, with the contamination consequence in the previous subsection.
- **`setRegion(LatLngBounds)` is a native bounds fit** — the very thing `boundingRegion`'s doc comment says this app's own map path lacks. A snapshot could frame the true bounding box rather than the four-step zoom table. That does not change the §2 finding (a multi-site day still yields dots); it changes which dots.
- **Every setter is `native`**, so like every other MapLibre object here it is unconstructable under Robolectric.
- **It has an `ErrorHandler`**, but nothing readable here says whether a missing tile is reported as an error, drawn as a gap, or delivered as a success with blank areas. That remains a device question. What the repo does establish is that every MapLibre object with a native constructor is unconstructable under Robolectric (`SightingsMapOverlayDataTest.kt` doc comment: `GeoJsonSource` and every `Layer` "call a `native initialize` from their constructor … so constructing even one outside a real device or emulator throws `UnsatisfiedLinkError`"), which the pulse's "a native call tests cannot see" matches.

---

## 4. Sizing and density

### The grid as it is

`CartographyEntryListScreen.kt:84-92`:

```kotlin
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
```

with `Spacing.lg = 16.dp`, `Spacing.sm = 8.dp`, `Spacing.xs = 4.dp` (`Spacing.kt:32-41`). Each tile is `fillMaxWidth().aspectRatio(ENTRY_TILE_ASPECT_RATIO)` with `ENTRY_TILE_ASPECT_RATIO = 0.85f` (`:124-125`, `:163`) — width/height, so a tile is taller than wide. Inside, `padding(Spacing.sm)`.

**Column count:** `CartographyScreen(columns: Int = 2)` (`CartographyScreen.kt:113-114`). `JournalTab` (compact) passes nothing, so 2. `LogPanel` passes `EXPANDED_GRID_COLUMNS = 3` at both its call sites (`LogPanel.kt:331, 392, 446`), and its own comment says which classes it serves: "3, not [CartographyEntryListScreen]'s own compact default of 2 — the medium/expanded drawer panel is wider". `WindowWidthClass` has three values with breakpoints at 600 and 840 dp (`WindowWidthClass.kt:25-26`), and `AvailabilityScreen.kt:2013` branches only on `== COMPACT`: compact gets the bottom-nav `JournalTab`; medium and expanded both get `PermanentNavigationDrawer` with `PermanentDrawerSheet(modifier = Modifier.width(PERMANENT_DRAWER_WIDTH))` (`:2047`), `PERMANENT_DRAWER_WIDTH = 360.dp` (`:2149`), and `LogPanel` renders inside that sheet (`:1066-1067`).

**So the pulse's "full width on compact, 2-up on medium, 3-up on expanded" describes neither the code nor its column arithmetic.** Today:

| Window class | Host | Grid width | Columns | Plate width = (width − 2·16 − (cols−1)·8) / cols | Plate height (÷ 0.85) |
|---|---|---|---|---|---|
| Compact, 360 dp | `JournalTab`, full screen | 360 dp | 2 | **160 dp** | 188 dp |
| Compact, 393 dp | " | 393 dp | 2 | 176 dp | 208 dp |
| Compact, 412 dp | " | 412 dp | 2 | 186 dp | 219 dp |
| Medium and expanded | `LogPanel` in a 360 dp drawer sheet | 360 dp | 3 | **104 dp** | 122 dp |

The pulse's "roughly 170 dp" on a 360 dp compact screen at 2-up is 160 dp by this arithmetic (the 16 dp content padding on each side and the 8 dp gap account for the difference). A 1-up plate on compact — the schematic's "full width" — would be 328 dp. Whether the medium/expanded panel should stay at 360 dp and 3 columns for a plate grid is an owner decision; it is flagged below.

**Physical pixels (general knowledge, not from this codebase):** px = dp × (density dpi ÷ 160). 160 dp is 320 px on a 320 dpi device, 420 px at 420 dpi (the common ~1080 px-wide phone class), 480 px at 480 dpi. 104 dp is 208, 273 and 312 px respectively.

### Is a day's track legible at that size? What the numbers say, and what only hardware says

Take the compact 160 dp plate and the camera the report screen would use for the same data (`zoomForRadiusKm`, §2). The ground distance per dp at a MapLibre zoom level is inferred from the standard web-Mercator scale (about 78 km per 512 px tile at zoom 10, scaled by cos(latitude)); it is not something this codebase computes, and the point is the order of magnitude:

| Entry extent | Zoom applied | Metres per dp (45° N, inferred) | Whole plate spans | A 2 km walk is |
|---|---|---|---|---|
| Within 1–5 km | 13 | ~9.5 | ~1.5 km | the whole plate, or larger than it |
| 5–15 km | 12 | ~19 | ~3 km | ~105 dp |
| 15–30 km | 10.5 | ~54 | ~8.6 km | ~37 dp |
| > 30 km | 9 | ~152 | ~24 km | ~13 dp |

A Canvas renderer need not use that zoom table — it can fit to the actual bounding box — but the *ratio* between a walk and the entry's extent is what decides legibility, and that ratio is set by the data, not the renderer. For a one-wood day the walk fills the plate; GPS jitter at the sampler's own accuracy ceilings (30/50/100 m, `TrackRecordingMode.kt:27-29`) is 3–10 dp at zoom 13, so the line will look hand-drawn but continuous. For a three-site day the walks are 13–40 dp squiggles separated by empty plate. **Whether a 13 dp squiggle at 420 dpi reads as a shape or a smudge is a hardware question**; the pulse is right that nothing here can answer it, and no device build was possible in this session (no `/dev/kvm`).

Existing line weight, for reference: the kept-track line is `KEPT_TRACK_STROKE_WIDTH_PX = 6f` (`SightingsMap.kt:1082`), solid, round-capped, and the breadcrumb is the same width dashed. That is MapLibre's own unit, not `dp` — how it converts is not established in this repo.

### A Canvas that knows its own size: precedent exists, projection does not

There is one hand-rolled `Canvas` in production: `FruitingLagChart` (`AvailabilityResultsUi.kt:382-397`), which lays its bars out from `size.width` inside the `DrawScope` (`val barWidth = ((size.width - gap * (buckets.size - 1)) / buckets.size)`) at a fixed `height(160.dp)`. So "render differently by available width" is already done once, in the ordinary Compose way. `BoxWithConstraints` is used once, in `AvailabilityScreen.kt:1640` (held file; not read further). What would be new: any projection of latitude/longitude into a `DrawScope`'s coordinate space. `GeoDistance.boundingBox` gives a lat/lng box; nothing converts a box plus a `Size` into x/y. `SlippyMapTile.x/y` (used by `OfflineTileMembership.kt:31-37`) is the only Mercator conversion in the domain layer, and it produces tile indices, not pixels.

`FruitingLagChart`'s own neighbour states the testability limit plainly (`AvailabilityResultsUi.kt:404-407`): "the canvas above is unmeasurable in the Robolectric layout tests this project relies on (no rendering happens under Robolectric …), so the numbers this feature's honesty rests on live here, not only in pixels."

### What about a plate cannot be verified headless

- **Pixels, from any renderer.** Canvas draw calls do not render under Robolectric (above). MapLibre layers cannot be constructed at all (§3).
- **Insets.** CLAUDE.md's standing note: Robolectric reports zero window insets. A plate grid's column arithmetic depends on `LocalConfiguration.screenWidthDp` (`WindowWidthClass.kt:41`), which Robolectric *does* honour via qualifiers, so the width table above is testable; whether the grid's bottom row is hidden behind the real navigation bar is not.
- **Density.** Robolectric's density is what the test's qualifier says; no test here has exercised a plate at 420 dpi.
- **Snapshot cache correctness on device** — a stale-vs-fresh plate can only be seen.

What *can* be verified headless, for a Canvas plate: the projection math (pure Kotlin), the "which entries get a plate" decision, the bounding region, and the `MapOverlayContent`-style capture pattern `CartographyEntryReportScreenMapTest` already uses (a `MapSlot` stub that records what the screen asked for, `CartographyEntryReportScreenMapTest.kt:75-79`).

---

## 5. Caching and cost

### `updatedAtEpochMillis`: it exists, and it does not change when it should

The field is on the entity and the model (`CartographyEntryEntity.kt:31`, `CartographyEntry.kt:56`; column added in `Migrations.kt:629`). It is written by exactly two use cases:

- `CreateCartographyEntryUseCase.kt:22`: `CartographyEntry.draft(id = idGenerator(), date = date, updatedAtEpochMillis = now())`
- `CommitCartographyEntryUseCase.kt:16`: `val committed = draft.copy(isDraft = false, updatedAtEpochMillis = now())`

Every other write goes through `SaveCartographyEntryUseCase` (`SaveCartographyEntryUseCase.kt:12-17`), which is `repository.save(entry).map { entry }` — no stamp — and its own doc comment says what it covers: "Per-edit autosave write for an open Cartography entry (writing text, tags, or the kept-item selection) … since editing a committed entry happens in place — for one that isn't [a draft]." The ViewModel's `persist` (`CartographyViewModel.kt:466-470`), `onSaveEntry` for a committed entry (`:327-330`), `onSaveEntryAsDraft` (`:364-368`), photo attach/detach (`:289-299`) and the PR #73 track-snapshot write-back on open (`:166`) all call it. A `grep -rn updatedAtEpochMillis app/src/main` finds no other writer.

**So, for a committed entry edited in place — text, tags, a track withheld, a photo attached — `updatedAtEpochMillis` is unchanged.** A plate cached on it would keep showing the withheld track. It also cannot see changes that alter the map without touching the entry row at all: a kept track deleted from Records, a photo gaining a coordinate, an offline region deleted. The entries grid sorts by this field (`GetCartographyEntriesUseCase.kt:10`, `sortedByDescending { it.updatedAtEpochMillis }`), so changing its semantics reorders the grid — flagged below as an owner decision, not a fix.

### Where a generated image would live

Nowhere yet. **Nothing in the app caches a generated image.** `DecodedPhoto` (`DecodedPhoto.kt:48-73`) decodes from `filesDir` into a `remember(relativePath)` state in a `LaunchedEffect` — in-memory, per composition, discarded when the composable leaves the tree, redecoded on return, with `inSampleSize = 4`. The marker bitmaps `SightingsMap` builds (`plannedTripDiamondBitmap`, `waypointPinBitmap`, `:975-1020`) are generated per style load and handed to MapLibre. The only file writes under `cacheDir` are GPX exports (`TrackGpxExporter.kt:48`); photos are deliberately under `filesDir` (`PhotoStore.kt:13`, `FilePhotoStore.kt:18`), and MapLibre's store was moved out of `cacheDir` for the reasons in `MapLibreStorage.kt`. Where a plate image belongs — `cacheDir` (evictable, and this app's own "Clear cache" wipes it) or `filesDir` (counted as data) — is an owner decision.

### What the grid does on scroll, and what N renderers cost

`LazyVerticalGrid` composes the visible cells plus its prefetch window; this is standard Compose behaviour, not something the repo configures (no `LazyGridState` or prefetch settings are set — `CartographyEntryListScreen.kt:84-92`). **Inferred from the tile size:** at 160 × 188 dp on a compact screen with roughly 600 dp of grid visible, that is 3–4 rows × 2 = 6–8 tiles composed, plus a prefetched row. On the 360 dp drawer at 104 × 122 dp it is more like 5–6 rows × 3 = 15–18. Each composed plate would, with the current use case, cost the queries in §1 plus a `listRegions()` native call if a snapshot needs the coverage check; the resolutions run on the ViewModel/IO side and land asynchronously, so scrolling composes tiles that then fill in — the report screen already shows this shape for one map. For option 3 each composed tile is a `MapView` with its own render thread and tile fetches (`MapSlot.kt:200-203`), torn down and rebuilt as it scrolls out and back in, since `remember` is positional and does not survive the cell leaving composition (the very behaviour `CartographyEntryReportScreen.kt:165-178` arranges to avoid for its one map).

---

## 6. The colour question, settled

`SightingsMap` reads `MapPalette.DAY` unconditionally (`SightingsMap.kt:207-210`: "Always MapPalette.DAY, deliberately independent of nightMode"). `DAY` (`MapPalette.kt:149-160`):

```kotlin
        val DAY = MapPalette(
            sightingDot = 0xFF3B2E24.toInt(),
            sightingDotStroke = Color.White.toArgb(),
            sightingDotStrokeSelected = 0xFF2196F3.toInt(),
            connector = 0xFFC97B3D.toInt(),
            areaMarkerBackground = 0xFF2E5339.toInt(),
            areaMarkerForeground = Color.White.toArgb(),
            plannedTrip = 0xFF3B6EA5.toInt(),
            searchCentre = 0xFFB33B3B.toInt(),
            breadcrumb = 0xFF2979FF.toInt(),
            waypoint = 0xFFE0A030.toInt(),
        )
```

What the entry map draws for each kind (`initializeOverlayLayers`, `SightingsMap.kt`):

| Kind | Layer | Palette role | Value | Shape |
|---|---|---|---|---|
| Kept tracks | `LineLayer(KEPT_TRACKS_LAYER_ID)` `:606-614` | `palette.connector` | `#C97B3D` (amber) | solid, 6 px, round caps and joins |
| Finds | `SymbolLayer(FIND_LAYER_ID)` `:643-651` | `palette.areaMarkerBackground` | `#2E5339` (dark green) | `waypointPinBitmap` — a pin, bottom-anchored |
| Photos | `SymbolLayer(PHOTO_LAYER_ID)` `:653-661` | `palette.plannedTrip` | `#3B6EA5` (blue) | `plannedTripDiamondBitmap` — a diamond, centre-anchored |
| Waypoints | `SymbolLayer(WAYPOINT_LAYER_ID)` `:626-636` | `palette.waypoint` | `#E0A030` (gold) | `waypointPinBitmap` — a pin, bottom-anchored |
| Offline regions | fill + outline layers `:545-552` | `palette.areaMarkerBackground` | `#2E5339` at `OFFLINE_REGION_CIRCLE_OPACITY = 0.2f` fill (`:1083`), solid outline | circle polygon from `GeoDistance.circlePolygonPoints` (`:961-962`) |
| Live breadcrumb (not on an entry map) | `LineLayer(BREADCRUMB_LAYER_ID)` `:590-598` | `palette.breadcrumb` | `#2979FF` | dashed |

The code's own comments on the choices: the kept-track line "revived [`connector`] here rather than adding a new, contrast-unverified colour to MapPalette" (`:601-605`); find and photo markers are "the waypoint pin's own template, reused with a different colour/shape" (`:638-642`).

**So the schematic's open question has a definite answer — and it is that finds and photos have no palette role of their own.** A find borrows the foraging-*area* role (green), a photo borrows the *planned-trip* role (blue), and offline regions share the find's green. A colour key that says "green dot = find" is true on the entry map and false on the live map, where the same green is an area marker and the same blue is a planned trip. `MapPaletteTest`'s `markers()` set is the seven roles "a user must tell apart from each other at a glance" (`MapPalette.kt:101-103`); find and photo are not among them because they are not roles.

**One more thing the entry map draws that is not data:** `refreshOverlayData` always sets the search-centre source from `region` (`SightingsMap.kt:682`), and the layer is a `CircleLayer` in `palette.searchCentre` (`#B33B3B`, red — `:556-560`). On the entry map `region` is `boundingRegion`'s box midpoint, so a red dot is drawn at the geometric centre of the entry's extent, a point nothing happened at. No conditional hides it (no `visibility(` call anywhere in `SightingsMap.kt`). Reported, not fixed; whether a plate would inherit it depends on the renderer.

---

## 7. Entries with nothing georeferenced

### What an entry can consist of with no positioned item

From the model and the paths that build it:

- **Text and/or tags only.** `onStartEntry` on a day with no records keeps nothing and saves the draft (`CartographyViewModel.kt:105-118`); writing is optional but so is everything else (`CartographyEntry.kt:14-17`). The tile today shows "0 kept items" for it.
- **Photos without coordinates** — the ordinary case: "`null` is the ordinary, expected state, not an error or an incomplete one — most photos will never have one" (`LogPhoto.kt:18-19`). The use case's own doc comment names "an entry made entirely of photos with null coordinates" as the reachable empty state.
- **Finds without a coordinate.** `foundAt: LatLng?` (`MushroomLogEntry.kt:40`); its doc comment says "Nothing in this codebase constructs a location-less entry yet" (`:36-37`), and a `grep` for `location = null` / `foundAt = null` in `app/src/main` finds only that comment. So today a kept find normally has a coordinate — but the type allows otherwise and the use case already tolerates it.
- **Kept tracks with no usable points** — new since PR #73. A track whose every stored point is a network-provider fix reads back with `points = []` and `excludedPointCount > 0` (`RoomTrackRepository.kt:66-79`); the "no usable points" row suffix exists for exactly this (`CartographyEntryEntity.kt:62-63`). This case has a wrinkle, below.
- **Withheld everything.** Every candidate decided `kept = false`; the decision lists are full and the map has nothing.

**How common:** no usage data exists in this repo to say. Structurally, the text-only and photos-only cases are first-class ("selection alone … is a complete act of authorship", `CartographyEntry.kt:14-15`), not edge cases.

### The report screen's rule, quoted, and whether it is reusable

The doc comment (`CartographyEntryReportScreen.kt:136-140`):

> **No map section at all while loading, or if nothing resolved** ([CartographyEntryMapData.isEmpty]) — never an empty map frame with nothing on it. An entry made entirely of photos with no coordinates is a real, reachable state, reported as "nothing to frame" rather than guessing a default location (see [GeoDistance.boundingRegion]'s own doc comment).

The code (`:330-332`, quoted in §1) is two guards, not one: `takeUnless { it.isEmpty }` **and** `boundingRegion(...) != null`. That second guard is what actually catches the empty-polyline case — with one kept track that lost every point, `trackPolylines` is `[[]]`, so `trackPolylines.isEmpty()` is `false` and **`CartographyEntryMapData.isEmpty` is `false`**, while `allPoints` is empty and `boundingRegion` returns `null`. The screen shows no map, correctly, but by the second guard, not the first. The `isEmpty` doc comment ("`true` when nothing here resolved to a single drawable point or line") describes what the property is meant to mean, not what it computes for this input. The use case's test (`GetCartographyEntryMapDataUseCaseTest.kt`) has cases for "nothing kept" and "two kept tracks" but none for a track that resolves to zero points — so no test would catch a plate renderer that keys on `isEmpty` alone. **Reported, not fixed** (read-only pulse; and whether `isEmpty` should be `allPoints.isEmpty()` or the polyline should be dropped at the use case is a small decision the owner should make with the timestamp-filter context in view).

As a *rule* the screen's approach is reusable — "resolve, then draw only if the bounding region exists" — and `boundingRegion == null` is the right test. What does **not** transfer to a grid is *when* the rule can be evaluated: it needs the resolved data, i.e. the per-entry fetch. From the snapshot alone, a tile can know synchronously that it *is* georeferenced only if it has a kept waypoint or a kept offline region (positions in the decision lists); for kept tracks it can know `pointCount > 0` from the snapshot, which is strong evidence but not proof (the track may have been deleted since); for kept finds and attached photos it cannot know at all without the fetch. So "cover photo if there is one, else a contour texture" would be decided *after* an asynchronous resolution per tile, or decided optimistically from the snapshot and corrected — a design choice, flagged below.

---

## Overlap with the timestamp filter (PR #73, merged into this base)

Read, not acted on:

- **The read seam is the plate's data path.** `GetCartographyEntryMapDataUseCase` goes through `TrackRepository.getById`, which is `RoomTrackRepository.toDomain` — the one place the exclusion runs. A plate would draw filtered polylines automatically. `Track.excludedPointCount` is dropped at the use case (`.points?.map { … }` keeps only coordinates), so a plate could not say "N more not shown" without widening the type.
- **The recompute on entry open** (`CartographyViewModel.onOpenEntry`, `:155-170`) writes corrected `TrackDecision` snapshots back only when an entry is opened. An entry not opened since the filter landed still carries a pre-filter `pointCount` in the grid — so a tile that read `pointCount` from the snapshot to decide "has a track to draw" could be wrong in the direction of drawing an empty polyline (the §7 wrinkle).
- **Records row subtitles** are untouched by anything here.

---

## Test and skip counts as found

Run in this session on `0ca2f55` with the repo's own `./gradlew testDebugUnitTest`, after installing the Android SDK through `scripts/setup-android-sdk.sh` (this container had none); JUnit XML aggregated from `app/build/test-results/testDebugUnitTest/`:

| suites | tests | failures | errors | skipped |
|---|---|---|---|---|
| 155 | 1201 | 0 | 0 | 24 |

`BUILD SUCCESSFUL in 6m 50s`. The 24 skips are exactly the CI allowlist's identity set (below): 19 `AvailabilityScreenMapIconStackTest`, 2 `AvailabilityScreenTripPlanningFlowTest`, 1 `AvailabilityScreenOfflineCacheTest`, 1 `AvailabilityScreenWaypointFlowTest`, 1 `GenerateFungiIndexDbAsset`. **`JournalTabTest`'s "From Album on the edit form opens the picker and pulls the selected photo into the entry" passed on this run** (14 tests in the class, 0 failures); the known intermittent flake did not fire. Not touched.

The CI skip allowlist (`.github/workflows/ci.yml`, `SKIPPED_TESTS_ALLOWLIST`) holds 24 entries: 19 in `AvailabilityScreenMapIconStackTest`, 2 in `AvailabilityScreenTripPlanningFlowTest`, 1 each in `AvailabilityScreenOfflineCacheTest`, `AvailabilityScreenWaypointFlowTest`, and `GenerateFungiIndexDbAsset`. Source `@Ignore` annotations: 53 occurrences of the token across `app/src/test`, most of them the comment-plus-annotation pairs in the two `AvailabilityScreen*` files named in the 2026-08-31 audits.

---

## Questions the owner will decide — not answered here

Which renderer; what a multi-site plate shows; whether plates are worth it if only option 2 is viable; aspect ratio, column counts, what else sits on the plate. Nothing above chooses among them.

## Decisions this pulse did not list, found while reading — flagged, not made

1. **Column count and panel width on medium/expanded.** The pulse's 2-up/3-up premise is not the code's; today medium and expanded share one 360 dp drawer and 3 columns, giving a 104 dp plate. Whether the plate grid should widen the drawer, drop to 2 columns there, or accept 104 dp is a layout decision.
2. **What `updatedAtEpochMillis` means.** Stamping it on every save would make it a valid cache key and would also reorder the Entries grid on every edit (it is the sort key). Leaving it means a cache needs another invalidation signal. Either is a semantic change to a field with a reader.
3. **Whether a plate resolves synchronously from the snapshot (waypoints and regions only), or waits for the full fetch like the report screen does.** The data supports both; they look different while scrolling.
4. **Whether a kept offline region alone counts as "georeferenced."** Today it does — a region centre is in `allPoints`, so an entry with one kept region and nothing else gets a map with a 20 %-opacity green circle on it.
5. **Whether the red search-centre dot belongs on an entry map at all** (§6). It is drawn at a point nothing happened at.
6. **The `isEmpty` / empty-polyline inconsistency** (§7): fix the property, drop empty polylines at the use case, or leave both and rely on `boundingRegion`. Small, but it is in the path a plate would take.
7. **Whether a plate honours "kept only."** The use case does; a renderer reading decision lists directly would have to repeat the filter. Presumably yes, but it is a rule to state, not assume.

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed by reading the code on `0ca2f55`:** everything with a file:line above — the type and use case verbatim; the five per-entry ref queries in the repository; the two-queries-per-track and one-per-find-day fetch shape; the day-scoping SQL for tracks and waypoints; that photo attachment is by id only; that `updatedAtEpochMillis` is written only at create and commit; that `useOfflineTiles` is read by nothing; the coverage check's `any`; the layer-to-palette mapping and the `DAY` values; the grid's padding, gap, aspect ratio and column sources; the 360 dp drawer width and the `== COMPACT` branch; the absence of any `MapSnapshotter` reference and of any generated-image cache; the `isEmpty`-versus-`boundingRegion` behaviour on an empty polyline (by reading the two expressions, not by running them).

**Confirmed against the artifact, not the code:** `org.maplibre.gl:android-sdk:13.5.0`'s `classes.jar` (from this session's Gradle cache, `javap`) contains `org.maplibre.android.snapshotter.MapSnapshotter` (§3).

**Inferred:** metres-per-dp at each zoom (standard Mercator arithmetic, not from this codebase); physical-pixel figures (standard dp-to-px formula); how many tiles `LazyVerticalGrid` composes (from tile size and an assumed visible height); that MapLibre's `px` line width is not `dp`; that a snapshotter shares the same store as a `MapView` (general MapLibre knowledge, unverified against 13.5.0).

### What I could not determine

- What `MapSnapshotter` returns when tiles are absent — its `ErrorHandler` exists (§3) but nothing here says whether a missing tile is an error, a blank, or a success with gaps; a device test is the only way to know.
- What a live style load does with a downloaded region — the repo itself records this as unsettled without a device (`MapSlot.kt:69-75`).
- Plate legibility at real density — hardware only, as the pulse says.
- How spread out the owner's real entries are — no usage data, no walk-length track exported to the repo.
- Whether the report screen's map and the Maps tab's map are ever alive at the same time (two `MapView` instances) — not read far enough into `AvailabilityScreen.kt` to say, and that file is held.
- Whether the "From Album" `JournalTabTest` flake fired — see the test-count section for what ran.

### Premises in this pulse that were wrong

- **"2-up on medium, 3-up on expanded"** — the code is 2 on compact and 3 on both medium and expanded, inside a fixed 360 dp sheet (§4).
- **"On a 360 dp screen at 2-up, a plate is roughly 170 dp"** — 160 dp, after the grid's 16 dp padding and 8 dp gap (§4).
- **"The report screen's own doc comment about keeping to a single `MapView` instance"** — the comment is about a single *call site* within that screen so `remember` survives a `Modifier` change; it is not a rule against multiple instances, and no such rule exists in the repo (short version, row 3).
- **"An offline toggle that appears when a covering region exists"** — true as stated, but the toggle changes nothing about what tiles are requested; the pulse's framing of option 2 as "needs the tiles" understates it: no map in this app has yet been shown to render from the offline store (§3).
- **"Keyed on `updatedAtEpochMillis`" as a viable cache key** — the field exists but does not change on in-place edits of a committed entry (§5).
- The pulse says the timestamp filter is PR #73 and out of scope; it is merged into the base this pulse ran on, so its read seam is already the plate's data path (overlap section).

### Anything I decided that this pulse did not cover

- I treated "typical entry" as unanswerable from this repo and gave the sampler's ceilings plus the only real point counts filed, rather than guessing a number.
- I read `AvailabilityScreen.kt` only for the window-class branch and drawer width (lines 2013-2047, 2149) and `BoxWithConstraints`'s existence; the pulse holds that file and I made no further claims about it.
- I installed the Android SDK into this session (via the repo's own `scripts/setup-android-sdk.sh`) to run the unit suite for the counts the pulse requires, and unzipped the pinned MapLibre `.aar` from the resulting Gradle cache into the session scratchpad to check for `MapSnapshotter`; nothing was written into the repository by either.
- I filed this under `docs/audits/` and added its row to `docs/audits/README.md`, following the light-budget and track-point-filter pulses' precedent.
