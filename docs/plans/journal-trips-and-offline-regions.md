# Journal / Trips and offline region management: design decisions

Design only. Nothing here is built. Written against the MapLibre + PMTiles
stack on `claude/pmtiles-cloudflare-worker`, not the osmdroid stack on main.

## Confirmed context

Tiles come from an own-hosted Cloudflare Worker serving z/x/y vector tiles out
of a continental-US PMTiles archive in R2, maxzoom 14. Offline downloads go
through MapLibre's `OfflineManager` with `OfflineTilePyramidRegionDefinition`,
because that API wants a tile-server-shaped URL rather than a pmtiles source
directly. Downloading works. The remaining work is designing around it.

`Journal` is the compact-window label for the mushroom log, not a separate
feature. This plan adds Trips above the existing log entry; it does not rename
anything.

## Reuse rather than rebuild

- `GeoDistance.boundingBox` and `GeoBoundingBox` already do the point-radius
  math, unit tested including antimeridian and near-pole cases.
- `LocationResult.altitude` already carries a nullable real altitude, with the
  never-guess rule already applied.
- `formatDistanceKm` is the single route every displayed distance takes. A
  coverage gap in kilometres goes through it or it ignores the imperial toggle.
- The `location/`, `sensor/`, `map/`, `photo/` seam pattern: one Android
  boundary per package behind a domain interface. A track recorder is a fifth
  such package, not an addition to `AndroidLocationProvider`, because a
  foreground service has a lifecycle a one-shot fix does not.
- The DBSCAN foraging-area clustering is already the "named sites" concept.
  Derive site names from it rather than adding a user-maintained entity.

## Region management

### Storage

`OfflineManager` owns the regions in its own database. What the app adds is an
index and a screen.

The current sidecar Properties file describes one download. Multiple dated
regions need either a directory of sidecars or a Room table. Take the Room
table: regions now need querying by bounds, which a properties file cannot do.
That is `MIGRATION_4_5` alongside the trips tables, hand written, with
`exportSchema` already true.

Write metadata into the region's own metadata blob as well as the table: name,
centre, radius, zoom range, created timestamp. The blob is the only durable
label inside MapLibre's store, and the table is the source of truth so a region
can be rebuilt from scratch after a corruption or a library change. The reverse
is not recoverable.

### What the list shows

Name, centre, radius, size, download date, completion state. Size and tile
counts come from each region's `getStatus()`, never from a variable set during
the download. That is what makes the summary survive a cold start.

Per-region delete replaces the current all-or-nothing delete. Two caveats to
respect in the UI:

- Per-region sizes do not sum to disk usage. The resource table dedupes tiles
  across overlapping regions, so two overlapping downloads share bytes.
- Deleting one of two overlapping regions frees much less than its reported
  size. Do not promise a specific amount reclaimed.

### Tile budget

MapLibre's default offline tile count limit is 6000. At zoom 14 a tile is
roughly 1.7 km across at 45 degrees north, which matches the observed 71 tiles
for a 5 km radius. Scaling by area, a 15 km radius is on the order of 600
tiles, so the default allows roughly nine large regions.

Set the limit deliberately rather than inheriting it, and show the budget in
the management screen. A user should not discover the ceiling at a trailhead.

### Freshness

- No automatic expiry. No automatic deletion. An offline map that deletes
  itself on a schedule fails exactly when it was needed, with no connectivity
  to recover.
- Mark stale at roughly 60 days and badge it. Make the interval a setting.
- Prompt to refresh only on an unmetered connection, batched into one prompt
  covering every stale region, with a sticky snooze.
- Best placement for the prompt is trip start, before the user leaves.

Measure what a refresh actually transfers before tuning the interval. If it
revalidates and mostly returns 304s, the interval barely matters.

### Placement

Management stays in Settings where Offline Maps already lives. Add a coverage
indicator on the Maps tab that taps through, and draw region bounds as a
translucent overlay, so checking coverage does not require leaving the map.

### Cold-start default

The picker currently opens near the centroid of the archive bbox with the
radius back at its default. That drops a returning user roughly 2000 km from
anything they have downloaded. Restore the last picked centre and radius from
DataStore, which is user intent rather than derived state and so should not
live in `SavedStateHandle`.

## Style and attribution

Two items that belong to the migration but bear on offline specifically.

**Host glyphs and sprites yourself.** A style references them by URL and an
offline region download captures them alongside tiles. Pointing at a third
party means offline label rendering depends on that host having been reachable
at download time. A region that draws roads and water with no place names is
close to useless for navigation.

**OpenStreetMap attribution is now the app's responsibility.** Protomaps
builds are OSM data under ODbL, and osmdroid was previously supplying copyright
strings through `TileSourceFactory`. The `Basemap` catalogue already has an
attribution field.

**Coverage limit.** The archive stops at the continental US bbox, the same
shape of limit already documented for USGS. Per the existing rule, state it
rather than detecting it and falling back silently.

## Trips

A Trip is a recorded session: start, end, optional breadcrumb track, and the
finds logged during it. It is a new entity above the existing log entry. A log
entry gains an optional trip reference, and an entry created outside any trip
stays valid and unattached.

### Trip record

- Start and end timestamps, duration
- Breadcrumb track, optional, off by default
- Total distance walked
- Covering region parameters at the time of the trip: centre, radius, zoom
  range, bounds
- A rendered snapshot of the track over the map, captured at trip close
- Notes

The snapshot is the only payload a trip stores. Tiles are never copied into a
trip. Region parameters are about a hundred bytes, survive the region being
deleted, and let an entry offer a one-tap re-download next season.

### Track storage

- Append-only points table, so a killed process leaves a truncated track rather
  than nothing
- Every point stores its reported accuracy, and altitude where the fix reports
  one
- Filter incoming points by distance and accuracy, not a fixed time interval
- Keep a simplified copy for rendering, separate from the raw series

Accuracy matters beyond storage hygiene. GPS under dense conifer canopy runs
tens of metres of error and wanders, so a raw track appears to cross itself. A
find marker without its recorded accuracy will be trusted more next season than
it deserves.

## Coverage, not use

For each trip, test the logged coordinates against the bounds of downloaded
regions.

- Test against the downloaded rectangle, not the radius circle the picker works
  in. The circle under-reports at the corners.
- Weight partial coverage by segment length, not point count. Points are
  unevenly spaced once distance-filtered, and clusters where the forager
  stopped would dominate a point-count percentage.

Vector tiles overzoom cleanly, so a zoom-14 region still renders sharp well
past 14. Coverage is therefore a single containment test rather than a pair of
containment and zoom adequacy. This is simpler than the raster case would have
been.

Compute both directions:

- Region to trips and finds: how many sessions a download served, which is the
  keep-or-delete signal
- Trip to coverage gaps: how far the forager walked outside any region, which
  feeds an expand-this-region action

**Name the field coverage, not use.** Whether the map was looked at is not
measurable through the tile layer without hooking resource loading, which is
not a supported extension point. A field named `used` that means "the route
overlapped a downloaded rectangle" will be misread later.

Performance is a nested loop over tens of regions and a few thousand simplified
points. Compute the trip's bounding box first and skip regions that do not
intersect it. No spatial index.

## Privacy

- Private by default. Sharing is opt in.
- Breadcrumbs off by default. The toggle is easy to reach in Settings and
  persists across sessions. Persisting the toggle means the app is willing to
  record. A trip still needs an explicit start and stop, or a clean auto-close
  on a long stationary gap, or coverage grouping has no boundaries to group
  against.
- An armed recorder needs an unmissable ongoing notification and a real
  auto-close.

Leak paths, none of which involve an upload feature:

- Auto backup. `filesDir` is included by default, which currently covers the
  log database, the photos, and downloaded content. Use `dataExtractionRules`
  to exclude cloud backup while allowing device transfer, plus
  `fullBackupContent` for older devices. Excluding both would silently lose
  years of field notes on a phone upgrade.
- Photos stay app-private, with GPS EXIF stripped on capture.
- No coordinates in logcat, and no crash reporter breadcrumbs carrying position.
- Region bounds are patch-revealing even though they hold no recorded data.
  Same backup exclusion, same no-export rule.

### If and when export happens

- Tracks never leave the device under any setting. Simplifying a route still
  hands over the route.
- Find coordinates obscure by default, following iNaturalist's model: snap to a
  fixed grid cell containing the true point, randomise within it, and inflate
  the stated positional accuracy to the cell diagonal.

The cell must come from a fixed grid, not a box centred on the point, or many
finds at one patch can be averaged back to the centre. And the accuracy must be
inflated, or the export publishes a precise-looking coordinate that is wrong.

Obscured is not hidden on iNaturalist: project curators can see true
coordinates. Say so in the export UI. Most fungi carry no conservation status,
so automatic obscuring will not catch a chanterelle patch. Default to obscured
and make open the extra tap.

## Operational notes

- The archive is 8.8 GB against a 10 GB free tier, so a replacement cannot be
  uploaded alongside the old one and swapped atomically. A rebuild means
  deleting first and accepting a window with no tiles, or paying.
- The Worker URL is public and published in a public repo. A download loop is a
  few hundred requests. Consider rate limiting before someone finds it.

## Not yet verified, and how each part could be

Written first rather than last, because the largest piece of this feature is
the hardest to measure and that should be visible from the start.

**Measurable headlessly.** The point filter and simplification, the coverage
containment and segment-length math (pure, alongside the existing
`GeoDistanceTest`), the region table round trip and its migration against a
real database, the staleness threshold logic under an injected clock, and the
management screen's list and delete flows against the real Compose tree.

**Not measurable headlessly, and not claimable.** Whether a foreground service
survives Doze and OEM battery management across a four-hour walk. Whether the
ongoing notification stays visible. Whether a track has gaps. Whether a
download that loses connectivity partway resumes or corrupts. Whether offline
labels render when the device is genuinely offline rather than merely
simulating it. Whether region deletion reclaims the disk it claims to.

There is no emulator in this environment, so all of the second list needs a
device. The honest acceptance test for breadcrumbs is one real all-day walk,
planned as a step rather than treated as a nice-to-have. Everything above the
line can ship with tests; the feature working cannot be established by them.

## Open questions

- Whether a region refresh revalidates or re-downloads the full pyramid.
- Whether a foreground service with the location type lets the app stay on
  while-in-use location permission and skip background location, avoiding the
  Play Store prominent-disclosure review. Verify against the target SDK.
- Whether `setOfflineMapboxTileCountLimit` exists under that name in the pinned
  MapLibre version, and what it is currently set to.

## Handoff, 2026-08-20: Region management phase implemented

This session picked up the "Region management" section above and built it end
to end on `claude/plan-implementation-rjzmkr` (PR — see `docs/plans/README.md`
for the link once opened). Trips, coverage, and privacy/export below this
section are still design-only; nothing in this phase touched them.

**One of the plan's own open questions resolved.** `setOfflineMapboxTileCountLimit`
exists on the pinned `org.maplibre.gl:android-sdk:13.5.0` artifact, confirmed
with `javap` against the resolved `.aar`'s `classes.jar` — `public final native
void setOfflineMapboxTileCountLimit(long)` on `OfflineManager`, no getter, no
callback. `OfflineMapRepository.TILE_COUNT_LIMIT` (still 6000, the library's
own default — kept rather than replaced with a guessed number, since there is
no real usage data yet on how many regions a season of foraging actually
needs) is applied on every `OfflineManager` access rather than once at
startup, since the call is cheap, idempotent, and has nothing to avoid
re-setting.

**Built, hand-in-hand with the plan's own text:**

- `MIGRATION_4_5` / `OfflineRegionEntity` / `OfflineRegionDao` — the Room index
  the plan calls for, keyed on MapLibre's own native `OfflineRegion.getId()`
  rather than an app-generated id, so no bounds-matching heuristic is needed to
  reconcile a Room row with the `OfflineRegion` it describes.
- The region's metadata blob now carries name, centre, radius, and zoom range
  (previously just centre/radius/downloaded-at), and `listRegions()` rebuilds
  a missing Room row from that blob if the table is ever lost — the recovery
  path the plan's "Storage" section describes, actually implemented rather
  than just described.
- `OfflineMapRepository.download`/`deleteRegion`/`listRegions` replace the old
  single-region `download`/`delete`/`getStatus` — a download no longer deletes
  whatever was already on disk.
- The management screen (Settings → Offline Maps) lists every region with
  name/centre/radius/size/date, a staleness badge, and per-region delete. The
  tile-budget line and the "sizes don't sum to disk usage" caveat are both in
  the UI copy, not just this doc.
- `isOfflineRegionStale` (pure, injected-clock-tested) plus a
  DataStore-backed `MapPreferencesRepository` for the staleness threshold and
  the picker's last-picked centre/radius (the "Cold-start default" section).

**Deliberately not built this phase, per the plan's own section boundaries:**

- The coverage indicator and translucent region-bounds overlay on the Maps tab
  ("Placement") — needs the Maps tab's own rendering work, not just the
  region index.
- The connectivity-aware, batched, snoozable refresh prompt ("Freshness") —
  needs connectivity monitoring and a natural trigger point (trip start),
  neither of which exist yet since Trips isn't built.
- Host-your-own glyphs/sprites and the `Basemap` attribution field ("Style and
  attribution") — orthogonal to the region index; touches the Worker and the
  live-rendering style, not the offline download path.
- Everything under Trips, Coverage, and Privacy below — a new `RecordedTrip`
  entity (name chosen to pair with the existing `PlannedTrip`, distinct from a
  `TripSession` naming considered for the future foreground-service recorder
  itself), the breadcrumb track table, the coverage containment/segment-length
  math, and the backup-exclusion/EXIF-stripping/obscured-export work.

**Update, same day, hardware-confirmed by the owner:** three regions downloaded
on a real device, force-closed, cleared from recents, and cold-restarted — all
three survived and listed correctly, not just one. This is the multi-region
generalization of the single-region restart-persistence check PR #25 already
confirmed (`pmtiles-worker-android-wiring.md`'s own handoff), now re-confirmed
against the Room-indexed multi-region path this phase adds.

**Update, same day:** the owner also found, on the same device, that downloaded
regions were counted under Android's "Cache" storage bucket rather than "Data"
— confirmed in code (`javap` against the pinned MapLibre artifact: the API is
literally `FileSource.getResourcesCachePath`/`setResourcesCachePath`) and fixed
by redirecting MapLibre's resource storage to a subdirectory of `filesDir`. Not
listed as an open question in the plan's original text, but exactly the kind of
gap the plan's "Freshness" section (no automatic deletion) depends on not
existing — Android can clear app cache under storage pressure, and this app's
own "Clear cache" control would have wiped every downloaded region with it. See
`com.forager.app.map.MapLibreStorage.ensureMapLibreStorageOutsideCache`'s doc
comment for the fix and why it isn't in `Application.onCreate()` despite that
being the more obvious place (it broke 171 unrelated Robolectric tests — no
test had ever exercised real MapLibre code before, and no test should have to
just because storage init moved).

**Not verified, and not claimable from this sandbox** — same reasoning the
plan's own "Not yet verified" section gives, applied to what actually got
built: whether a second/third region *overlapping* an existing one behaves
correctly against the real `OfflineManager` (the two regions confirmed above
were not overlapping), whether deleting one of two overlapping regions
actually frees only the bytes it should, and whether a region rebuilt from its
metadata blob (Room row deliberately dropped) round-trips on hardware. All
three need a physical device; this sandbox has none.

**Next, in priority order for whoever picks this up:**

1. Hardware-verify the three items directly above before trusting this phase
   under real multi-region use.
2. Resolve the `RecordedTrip`/`TripSession` naming split for real once the
   Trips entity is actually built — it was decided as a naming convention
   this session, not yet exercised by real code.
3. Trips: the recorded-session entity, breadcrumb track table, and foreground
   service — the largest remaining piece, and per the plan's own framing, the
   one hardest to verify without a device.
4. Coverage math, once both regions and trips exist to compute it from.
5. Privacy/export — independent of the above, could be picked up any time.
