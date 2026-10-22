# Three map types: verify-first report (dispatch 2026-10-22-01 Part B, with Amendment 1)

**Coder:** Hazel. **Model:** Claude Opus 5.5 (`claude-opus-5-5`). **Date:** 2026-10-22.
**Base:** `origin/pre-main` at `01c7c5b56300bcf7ba4379391057eeef0941d2e4`, the dispatch's assumed base, checked against the remote before reading. Every file:line below is at that commit.
**Decisions read:** RECORD -1554 and -1555 (records-after-173).
**Scope:** read only. Nothing is built for Part B, and no PR is opened. This branch holds only this file. I didn't add the `docs/audits/README.md` row: the index is a serialization point, so the planner can add it with the record entry.

**Stop:** item 4 fails. The three chips don't fit on one line at the S22's width. The dispatch and -1555 send that to the owner. Items 1, 2, 3 and 5 are reported below with the decisions they need.

---

## Item 4. The chip row: **does not fit (STOP, owner's decision)**

**Computed, not measured on a phone.** The row needs about **430 dp**. The S22 has **352 dp**, so it is about 78 dp short (22%).

| Chip | Text width | Chip width (text + 32 dp) |
|---|---|---|
| Forager Trail Map | 120.4 dp | 152.4 dp |
| Topographical | 97.5 dp | 129.5 dp |
| OpenStreetMap | 107.7 dp | 139.7 dp |
| Two gaps of `Spacing.xs` (4 dp) | | 8 dp |
| **Row** | | **429.6 dp** |

**How the widths were computed:**
- **Font and size.** The chip label is Material3's `FilterChipTokens.LabelTextFont`, which is `LabelLarge`: 14 sp, weight Medium, tracking 0.1 sp. I read this from the `material3-android:1.5.0-alpha26` sources, the version in `gradle/libs.versions.toml:19`. The app's typography swaps only the family, to Noto Sans (`ui/theme/Typography.kt:20-24, 51`). So the text is `res/font/notosans_medium.ttf` at 14 sp.
- **Text widths.** Each label's glyph advances, read from that font file with fontTools, times 14/1000, plus 0.1 sp tracking per character. Kerning isn't applied. It could move each label by a few dp, which is far less than the 78 dp shortfall.
- **Chip padding: 32 dp per chip.** `FilterChipDefaults.ContentPadding` is 8 dp on each side (Chip.kt:2263). With no icons, `ChipContent` lays out three children with an 8 dp `ChipArrangement`: a zero-width spacer, the label, and another zero-width spacer (Chip.kt:3366-3405). The chip is `width(IntrinsicSize.Max)`, so the label carries 8 + 8 dp on each side.
- **The available width.**
  - **The S22 window:** 384 dp wide (`AvailabilityScreenReturnRouteTest.kt:347`, "384 dp, the S22").
  - **The sheet's padding:** 16 dp on each side (`MapLayersSheet.kt:263-267`, `Spacing.lg`), which leaves 352 dp.
  - **The gaps:** 4 dp between chips (`MapLayersSheet.kt:277-279`).

**At larger font scales** the row needs 462 dp at 1.1× and 527 dp at 1.3×.

The labels alone, with no chip padding at all, already need 325 dp plus the gaps. So no padding tweak makes the row fit. Today's row (Street, Topographical) is 207 dp.

**What happens if it's built as is.** The row is `fillMaxWidth()` with `spacedBy(…, CenterHorizontally)` (`MapLayersSheet.kt:277-286`). The third chip would get whatever width is left and its label would be cut or wrapped. I didn't render this.

**Options for the owner.** I'm not choosing one. Each is written as what the user would see.
1. **Wrap to two lines.** "Forager Trail Map" and "Topographical" on the first line, "OpenStreetMap" under them. It needs a FlowRow, so the order holds left to right, top to bottom.
2. **Shorter labels.** For example "Trail Map · Topo · OSM". This changes words the owner chose in -1555.
3. **A scrolling row.** The third chip sits partly off the right edge. That hides an option, which goes against "Don't make me think".
4. **A vertical list of three rows,** like the Overlays switches below it. The order becomes top to bottom.

**To confirm on the phone:** the S22 at its default font size, Layers sheet open, a screenshot of the row. Device check step 1 in the dispatch covers this.

---

## Item 1. The one-time move from Street to Trail Map

**Read.** The choice is stored as a string key under `map.basemap` (`domain/BasemapPreferenceRepository.kt:9-16`).
- **On launch:** `AvailabilityMapDisplay.loadBasemapPreference` (`ui/availability/AvailabilityMapDisplay.kt:242-266`) reads the key.
  - Nothing stored gives `MapMode.DEFAULT`.
  - A key that names no mode gives `REPLACEMENT_FOR_UNKNOWN` (Topographical). That case is logged and the replacement is stored, so the log fires once.
  - A failed read is logged and gives `DEFAULT`, storing nothing.
- **On a pick:** `storeBasemap` writes `mode.storageKey` (`:224-229`).
- **The keys today:** `STREET` is stored as `"street"` and `TOPOGRAPHIC` as `"topographic"` (`ui/map/MapMode.kt:36-37`).

**Two mechanisms. Both satisfy -1554, and the choice is the planner's.**

- **(a) Trail Map takes the key `"street"`, and OpenStreetMap gets a new one, e.g. `"openstreetmap"`. (My recommendation.)**
  - Anyone with `"street"` stored opens on Trail Map. Anyone with nothing stored opens on `DEFAULT`, which is Trail Map.
  - Picking OpenStreetMap stores `"openstreetmap"`, and that survives restarts.
  - The move is "once" because nothing ever writes `"street"` for OpenStreetMap again. There's no special read, so nothing needs logging: no fallback fires.
  - **Cost:** the key `"street"` means "our map" from then on, and the name will read oddly. `MapMode.kt:29-33` says never to change a key so that a stored value can't be orphaned. This doesn't orphan anything, because `"street"` still names a mode, but its meaning moves on purpose. With the switch on, as #359 had it, `"street"` already drew our map, so this follows what the key already meant there.
- **(b) Two new keys, e.g. `"trail"` and `"openstreetmap"`, plus a one-time read of `"street"`.**
  - When the stored key is `"street"`, the load gives Trail Map, logs one line ("stored Street moved to Forager Trail Map, RECORD -1554"), and stores `"trail"`. The line fires once, the same pattern as the unknown-key path at `:250-258`.
  - **Cost:** a third branch in `loadBasemapPreference`, with its own test and revert check, and `"street"` becomes a retired key that has to be kept readable for good.

**For both:**
- **Downgrade.** If a phone downgrades to an older build, the new key or keys read as unknown there. That build opens on Topographical, logs it, and overwrites the key with `"topographic"`. That's harmless, and nothing in the database is affected.
- **Stays as is:** `REPLACEMENT_FOR_UNKNOWN` stays Topographical (-1297) unless the owner says otherwise.
- **The test** goes through the real entry point: a fake repository holding `"street"`, `loadBasemapPreference`, the map opening on Trail Map, a pick of OpenStreetMap, a reload, and the map opening on OpenStreetMap.

---

## Item 2. "Signal lost" on OpenStreetMap

**Detection: no new permission, no polling loop.**
- **What's already declared.** `ACCESS_NETWORK_STATE` is already in the merged manifest by inference. I read each library's manifest:
  - MapLibre `android-sdk-13.5.0.aar` declares it (and `ACCESS_WIFI_STATE`), at its manifest line 15.
  - `work-runtime-2.12.0.aar` declares it at its manifest line 24.
  - The app's own manifest declares neither and has no `tools:node="remove"` (`app/src/main/AndroidManifest.xml`).
  - I didn't build the merged manifest myself. The journal-backup report read a merged debug manifest with it present (`docs/audits/2026-09-29-journal-backup-completion-report.md:146`, at that date).
  - It is a normal-protection permission, so no prompt.
- **The detector I'd propose.** `ConnectivityManager.registerDefaultNetworkCallback` (minSdk 26, `app/build.gradle.kts:251`), reading `NET_CAPABILITY_VALIDATED` from `onCapabilitiesChanged`, plus `onLost`.
  - It is callback-driven, not a loop.
  - It sits behind an interface the project owns, as CLAUDE.md asks for external integrations, so the switching logic is testable headless.
  - No code uses ConnectivityManager today (grep of `app/src/main`).
- **The other candidate, OpenStreetMap's raster tile errors.**
  - **The hook exists:** `MapListeners.kt:56` sees `TileOperation.Error` for every source.
  - **The problem:** a tile error also means a 404, a 429 or a slow server. Used alone, it would switch a user who has signal to Trail Map.
  - **What it adds:** it catches the case validation misses, a network that is validated but too weak to load tiles.
  - **So it's a choice.** Validated network alone is simple, but on a weak signal OpenStreetMap stays up with grey areas. Validated network plus a tile-error count covers the weak signal, at the cost of some false switches. The planner's call; I'd start with validated network alone.

**How Trail Map is drawn when the signal is lost.** This matters more than it looks.
- **Recommended: the existing offline branch.** That is `mapStyleSourceFor(useOfflineTiles = true)` with `currentOfflineRenderStyleUrl` (`BasemapStyles.kt:287-297`, `MapStyleReload.kt:69`, `MapLibreOfflineMapRepository.kt:261-273`).
  - **Why not the online style:** if the online Trail Map style were loaded by URL with no signal and failed, `OwnStreetFallback` would fire, because it counts any online load of our style (`OwnStreetFallback.kt:42-47, 70-75`). That would switch the map to OpenStreetMap's raster for the rest of the process. This is the one place the two rules collide.
  - **Why the offline branch avoids it:** the fallback never fires in offline mode (`fallsBack` returns false when `useOfflineTiles`). So its rules stay untouched, as the dispatch requires, and nothing is requested from OpenStreetMap's tile host.
  - **Limit 1, older regions:** the offline branch loads the style some downloaded region holds. A user whose only regions predate Trail Map sees the older label-less offline style (`offline.json`, -1294), not Trail Map's look, until they download again.
  - **Limit 2, no regions:** a user with no regions at all gets MapLibre's blank, as offline mode does today.
  - **Not verified:** whether MapLibre would serve the style and tiles from its ambient cache with no region held. That needs a phone.
- **Rejected: loading `STREET_STYLE_URL` online while offline.** It hits the collision above unless the fallback's rules change, which the dispatch forbids.

**Avoiding flip-flops: hysteresis and one style per state.**
- Switch to Trail Map only after the network has been unvalidated or lost for a short time, say 5 s.
- Switch back to OpenStreetMap only after it has been validated for longer, say 30 s.
- Each switch is a full `setStyle`, and the camera is kept as on any basemap swap (`MapStyleReload.kt:85-106`).
- The two numbers are a proposal, not measured.
- The timers are one `delay` each per transition, not a loop. Any new ViewModel with a timer follows CLAUDE.md's `runRecordingTest` shape, so a failing test can't leave it scheduled.

**No stop here.** Not every option needs a new permission or a polling loop.

**Open for the owner: Topographical with no signal.** Topographical is online-only too, and offline downloads are Trail Map only. -1554 states the signal-lost rule for OpenStreetMap only. With no signal, a Topographical user sees grey wherever nothing is cached. Should Topographical also show Trail Map until the signal returns? I haven't assumed either answer.

---

## Item 3. Topographical at night: what draws underneath

**Read.** Below zoom 9.7, a night Topographical map draws another map under the topo layer (`nightLowZoomBasemap`, `BasemapStyles.kt:142-143`). Above that it draws topo alone, with a crossfade over 9.5 to 9.7.
- **On pre-main today** (switch `false`, `StreetStyle.kt:21`), underneath is **OpenStreetMap's raster** (`styleJsonFor` → `nightStyleJsonWithBasemapBelow`, `BasemapStyles.kt:145-195`).
- **With the switch on, as #359 has it,** `ownStreetStyleSourceFor` returns `StreetWithTopoOver(STREET_STYLE_URL, …)` (`BasemapStyles.kt:251-257`), so underneath is **our own vector style**, now Trail Map. If `OwnStreetFallback` has fired in that process, it's the raster OpenStreetMap again (`OwnStreetFallback.kt:18-20`).
- It doesn't depend on any other choice the user made. Only one map type is stored, and the underlay is fixed per type.

**Confirmed: the planner's expectation holds.** Once the switch is gone, Topographical at night draws Trail Map underneath at low zoom, with OpenStreetMap's raster only after the -1513 fallback. I see no reason to make it follow anything else.

**One point to note, not a decision.** A night Topographical user who loses signal gets neither the topo tiles nor the online Trail Map underlay. That's the open question in item 2.

---

## Item 5. Every reader of `STREET_SERVED_BY_OWN_MAP`, and what each becomes

The switch is read in `app/src/main` at the places below (grep of `STREET_SERVED_BY_OWN_MAP|ownStreet`, at 01c7c5b56). With Trail Map and OpenStreetMap both always present, the switch stops meaning "which Street". Its run-time value, `OWN_STREET_FALLBACK.ownStreet()`, becomes "Trail Map hasn't fallen back".

| Reader (file:line) | Today (switch off) | Becomes |
|---|---|---|
| `map/StreetStyle.kt:44` `offlineDownloadStyleUrl` | `offline.json` | always `STREET_STYLE_URL` (Trail Map), whatever the map type. -1554: "Offline downloads → Trail Map only". |
| `StreetStyle.kt:58` `offlineRenderStyleUrl` and `MapLibreOfflineMapRepository.kt:261` `currentOfflineRenderStyleUrl` | `offline.json`, store not read | always reads the store: Trail Map if any region holds it, else `offline.json` (-1294 unchanged) |
| `StreetStyle.kt:67` `regionHasOlderMapStyle`, called from `OfflineRegionsList.kt:171-203` | never shown | shown on every downloaded region whose style isn't Trail Map's (#359's change 5). Wording untouched. |
| `BasemapStyles.kt:251` `ownStreetStyleSourceFor` | `null` | Trail Map → `Uri(STREET_STYLE_URL)`. Topographical at night → `StreetWithTopoOver`. OpenStreetMap → its raster JSON. On fallback → raster. |
| `BasemapStyles.kt:270` `operatingMaxZoom` and `NavigationView.kt:161-169` `navigationMaxZoom`, via `MapNavigationView.kt:46, 59` | OpenStreetMap 19 | **Trail Map 18** (-1289). **OpenStreetMap 19.** Trail Map after the fallback 19, since it then draws OpenStreetMap. Topographical 17. Navigating raises each to at least 18 (`NAVIGATION_VIEW_ZOOM`). |
| `BasemapStyles.kt:305` `mapAttributionFor`, `:317` `mapCreditsFor`, `SightingsMapLayout.kt:139` (caption) | OpenStreetMap's credit | Trail Map → `Protomaps © OpenStreetMap`. OpenStreetMap → `© OpenStreetMap contributors`. Topographical at night → topo's credit plus Trail Map's. |
| `BasemapStyles.kt:376, 393` `AppliedMapStyle.ownStreet`, `requestedMapStyle`, and `MapStyleReload.kt:62-108` | false | stays a reload key, meaning "Trail Map hasn't fallen back" |
| `OwnStreetFallback.kt:36` `ownStreet()` / `effectiveOwnStreet` | `false && …` | `!fellBack`. The rules (what counts, cold-start retry, never offline) don't change. |
| `ui/credits/DataSourceCredits.kt:69-70` `protomapsCreditText` | "Protomaps (offline maps)" | always the on wording, today "Protomaps (Street and offline maps)". "Street" no longer names a map type (see the owner points below). |
| `ui/credits/SoftwareCredits.kt:82-87` with `assets/credits/software.json:333-373` (`onlyWithOwnStreetMap`) | Street font and icons hidden | always listed. Their names say "the Street map's icons" and "Noto Sans, the Street map's labels", which need new wording. |

**Readers outside the dispatch's list.** They don't read the switch, but they assume Street is one thing.

- **`MapMode.forBasemap`** (`MapMode.kt:58`), used at `JournalTab.kt:750`, and **`Basemap.DEFAULT`** (`Basemap.kt:152`, `SightingsMap.kt:150`).
  - Both assume map type and `Basemap` are one to one. Trail Map and OpenStreetMap would both be `Basemap.OSM_STANDARD` unless Trail Map gets its own entry. Today `Basemap.OSM_STANDARD` *is* "Street", whichever way it's drawn.
  - This is a build design point for the planner: a new `Basemap` entry for Trail Map, which isn't a raster and so has no tile template, or passing the map type through instead.
- **The offline-maps picker map** (`AvailabilityOfflineMapsUi.kt:169`) pins `Basemap.OSM_STANDARD`, from the owner's "Always be street." (-1309). Under three types, "street" could mean Trail Map, which is what downloads are, or OpenStreetMap. That's for the owner.
- **`CartographyScreen.kt:102` and `CartographyEntryReportScreen.kt:264`** default to `MapMode.DEFAULT`, so they follow the new default, Trail Map.

**Tests.** #359's 16 test changes (its body, read via `gh pr view 359`) are the starting list. They change again where they named "Street" as one thing. I didn't enumerate the test-side readers line by line.

---

## Points needing the owner's decision

1. **The chip row doesn't fit (item 4).** It needs about 430 dp and the S22 has 352 dp. Wrap, shorten, scroll or list, as described in item 4.
2. **Topographical with no signal:** should it also show Trail Map until the signal returns, as OpenStreetMap does (item 2)?
3. **The offline-maps picker map** ("Always be street", -1309): does it show Trail Map or OpenStreetMap now?
4. **Wording that still says "Street":**
   - the Protomaps credit, "Protomaps (Street and offline maps)";
   - the two software credits, "The Street map's icons…" and "Noto Sans, the Street map's labels".

## Points for the planner (not owner-facing unless you think so)

- **Item 1:** mechanism (a), Trail Map takes `"street"` (recommended), or (b), two new keys plus a logged one-time read.
- **Item 2:**
  - Detection by validated network alone, or validated network plus an OpenStreetMap tile-error count.
  - The hysteresis numbers, 5 s and 30 s, proposed and not measured.
  - Drawing through the offline branch, recommended.
- **Item 5:** how Trail Map enters the model, as a `Basemap` entry or by passing the map type through.

## The four disclosures

**Confirmed (read at 01c7c5b56 or in a pinned artifact):**
- the storage keys and load path;
- the chip's padding, arrangement and label style (material3 1.5.0-alpha26 sources);
- Noto Sans Medium's advances;
- the sheet's padding;
- the permission lines in the MapLibre 13.5.0 and work-runtime 2.12.0 AAR manifests;
- the night underlay logic;
- the fallback's `fallsBack` rule;
- every switch reader listed.

**Inferred:**
- that `ACCESS_NETWORK_STATE` is in the merged manifest (from the library manifests plus the app's having no removal; merged manifest not built here);
- the chip row's width (computed from the font and Material3's layout, not rendered);
- that the third chip would be cut (not rendered).

**Could not determine:**
- whether MapLibre serves the online Trail Map style and tiles from its ambient cache with no signal and no region;
- the real chip row at the S22's own font settings;
- whether `NET_CAPABILITY_VALIDATED` drops promptly on the S22 in aeroplane mode versus a weak signal.

All three need the phone.

**Premises that were wrong or need a note:**
- The dispatch says Trail Map is "what `STREET_SERVED_BY_OWN_MAP = true` draws today". On pre-main the switch is `false` (`StreetStyle.kt:21`). Only #359 sets it `true`. The style is right; "today" is #359's state, not pre-main's.
- The switch has readers the dispatch didn't list: the offline picker, `forBasemap` and `Basemap.DEFAULT`. They're in item 5.

**Decided beyond scope:** nothing. No code was written for Part B.
