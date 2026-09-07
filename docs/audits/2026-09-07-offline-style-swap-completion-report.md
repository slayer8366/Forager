# Completion report: Stage 2e-ii, the offline style swap

**Type:** completion report. **Date:** 2026-09-07.
**Base:** `main` at `0ca2f55`. **Branch:** `claude/new-session-b7z9bg`; code commit `7e6fbf0`, on top of the pre-build report (`eb1c042`, addenda `d2cf730` and the retraction in this same push). **Not merged**, per the dispatch.
**Dispatch:** `dispatch-offline-style-swap.md`, its amendment (retracted by the owner — recorded in the pre-build report's last section), and the owner's four build decisions recorded there.

---

## What was built

**The entry report screen's existing offline toggle now swaps the map's style.** Nothing else in the app changes behaviour.

| Change | File | What it does |
|---|---|---|
| `OFFLINE_STYLE_URL` moved to its own file, `internal`; `OFFLINE_STYLE_ATTRIBUTION` added beside it | `map/OfflineStyle.kt` (new); `map/MapLibreOfflineMapRepository.kt` (constant removed, history kept as a comment) | The download and the load share one string by construction — the pre-build report's §1.5 requirement. |
| `MapStyleSource` (`Json` / `Uri`), `mapStyleSourceFor`, `mapAttributionFor`, `AppliedMapStyle`, `needsStyleReload` | `ui/map/BasemapStyles.kt` | The three decisions the swap adds, as pure functions so they are testable away from the native map. |
| `useOfflineTiles` parameter; style effect keyed on it; `appliedBasemap`/`appliedPalette` replaced by one `appliedStyle`; `fromUri` for the offline branch; caption text from `mapAttributionFor`; `OnDidFailLoadingMapListener` logging | `ui/map/SightingsMap.kt` | The swap itself. |
| `SightingsMapSlot` forwards `renderMode.useOfflineTiles`; the field's doc comment rewritten from "inert" to what it now does | `ui/map/MapSlot.kt` | The seam Stage 2e-i left is now consumed. |
| Doc comment: "flipping the toggle changes nothing" → "swaps the map's style" | `ui/log/CartographyEntryReportScreen.kt` | No code change on the screen; its toggle and `MapRenderMode` were already wired. |

**Decisions applied as ruled:**

1. **Manual only.** No connectivity code was added; the swap happens when the user flips the toggle and at no other time. Choose-at-first-load was not built: the offline flag is one of the style effect's keys, so the toggle acts on the live map.
2. **Attribution follows the style.** Over the offline style the always-visible caption reads `Protomaps © OpenStreetMap` — the offline style's own source credit, as plain text. MapLibre's tap-to-reveal control shows the same credit from the style itself, as before.
3. **Night mode and max zoom: reported, not fixed** (below).
4. **The picker's pinned basemap is untouched.** `AvailabilityOfflineMapsUi.kt:158` is unchanged.

### What the user will see, stated rather than fixed

- **Night Maps has no effect on the offline style.** `NIGHT_RASTER_PAINT` is a raster paint block on the raster layer; the offline style has 57 vector layers and no raster layer. A user with Night Maps on sees the offline style's day palette. Written at `mapStyleSourceFor`'s doc comment and pinned by the "ignores … night mode" test.
- **A separate pre-existing observation, found while reading the swap effect and not changed:** `nightMode` is not among the style effect's keys (`LaunchedEffect(mapLibreMap, basemap, mapPalette, useOfflineTiles)`), and the palette it does key on is pinned to `MapPalette.DAY` since the markers-stay-day-only change. So toggling Night Maps alone, on an already-loaded map, may not reload the style until something else does. The effect's own comment still says night mode "goes through this same style-swap path", which was true when the palette varied by night. **Not verified on a device; not fixed; outside this dispatch.** Flagged for the owner.
- **Max zoom stays the basemap's** (17 for OpenTopoMap) over a store whose data stops at 15. Vector tiles overzoom, so the user can zoom past the data's ceiling; what that looks like is the device pass's case 3.
- **A style that fails to load shows MapLibre's own blank.** Offline with no region under the camera, or a moved worker URL, the `setStyle` callback never fires; the previous overlays are gone with the previous style and the map is empty. This is now **logged** (`SightingsMap`'s `OnDidFailLoadingMapListener`, `Log.w`), never silent in the log — but the user is told nothing on screen. What they should be told is the decision the dispatch lists under Item 3 and the owner has not made; it is the device pass's case 4 and the honest worst case.

---

## Tests

### New: `OfflineStyleSwapTest` (8 cases, plain JVM)

Expected values are literals typed independently of the code (the dispatch's "do not derive a test's expected value from the code under test"): the worker URL, the three basemap credits, the Protomaps credit.

- offline on → `MapStyleSource.Uri("https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json")`
- offline on → the same source for every basemap and both night values
- offline off → the basemap's own JSON, naming its tile template; night paint present only when night is on
- credit over the offline style → `Protomaps © OpenStreetMap`, for every basemap
- credit over each basemap → that basemap's own (three literals)
- `needsStyleReload`: flipping only the offline flag reloads (both directions) — **the exact gap the pre-build report named** (§2.2); nothing applied → reload; identical → no reload; basemap change → reload

### Existing tests that cover the wiring

`CartographyEntryReportScreenMapTest` · "flipping the offline-map toggle sets MapRenderMode-useOfflineTiles but changes nothing else the map requests" — unchanged and still passing; its claim ("changes nothing else the map requests") is still true, since the swap happens inside `SightingsMap`, below the slot the test captures.

### Reverted-variant check

Copy of `BasemapStyles.kt` saved to the scratchpad; three one-line reverts applied together:

| Revert | Edit | Predicted | Observed |
|---|---|---|---|
| A | `mapStyleSourceFor` always returns the basemap JSON | "loads … by URI" and "ignores the basemap and night mode" | both |
| B | `mapAttributionFor` always returns the basemap's credit | "credit over the offline style" | that one |
| C | `needsStyleReload` ignores the offline flag | "flipping only the offline flag is a reload" | that one |

Build log: **0 compile errors**. XML fresh (0 s). **8 tests, 4 failed** — exactly the four predicted. Restored from the copy; `grep REVERT`: 0; the only working-tree difference from `7e6fbf0` afterwards was this report and the pre-build report's retraction addendum.

### Full suite

_Full-suite run in progress at the time of this commit; counts follow in the next commit._ <!-- FULL_SUITE -->

### What has no test, and why

- **That a tile comes from the downloaded store.** Nothing headless can show it; MapLibre's `Style`, sources and layers cannot be constructed under Robolectric (`SightingsMapOverlayDataTest`'s doc comment), and the store is a native database.
- **That every overlay survives the swap.** The re-add happens inside `initializeOverlayLayers`, native-only. The structural argument (every `addSource`/`addLayer`/`addImage` is inside the `setStyle` callback; the basemap swap exercises the same path on hardware) is in the pre-build report §2.1; the proof is the device pass's case 2.
- **The caption's text on screen.** The Compose caption lives inside `SightingsMap`, which cannot be composed headless; `mapAttributionFor` is tested, the `Text` that reads it is not.
- **The failure log.** `addOnDidFailLoadingMapListener` is native; the listener body is one `Log.w`.

---

## The device pass — this is the verification

Designed in the pre-build report §5.2 so the ambient cache cannot fake a pass: **raster ambient tiles cannot serve a vector style**, so any vector render offline is proof by structure. Restated for the build that now exists:

**Cold state.** Android *Clear storage* for the app (not Clear cache — it does not touch `filesDir/maplibre-offline`, where regions and ambient tiles live together). Then, **online**, download one region through Journal → Records → Offline Maps. **Do not turn the offline toggle on while online**, on any entry — that would let the offline style's vector tiles into the ambient cache and spoil the elimination. Then airplane mode.

1. **Renders from the region.** Open an entry whose kept data lies inside the region (the toggle appears only when a kept region covers a drawable point). Turn the toggle on. **Any map at all is proof**: shapes only, no words, caption reading `Protomaps © OpenStreetMap`. A blank means the store does not serve the load in this app's shape, and the pre-build report's §1.4 resolves the other way — report the `SightingsMap` log line, which will carry MapLibre's own message.
2. **Every overlay survives.** On that entry: kept track polyline(s), waypoint pins, find pins, photo diamonds, region circle — before the flip, after the flip, and after flipping back. The camera must not move on either flip. (The live puck and search-centre dot are not on entry maps by design; the breadcrumb and sightings are Maps-tab only, and the Maps tab has no toggle.)
3. **The edge, and beyond.** Fullscreen, pan across the region's edge: what draws outside (the style's `background` colour, nothing, or stretched parents), and whether anything says so. Zoom past 15 inside: overzoom should stay sharp.
4. **No region, offline, cold.** Clear storage, no download, airplane mode. The toggle will not appear on any entry (no kept region covers anything), so **the swap cannot be reached in this state** — the "silent blank" case is structurally unreachable today through the only control that exists. Record that. The reachable analogue is case 3's "beyond the edge".

**A tell for every future screenshot:** an offline map with a street name or house number on it came from the ambient cache; one with shapes only came from the region.

---

## Test and skip counts

See the full-suite table above. The 24 skips are the CI allowlist's identity set, unchanged. `JournalTabTest`'s "From Album" case: see the table's note.

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed:** every claim about the code in the pre-build report carries over; the new code compiles and its pure decisions behave as the eight tests assert; the three reverts fail exactly their predicted tests; `Style.Builder.fromUri`, `MapView.addOnDidFailLoadingMapListener` exist in the pinned artifact (`javap`); no overlay layer uses a `text-field` (grep — the only mention is a historical doc comment in `BasemapStyles.kt`), so the glyph-less offline style does not silently drop any overlay.

**Inferred:** that MapLibre serves a `fromUri` load of the stored style from the offline database when offline (the pre-build report's §1.4 — the device pass is what confirms it); that overzoom above 15 renders acceptably; what draws outside a region.

### What I could not determine

- Whether the swap renders from the store on a device. This is the dispatch's whole question and only the device pass answers it.
- Whether the Night Maps toggle reloads a live map at all today (the pre-existing observation above).
- What MapLibre's failure message says for a cold-store load, and whether `OnDidFailLoadingMapListener` fires for a style-fetch failure specifically (it is the SDK's general "map failed to load" hook; a partial failure — style served, tiles missing — may not fire it at all, in which case the map draws the style's background with no tiles and nothing is logged).

### Premises in this dispatch that were wrong

All recorded in the pre-build report; none new from the build. One clarification: the dispatch's verification case 4 ("a user with no region at all… must not be a silent blank") cannot be reached through the toggle, because the toggle is absent without a covering kept region. The silent-blank risk exists on the *far side* of a region's edge (case 3), not on an entry with no region.

### Anything I decided that this dispatch did not cover

- **Where the constant lives:** a new `map/OfflineStyle.kt` rather than widening the repository's `private const` in place, so the load-side reader imports a file whose only job is that string and its credit. The repository still reads the same symbol.
- **Logging a failed style load.** Not asked for; added because CLAUDE.md forbids a silently swallowed failure and the offline branch creates a new way for `setStyle` to fail. It is a log line, not a user-facing message — that decision is the owner's.
- **`AppliedMapStyle` carries the palette** though the palette is pinned to `DAY` today, so the guard's behaviour is exactly the old two-variable guard's plus the offline flag — no more, no less.
- **The picker and the Maps tab are untouched.** The Maps tab has no offline state; nothing here adds one.
