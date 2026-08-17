# Forager

[![CI](https://github.com/slayer8366/Forager/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/slayer8366/Forager/actions/workflows/ci.yml)

An Android app that ranks which species are worth looking for, in a chosen
region and month, based on how often people have historically logged them
there on [iNaturalist](https://www.inaturalist.org/). Despite the name, it's
not limited to fungi: search by broad category (Fungi, Plants, Lichens) or
by a specific species.

This is a historical-frequency ranking over real observation data, not a
weather-style forecast or a fitted model: the app deliberately doesn't claim
more certainty than the data supports. See `AvailabilityForecast` and
`PredictAvailabilityUseCase` in `domain/` for how the ranking is computed.

## How it works

1. Search controls live in a **navigation drawer**, opened from the tune
   icon in the app bar. You pick a region there — either "use current
   location" (device GPS/network location, with a radius slider) or manually
   entered latitude/longitude — and a month. The drawer keeps the map, which
   is the primary content, at full height; a one-line strip under the app bar
   ("Fungi · August · 15 km") says what the current search is while the
   controls are hidden. See `ui/availability/AvailabilityScreen` for why the
   controls are not stacked above the results.
2. You pick what to search for: the **Fungi**, **Plants**, or **Lichens**
   quick-filter chips, or a specific species by name (autocomplete over
   `GET /v1/taxa/autocomplete`). Lichens has no distinct top-level group on
   iNaturalist — it's approximated via the Lecanoromycetes class, which
   covers most lichen species, and labeled as an approximation in the UI.
   See `domain/model/TaxonFilter`.

   **Fungi excludes most lichens.** iNaturalist files lichens under its Fungi
   iconic taxon (it calls the group "Fungi Including Lichens"), so an
   unfiltered Fungi search returns them ranked among the mushrooms — in one
   real 15 km search, three of the top five species were lichens. Fungi
   therefore subtracts Lecanoromycetes via `without_taxon_id`, and the
   Lichens chip is how you ask for them. Because that class is an
   approximation of "lichen", this removes *most* lichens rather than all of
   them: taxa from other lichenized classes (Candelariomycetes,
   Verrucariales and others) can still appear in Fungi results. Plants
   excludes nothing, and a species you searched for by name is returned
   as-is — an exclusion never overrides an explicit species choice.
   iNaturalist ignores unrecognised query parameters instead of rejecting
   them, so a dropped `without_taxon_id` would fail silently;
   `scripts/verify-lichen-exclusion.sh` re-checks against the live API that
   it still does something.
3. The app queries iNaturalist's `GET /v1/observations/species_counts` for
   verifiable observations matching that filter within the radius, filtered
   to that month across all years.
4. Species are ranked by observation count, with the top species normalized
   to a relative-likelihood of 1.0.
5. The **Map** tab — the tab the app opens on — shows the searched region on
   a **topographic** basemap (see the basemap selector below), with a small
   dot per individual verifiable
   observation (`GET /v1/observations`): real reported sighting locations,
   not just the aggregate ranking. Dots rather than osmdroid's stock pins
   because a dense radius merges a few hundred pins into one unreadable
   mass, which throws away the density that is the signal here.
   Observations iNaturalist doesn't expose a location for (e.g.
   conservation-sensitive taxa) are left off the map rather than guessed at.
   Sightings are fetched lazily, only when the Map tab is opened, so
   browsing the ranked list alone doesn't cost the extra API call.
6. **Foraging areas**, on by default, groups those dots into the spots that
   have produced *repeatedly*, and the drawer's toggle switches the layer
   back off to read the raw observations. It is the default view because
   where observations bunch together across many years is the strongest
   signal in the dataset, and drawing every one as an identical dot throws
   it away. Grouping is
   [DBSCAN](https://en.wikipedia.org/wiki/DBSCAN) in pure Kotlin
   (`domain/Dbscan`), run over the sightings the Map tab already fetched —
   it makes no extra API call. DBSCAN rather than k-means because it takes
   a distance radius instead of a preset cluster count, and because it
   labels isolated points as noise: one observation 8 km from anything else
   is not a foraging spot and isn't promoted into one. Distances are true
   great-circle metres (`domain/GeoDistance`), never Euclidean arithmetic
   over raw lat/lng degrees, which would distort clusters east–west as
   latitude rises. Each area reports its observation count, distinct
   species count, and most recent observation year. The two thresholds —
   how close counts as "the same spot" and how many finds make a pattern —
   are labelled adjustable assumptions in
   `ClusterForagingAreasUseCase`, not data-derived facts.

   **Which basemap draws the tiles is two separate decisions with two
   different lifetimes.** Which *service* to use — **OpenStreetMap** (the
   default) or **USGS** — is occasional, so it lives in **Settings ▸ Choose
   Maps Service**, reached from the sticky entry at the bottom of the search
   drawer. Which *mode* that service is in — topo or regular — is a
   during-the-walk decision made often, so it's a quick-fire icon overlaid on
   the map's own top-right corner rather than buried in a menu: "if a map has
   two modes, toggle the two." OpenStreetMap's two modes are OpenTopoMap and
   the standard OSM street map; USGS's are USGS Topo and USGS Imagery.
   Switching service never resets the mode — leave the icon on regular mode
   under OpenStreetMap and switch the service to USGS, and the map lands on
   USGS Imagery, not USGS Topo. All four tile sources come out of the pinned
   osmdroid's own `TileSourceFactory`, so there is no API key and no
   hand-written URL template. See `ui/map/MapService` and `ui/map/Basemap`.

   This replaces an earlier design (still visible in this project's git
   history) where all four basemaps sat in one flat dropdown in the app bar,
   defaulting to USGS Topo. **The default changed to OpenStreetMap** for the
   same reason USGS was never a hardcoded default there either: **USGS
   National Map covers the United States only**, and an opening basemap that
   is blank for every user outside the US is a worse trade for a first launch
   than it is for a browsing choice the user can already see and change. USGS
   Topo — the better read for a wooded search — is still one tap away in
   Settings. The two alternatives to stating the coverage limit outright are
   recorded in `Basemap`'s doc comment: detecting coverage and falling back
   silently (`CLAUDE.md` forbids an unlogged fallback, and the failure mode
   isn't even detectable — the service returns HTTP 404 outside the US, which
   is indistinguishable from a network error), and guessing from device locale
   or GPS.

   Zoom ceilings differ per basemap and are applied explicitly: USGS stops at
   15, OpenTopoMap at 17, OpenStreetMap at 19. This fixed a bug that predated
   the selector — osmdroid derives a `MapView`'s ceiling as the maximum across
   its *module providers*, and `MapTileProviderBasic` stacks a
   `MapTileApproximater` that claims 29, so the app previously let you zoom
   ten levels past what OpenStreetMap actually serves, into upscaled blur.
   `scripts/verify-usgs-basemap.sh` is the evidence behind the USGS numbers:
   it fetches real tiles and checks the response bodies really are images, so a
   200 carrying an error page can't pass for coverage. It also records that the
   service's own metadata advertises tile levels up to **23** while tiles stop
   at 16 — the app trusts the observed ceiling, per `CLAUDE.md`'s rule that a
   reported capability range is not an operating limit.

   **Settings ▸ Offline Maps downloads USGS tiles for a region you pick, for
   offline use — and only USGS.** The section is unreachable while
   OpenStreetMap is the selected service, not merely disabled-looking: both
   OpenStreetMap's and OpenTopoMap's tile providers prohibit bulk/prefetch
   downloading in their usage policies, and USGS's own low zoom ceiling (15)
   keeps a region download a practical size regardless. osmdroid's pinned
   artifact encodes the OpenStreetMap half of that directly — the standard
   map's `TileSourcePolicy` sets `FLAG_NO_BULK`, citing
   `operations.osmfoundation.org/policies/tiles/` — but carries no such flag
   for OpenTopoMap, so that half rests on a web search of the same domain and
   `opentopomap.org`'s own usage text rather than a fetch of either page:
   this environment's egress proxy blocks both directly. Worth a primary-source
   spot-check before relying on it further. See `domain/OfflineBasemapStyle`'s
   doc comment for the full citation trail. The download always targets
   whichever USGS style (Topo or Imagery) the quick-fire icon is showing at
   the moment "Download Maps" is tapped, stated on screen rather than left as
   a silent assumption; downloaded tiles are stored under the app's private
   files directory, not the cache directory ordinary browsing uses, so
   neither an OS cache-clear nor ordinary map panning can evict a region the
   user explicitly asked to keep. See `map/OsmdroidOfflineMapRepository`.

   **The numbering is a visiting order, not a walking route.** Areas are
   numbered by greedy nearest-neighbour from the search centre — head for
   the nearest area you haven't done yet — and the connectors between them
   are drawn *dashed* because they are straight lines between area centres
   and nothing more. This project has no trail data, no terrain, no
   land-ownership data and no path graph, only scattered coordinates over
   raster tiles, so a walking path is a capability it does not have; a
   solid line implying one could route you across a river, a motorway, a
   cliff, or private land. Per `CLAUDE.md` an unsupported capability is
   reported as unsupported rather than given a plausible-looking value, so
   the app ships the order and says plainly, on screen, that it is not a
   route. The ordering is also not optimal or shortest — greedy
   nearest-neighbour is neither, and it isn't described as either. If no
   group of observations meets the density threshold, the app says so
   explicitly instead of relaxing the threshold until something appears.
7. When the selected month is the current month, a **Current Conditions**
   card at the top of the **List** tab shows recent observed rainfall for
   the region (total precipitation and days since the last significant
   rain), pulled from
   [Open-Meteo](https://open-meteo.com)'s forecast API. iNaturalist has no
   answer for "has it actually been wet lately here" — historical
   observation frequency says nothing about this week. This is raw current
   data shown next to the ranking, not fused into it: there's no measured
   correlation in this codebase between rainfall and observation upticks,
   so `AvailabilityEntry.relativeLikelihood` is never adjusted by it (see
   `GetConditionsUseCase`'s doc comment and `CLAUDE.md`'s rule against
   unproven correction logic). The card is hidden entirely when browsing a
   different month, since today's rain says nothing about typical
   conditions in some other month.

## Project layout

- `data/remote/` — the only code that speaks Retrofit/iNaturalist's or
  Open-Meteo's wire formats (`INaturalistApi`, `OpenMeteoApi`, DTOs,
  `INaturalistClient`, `OpenMeteoClient`).
- `data/repository/` — maps the iNaturalist API onto the domain-owned
  `MushroomRepository` interface, including parsing iNaturalist's
  `"lat,lng"` location string and `observed_on` date; and the Open-Meteo
  API onto `WeatherProvider` (`OpenMeteoWeatherProvider`).
- `domain/` — pure Kotlin: `Region`, `LatLng`, `SpeciesObservationCount`,
  `Sighting`, `TaxonFilter`, `TaxonSearchResult`, `AvailabilityForecast`,
  `ConditionsSummary`, `ForagingArea`/`ForagingAreas`, `GeoDistance`
  (including its point-radius `boundingBox` helper, used by the offline-map
  region picker), `GeoBoundingBox`, `Dbscan`, `PredictAvailabilityUseCase`,
  `GetSightingsUseCase`, `SearchTaxaUseCase`, `GetConditionsUseCase`,
  `ClusterForagingAreasUseCase`, `OfflineMapRepository`/`OfflineBasemapStyle`/
  `OfflineMapInfo`, and the
  `MushroomRepository`/`LocationProvider`/`WeatherProvider` interfaces. No
  Android imports, so it's unit-testable headless (see `app/src/test/`).
- `location/` — the one place that touches `android.location` directly,
  behind the `LocationProvider` interface.
- `map/` — parallel to `location/`: the one place that touches osmdroid's
  `CacheManager` and the filesystem directly, behind the `OfflineMapRepository`
  interface. `OsmdroidOfflineMapRepository` downloads tiles for a picked
  region into a persistent store under the app's private files directory
  (not the cache directory `SightingsMap`'s ordinary browsing uses, so
  neither an OS cache-clear nor ordinary panning can evict it) and records
  what's downloaded in a small sidecar file rather than a Room table, read
  back by `getStatus()`; `PersistentTileWriter` is the hand-rolled
  `IFilesystemCache` that writes there instead of through either of
  osmdroid's own writers, both of which resolve their storage path from a
  process-wide `Configuration` singleton the browsing map already claims.
- `ui/availability/` — `AvailabilityViewModel` and the Compose screen: a
  `ModalNavigationDrawer` holding every search control over a map-first
  content area with the List/Map tab switch, plus a second drawer panel
  (`DrawerPanel.Settings`) for "Choose Maps Service" and "Offline Maps".
  `AvailabilityScreen`'s doc comment records why the controls are in a
  drawer and what was rejected; its `drawerPanel` state doc comment covers
  the two-panel switch.
- `ui/map/` — `MapSlot`, the seam the screen fills instead of naming
  osmdroid directly (the `MushroomRepository` pattern applied to the UI
  layer, so the screen can be composed in a test without starting tile
  threads); `SightingsMap`, the osmdroid `MapView` wrapped for Compose,
  including the dot marker for individual observations and the numbered
  foraging-area markers with their dashed order connectors;
  `ForagingAreaLabels`, which holds the single wording of
  the "not a walking route" disclaimer so the on-map info window and the
  on-screen caption can't drift apart; `Basemap`, the same
  own-the-vendor-boundary idea one level down — the basemap catalogue is pure
  Kotlin (labels, coverage limits, zoom ceilings, attribution, no osmdroid and
  no Compose), and `BasemapTileSources.tileSourceFor` is the only place it
  becomes an `ITileSource`; and `MapService`, which groups those four
  `Basemap`s into the two services (OpenStreetMap, USGS) Settings' "Choose
  Maps Service" picks between, each with a topo/regular mode the map's own
  quick-fire icon toggles.

These boundaries follow `CLAUDE.md`.

## Building

Requires the Android SDK (`compileSdk`/`targetSdk` 37, `minSdk` 26) and JDK
17+. Run `scripts/setup-android-sdk.sh` to install it, or point
`ANDROID_HOME` at an existing SDK install and create `local.properties`:

```
sdk.dir=/path/to/android-sdk
```

Then:

```sh
./gradlew assembleDebug       # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # runs the headless domain, ViewModel and layout tests
```

`assembleDebug` is followed by `verifyNothingTestOnlyReachesTheApk`, which
opens the built APK and fails the build if any test-only class or manifest
entry is inside it. The layout tests below need Compose UI Test and
Robolectric on the unit-test classpath; that task is what makes "these are
test-only" a checked fact about the artifact rather than a claim about the
Gradle configuration.

The layout tests run under Robolectric, which downloads an `android-all` jar
from Maven Central on first use.

The Gradle wrapper (`gradlew`) downloads its own Gradle distribution on
first run.

## Continuous integration

Every push to `main` and every pull request runs
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) on a clean Ubuntu
runner: it provisions the SDK with `scripts/setup-android-sdk.sh` — the same
script the section above tells you to run, so CI is a regression test for the
documented setup path — then builds the debug APK and runs the unit tests.
Either one breaking fails the run. Before this workflow existed the project
had no automated checks at all, and every merge to `main` was green on the
word of whoever last ran the build locally.

Two things are checked on the artifact rather than on the build
configuration, because the artifact is what gets installed:

- The APK's `versionCode`/`versionName` are read back with `aapt2 dump
  badging`, and the run fails if the build fell back to an `UNVERSIONED-*`
  identity or if the versionCode doesn't equal the checkout's commit count.
  CI checks out with `fetch-depth: 0` for that reason: `actions/checkout`
  clones shallow by default, and a shallow clone yields an APK that reports
  versionCode 1 and cannot install over a real build.
- The test summary fails the run if no tests ran or if any test was
  skipped — a quietly dropped Robolectric layout test would otherwise leave
  a green tick on a run that measured nothing. Per-suite test counts are
  printed to the run's summary page.

`assembleDebug` also carries `verifyNothingTestOnlyReachesTheApk` (see
above), so the test-only-code check runs on every push and PR too.

### Build artifacts

Each run publishes two artifacts, downloadable from **Actions** → the run →
the **Artifacts** box at the bottom of the run summary:

- `app-debug-apk` — the `app-debug.apk` that run built, so a build is
  obtainable without anyone building one by hand. Check its version in the
  drawer footer or with `aapt2 dump badging`; see "Which build am I
  running?" below.
- `unit-test-report` — the HTML test report and the JUnit XML it was
  generated from, uploaded even when the tests fail.

GitHub keeps both for 90 days.

## iNaturalist API access

Run `scripts/verify-inaturalist-access.sh` to confirm the environment can
reach `www.inaturalist.org` and `api.inaturalist.org`. No API key is
required for the read-only endpoints this app uses.

## Basemap tile access

Run `scripts/verify-usgs-basemap.sh` to re-check what the USGS National Map
services actually do: that they serve real image tiles for a US location at the
zooms the app allows, that they stop above zoom 16 and outside the United
States, and what tile levels their own metadata advertises. No API key is
required — USGS National Map is public domain — and none of the basemaps this
app offers needs one.

## Not yet verified

The app builds, links resources, and its unit tests pass, but nothing has
been *rendered* on a device or emulator — this environment has no hardware
virtualization (`/dev/kvm`), so the Android emulator isn't usable here.
Installing to a real device or an emulator on a machine with KVM is still
the verification step for anything about appearance. Layout geometry is a
separate question and is now measured headlessly; see below.

The foraging-area map layer **has** now been seen once, on a physical
phone, and two things came out of that. The connectors do render as
visibly dashed at the zoom a 15 km search opens at, which is the part
carrying the honesty burden — that much is confirmed. But the map was also
painting outside its
own rectangle: tiles over the tab row and over the caption below it, and a
dashed connector running up into the app bar. Nothing clipped the hosted
`MapView` to its Compose slot, and osmdroid draws beyond its viewport on
purpose (whole edge tiles; polyline geometry out to 2.2x the view's
half-diagonal) because it assumes the host clips. `SightingsMap` now sets
`Modifier.clipToBounds()` on the `AndroidView` and records the mechanism.

**The clip itself has not been re-checked on hardware.** It is reasoned
from the osmdroid and Compose sources, not observed. Still unchecked with
it in place: that tiles now stop at the map's edges, that a connector to an
area near the edge of the radius is cropped there rather than escaping, and
that no numbered marker the panel lists is left unreachable by panning.

The map-first layout's **geometry** is now measured, not just reasoned
about. `AvailabilityScreenLayoutTest` composes the real screen under
Robolectric across three device configurations — a small dense phone, the
same phone at a doubled font scale, and a larger phone — and reads back the
bounds Compose actually assigned. What that establishes: the map's slot gets
316dp of 640dp, 247dp of 640dp and 571dp of 891dp respectively; its top
lands exactly on the tab row's bottom edge rather than above it; the
visiting-order caption starts below the map's bottom edge; the Conditions
card is on screen with non-zero area on the List tab; and every drawer
control, plus the build-identity footer, is reachable and displayed. Those
tests were checked against the original defect — the control stack put back
in the unscrolled content column — and the map slot measured 0dp on all
three configurations, so they fail when the bug is present.

The map inside that slot is **stubbed** in those tests. They prove the
screen hands the map the right box; they prove nothing about what osmdroid
paints in it. The clip above is therefore still unverified, and so is
anything about rendering.

Still unchecked, and only a device or emulator can answer it: whether the
drawer's open/close gestures behave (swipe-to-open is disabled on purpose so
a horizontal drag over the map pans it instead), whether the `Scaffold`
insets actually keep content clear of the navigation bar, and how the small
dot markers for individual observations look at a dense radius.

### The topographic basemap, specifically

What **is** established about it, and how: the endpoints serve real tiles and
stop at the US border and above zoom 16, checked against the live services by
`scripts/verify-usgs-basemap.sh`, which inspects response bodies rather than
trusting a 200. The osmdroid side — source names, zoom ceilings, copyright
strings, the ArcGIS `z/y/x` URL order, and the tile-cache separation that keeps
two basemaps from mixing — is read off the pinned artifact by
`BasemapTileSourceTest`. That a basemap change leaves every app-drawn overlay
untouched, and that the zoom ceiling really moves with the basemap, are
measured against a real osmdroid `MapView` under Robolectric by
`SightingsMapBasemapSwapTest`; both were confirmed to fail when the swap and
the ceiling were disabled in turn, so they are not passing vacuously.

**Nothing about how it looks has been seen.** No topographic tile has been
rendered by this project. Two specific things follow, and neither is a
formality:

- **That topo tiles render at all** in the `MapView` — the URL is proven to
  return a JPEG, and osmdroid is proven to build that URL, but the two have
  never been observed joined up on a device.
- **Whether the overlay colours still read against topographic terrain.** This
  is a real, named risk rather than a shrug. The sighting dots are bark brown
  at ~70% alpha and the order connector is mushroom orange; both were chosen
  against OpenStreetMap's comparatively flat palette. USGS Topo draws contour
  lines in browns and tans and forest cover in green, and USGS Imagery is
  aerial photography where dark green canopy is exactly where mushroom
  observations cluster. Brown-on-brown and orange-on-contour-line are plausible
  legibility failures. The numbered area markers (white on forest green, opaque)
  are the least at risk.

  **The colours were deliberately not changed.** Adjusting them from reasoning
  alone would be the speculative correction `CLAUDE.md` rules out, and it would
  also alter the one part of this layer that *has* been confirmed on hardware —
  that the connector reads as visibly dashed. Re-tinting it unseen could spend
  that. The right sequence is to look at it on a device first; if the dots or
  the connector do wash out, the fix is a colour change with a reason, not a
  guess made in advance of the evidence.

The build-identity footer's version values are verified separately — they
were read off the packaged APK with `aapt2 dump badging`, not off the Gradle
config.

### The quick-fire mode toggle and the Settings panel, specifically

**Rendering is unverified, same as the rest of this section.** That the icon
sits legibly over real map tiles rather than under them or clipped by the
same bounds `SightingsMap`'s own clip-to-bounds fix addresses, and that its
placement and contrast read well against USGS Imagery's aerial-photo palette
specifically, have not been seen on a device. `AvailabilityScreenSettingsPanelTest`
proves the icon's *measured position* is inside the map's box and biased
top-right, and that tapping it changes which `Basemap` the map slot actually
receives — layout and wiring facts, not appearance ones.

### Offline map downloads, specifically

**Actual tile download/delete I/O is unverified.** Everything about
`OsmdroidOfflineMapRepository` that touches the network or the filesystem —
whether `CacheManager.downloadAreaAsyncNoUI` really writes through
`PersistentTileWriter` end to end, whether a downloaded region survives an OS
cache-clear the way it's designed to, what actually happens on a real network
failure partway through a download — is Android file and network I/O and
cannot be exercised on the JVM. What *is* verified, and how:

- The `CacheManager` API surface this class calls — the `(ITileSource,
  IFilesystemCache, minZoom, maxZoom)` constructor needing no live `MapView`,
  the `downloadAreaAsyncNoUI` progress-callback shape, and that osmdroid's own
  bulk-download policy check does not, by itself, block OpenTopoMap — was read
  directly from `osmdroid-android-6.1.20`'s attached sources, not assumed from
  its sparse Javadoc. See `OsmdroidOfflineMapRepository`'s doc comment for the
  citations.
- The sidecar-file format `getStatus()` reads is pulled out as pure
  `Properties` conversion functions and round-trip tested headlessly
  (`OfflineMapStatusFileTest`), including that a corrupted or partial file
  reads as "nothing downloaded" rather than crashing or guessing.
- The bounding-box math the region picker feeds to `CacheManager` is pure and
  unit-tested (`GeoDistanceTest`), including the antimeridian-wrap and
  near-pole cases.
- The ViewModel's loading → success/failure state machine
  (`AvailabilityViewModelOfflineMapsTest`) is exercised against a fake
  `OfflineMapRepository` this project fully controls, covering progress
  reporting, download/delete failure, and that invalid coordinates never
  reach the repository at all.
- That the "Offline Maps" section is structurally unreachable under
  OpenStreetMap and reachable under USGS is measured against the real
  Compose tree (`AvailabilityScreenSettingsPanelTest`), not just reasoned
  about.

**The OpenTopoMap tile-usage-policy finding is one step removed from its
primary source.** osmdroid's pinned artifact encodes the OpenStreetMap half
of decision #7 directly — `TileSourceFactory.MAPNIK`'s `TileSourcePolicy` sets
`FLAG_NO_BULK`, citing `operations.osmfoundation.org/policies/tiles/` by name
in the library's own source comment — but carries no such flag for
`TileSourceFactory.OpenTopo` at all, so nothing in the library blocks a bulk
request against it. The claim that OpenTopoMap's own tile server (a distinct
service, `tile.openmaps.fr`, run separately from the OpenTopoMap map style
itself) also prohibits bulk/prefetch downloading rests on a web search of
`opentopomap.org`'s and the OSM Foundation's own policy text, not a direct
fetch of either page — this environment's egress proxy blocks both
`opentopomap.org` and `operations.osmfoundation.org` directly, on both the
attempt during this task and an earlier one. Worth a primary-source
spot-check in an environment that can reach them before this is relied on
further; the enforcement in this app does not depend on osmdroid's own
(incomplete) policy check either way — see `OfflineBasemapStyle`'s doc
comment.

## Which build am I running?

Open the drawer, then tap **Settings** at the bottom; its own footer reads
`Build <versionCode> · <versionName>`, for
example `Build 9 · 1.0.9+g85fa6245`. The footer moved here from the bottom
of the search panel when Settings was added — see "Project layout" above.
The versionCode is the git commit count
— it is what Android compares when deciding whether an install replaces the
existing app or silently no-ops — and the sha names the exact commit. A
`.dirty` suffix means the build had uncommitted changes.

A versionName starting with `UNVERSIONED-` means the build could not derive
its identity (a tarball with no `.git`, or a shallow clone whose commit count
is meaningless) and fell back to versionCode 1. Such a build will not replace
anything on install. Build from a full clone to get a real version; see
`resolveBuildIdentity` in `app/build.gradle.kts`.
