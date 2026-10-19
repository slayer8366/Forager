# Offline maps picker on Street; the MapLibre logo: completion report (dispatch 2026-10-18-07)

Dispatch `prompts/preserved/2026-10-18-07.md` (records-after-173, `a6164b9c`), Forager RECORD -1309. Branch `offline-street-logo`,
PR #259 into pre-main, **not merged**. Written by Hazel (app coder), 2026-10-18.

**Item 1 (the picker always shows Street) is done, CI green at `9d8f49d5` (run 39162834679). Item 2 (the MapLibre logo to the
bottom-right) is not built in this commit: it hit the dispatch's first Stop, "The logo can't go bottom-right without covering
something".** The findings and the options are below. They went to the owner through the planner.

## What landed

Base: pre-main `d942e022`, read from the remote (`git fetch origin pre-main`) before the branch was cut; it matched the dispatch.

| Commit | What it does |
|---|---|
| `76d66f19` | `AvailabilityOfflineMapsUi.kt`: the picker's `CentrePinMap` gets `Basemap.OSM_STANDARD`, named directly (not the Layers choice, not `MapMode.DEFAULT`). The header comment and the panel's doc comment give the reason with the owner's words. Test: `OfflineMapsPanelPickerTest` gains one test; `PanRecordingMapSlot` (test helper) records the basemaps it is handed. |
| `9d8f49d5` | `AvailabilityScreenSettingsPanelTest` (`src/testDebug`): the existing test that pinned the picker to OpenTopoMap is rewritten to the owner's ruling (see "Premises that were wrong"). |
| this commit | This report and its index row. |

Not touched: `SightingsMap.kt`, the offline style, the Worker, the default map, main, dev, RECORD.md, street-cloudflare.

## Verify first

### 1. The picker is the only map hard-wired to Topo

`git grep -n "OPEN_TOPO_MAP" -- app/src/main` at `d942e022`, read hit by hit:

- `AvailabilityOfflineMapsUi.kt:187`: the picker. The only map call that names a basemap.
- `MapMode.kt:37`: the Topographical mode's own basemap (the Layers choice, not a hard-wiring).
- `BasemapStyles.kt:139`: `nightLowZoomBasemap`, Topo's night low-zoom swap to Street. It maps Topo to Street, it doesn't choose Topo.
- `Basemap.kt:120`: the enum entry. `DataSourceCredits.kt:34` and the picker file's lines 16, 124 and 127: comments.

Every other map reads its basemap from a parameter: `JournalTab.kt:519` (the Journal's location picker, `basemap` passed in),
`CartographyEntryReportScreen.kt:564` (`entryMapMode.basemap`), `AvailabilityScreen.kt:1002-1003` (the Maps tab, from the Layers
choice), and `SightingsMap.kt:222`'s default, `Basemap.DEFAULT`, which is `MapMode.DEFAULT.basemap` (Street). **No other map is
hard-wired to Topo.**

### 2. Where the logo sits today, and why

Read from MapLibre Native's source at tag `android-v13.5.0` (the pinned `maplibre = "13.5.0"`, `gradle/libs.versions.toml:43`):

- The app builds its map with `MapView(context)` (`SightingsMap.kt:326`), which uses `MapLibreMapOptions.createFromAttributes(context)`
  (`MapView.java:96-99`), so every default applies.
- Those defaults put the logo at `Gravity.BOTTOM | Gravity.START` with 4 dp margins on every side (`MapLibreMapOptions.java:55`,
  `:264-275`). The logo drawable is 88 x 23 dp (`res/drawable/maplibre_logo_icon.xml`).
- The "i" defaults to bottom-start too, with a 92 dp start margin to sit just past the logo (`MapLibreMapOptions.java:281-291`). It
  is 21 dp (`maplibre_info_icon_default.xml`).
- The app never sets the logo (`git grep -i logo` in `app/src/main` finds no `setLogo*`, `isLogoEnabled` or options call). It moves
  only the "i", to the bottom-end (`SightingsMap.kt:791`, `:1306`). So the logo is at MapLibre's default, 4 dp from the map's
  bottom-left. The caption is drawn at the bottom-left too, at `padding(bottom = bottomInset)` (`SightingsMap.kt:1470-1476`). That
  is why the logo shows under the start of the caption, as the dispatch describes.

### 3. Every attribution placement

`SightingsMap.kt:1297-1310` is the only code that places the "i" after start-up (`:791` sets its first gravity):

| Case | Who | Where the "i" goes |
|---|---|---|
| Default | every map except the case below | bottom-end. End margin MapLibre's 4 dp + `attributionEndInset` (the rail, Maps tab only) and moved inboard of `attributionKeepClear` if they would overlap. Bottom margin 4 dp + `attributionBottomInset ?: bottomInset`. |
| Beside the credit | the compact Maps tab in a short landscape window (`AvailabilityCompactMapUi.kt:1021`, `attributionBesideCredit = landscapeCluster`) | bottom-start, just past the caption's measured end, its bottom on the caption's bottom (RECORD -954). |

`attributionKeepClear` (the landscape L) is set only in that same landscape case (`AvailabilityCompactMapUi.kt:1015`), so in
practice the default case never has one.

Where the logo would go in each, and what it would meet:

- **Beside the credit (landscape Maps tab):** the logo can take the empty bottom-end corner, with the rail inset and the L's
  keep-clear applied to its own 88 x 23 dp. I found nothing there it would cover, except one state that is not verified: while
  navigating, the navigation card is drawn at the bottom-end on the rail side (`AvailabilityCompactMapUi.kt:1726`), and would sit
  over the logo.
- **Default (portrait, the pickers, the Journal entry maps):** the "i" already holds the bottom-end corner, so the logo has to go
  beside it or above it. That is where the Stop is.

## The Stop: why the logo can't go bottom-right without covering something

There are two ways to put the logo in the bottom-end corner with the "i", and each covers something on one of the app's own
screens:

**A. In a row with the "i", just inboard of it** (MapLibre's own logo-then-"i" order, mirrored). It would run from about 29 dp to
117 dp from the map's right edge, at the "i"'s height. That clears the legend chip, whose bottom is 32 dp above the inset
(`LEGEND_ATTRIBUTION_CLEARANCE`, `MapLayersSheet.kt:439`). It meets the caption whenever the caption is wider than the map's width
less 117 dp:

| Caption | Estimated strip width | Fits beside a row on a 384 dp / 412 dp portrait map? |
|---|---|---|
| Street, "© OpenStreetMap contributors" | about 190 dp | yes / yes |
| Topo, "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)" | about 306 dp | **no** (needs 423 dp) / **no** |
| Topo at night (both credits) | about 494 dp, so it wraps to two lines | **no** / **no** |

So on Topo in portrait, on the Maps tab and on a Journal entry's map, the logo would sit on the end of the caption.

**B. Stacked above the "i".** That would put it about 29 to 52 dp above the bottom inset, clear of a one-line caption at any width.
But it lands in the legend chip's slot. The legend sits bottom-right, 32 dp above the inset (`AvailabilityCompactMapUi.kt:1820-1850`,
the owner's ruling "Bottom-right, above the 'i'"). Whenever a colour-field layer is on, the legend would draw over the logo. A
two-line Topo night caption would reach it too.

**A variant of A, lifted above the caption only when the caption reaches it**, would clear the caption and the "i". On the Maps
tab with Topo and a colour-field legend showing, though, it would land under the legend again. To clear that, the legend would
have to rise by the logo's height, and that change is in `AvailabilityCompactMapUi.kt`, which this dispatch doesn't allow me to
touch.

The widths are **estimates, not measurements**. I computed them from the app's own font file
(`res/font/notosans_medium.ttf`, labelSmall: 11 sp, 0.5 sp letter spacing) plus the caption's 6 dp side padding
(`SightingsMap.kt`, `MapCreditCaption`), at font scale 1.0. A larger system font makes every caption wider. The 384 dp is the
S22's portrait width at 450 dpi (from the "i" measurement recorded in `AttributionClearance.kt:12`). The S26's width was not
checked.

### Licence and documentation (the second Stop is not hit)

MapLibre Native's licence at `android-v13.5.0` (`LICENSE.md` and `platform/android/LICENSE.md`) is BSD 2-Clause. It says nothing
about the logo. `UiSettings.java` documents `setLogoEnabled` ("By default, the logo is enabled") and `setLogoGravity` ("Use this to
change the corner of the map view that the MapLibre logo is displayed in") with no requirement attached. Nothing I read requires the
logo, or requires it in one place. I didn't read MapLibre's website or any trademark guidance: unverified.

### A finding outside scope: the "i" and the Topo night caption

By the same estimate, the Topo night caption (about 494 dp) wraps to two lines on a portrait phone. If it does, it spans the map's
width, and the "i" (bottom-end, 4 dp above the inset, 21 dp tall) sits on its second line today. This is not verified on a phone,
and I did not change it.

### Options for the owner

1. **Row beside the "i", and accept the overlap on Topo in portrait.** It's simple and matches MapLibre's own layout. It covers the
   end of the Topo caption.
2. **Row beside the "i", lifted above the caption when they would meet, with the legend raised to clear it.** Nothing is covered.
   It needs `AvailabilityCompactMapUi.kt` (the legend's bottom padding) in scope, and the logo moves up and down as the caption
   changes width (Street vs Topo, day vs night).
3. **Stacked above the "i", with the legend raised above the logo.** It's one fixed place, with the legend moved up for good. It
   needs `AvailabilityCompactMapUi.kt`, and a wrapped Topo night caption still reaches it.
4. **Keep the logo bottom-left and lift it clear of the caption** (above it). That isn't what the owner asked for. It's listed only
   because nothing else moves.

I haven't picked one.

## Tests and evidence (item 1)

- `OfflineMapsPanelPickerTest`, `the picker's map is always Street, by day and by night, before and after a pan`: drives the real
  `OfflineMapsPanel`, pans with a real drag, and switches the panel to night. It asserts that the list of basemaps the map slot was
  handed is exactly `[OSM_STANDARD]`, and that a night frame was composed.
- `AvailabilityScreenSettingsPanelTest`, `the Offline Maps submenu always resolves the picker map to Street, even with the Maps tab on
  Topographical`: through the real `AvailabilityScreen`, Records, then the Offline Maps chip. The Maps tab is set to Topographical,
  and a premise assertion checks that its map really got Topo. So a picker that followed the Layers choice would fail this too, not
  only one pinned to Topo.

### CI on this branch

| Run | Head | What | Result (counts from the JUnit XML in the run's `unit-test-report` artifact) |
|---|---|---|---|
| 39161507817 | `76d66f19` | PR, full | Build/test job failed; lint and release passed. Zero compile-error lines in the log. 684 files, 5,095 tests, 1 failure, 0 errors, 24 skipped. The failure: `AvailabilityScreenSettingsPanelTest` `the Offline Maps submenu always resolves the picker map to OpenTopoMap`, `expected:<OPEN_TOPO_MAP> but was:<OSM_STANDARD>`. That test pinned the old behaviour. It was rewritten in `9d8f49d5`. |
| **39162834679** | **`9d8f49d5`** | **PR, full** | **Green.** All three jobs passed (build/test/APK, lint, release package). Zero compile-error lines in the log. 684 files, **5,095 tests, 0 failures, 0 errors, 24 skipped**. The skipped count matches run 39161507817's; not compared by ID. Both picker tests are in the XML and pass. |

### Revert checks

Each one is on its own branch, with one edit on top of the forward commit named, run by `workflow_dispatch` with a test filter. For
each, I read the log for compile errors first (`e: file`, `Compilation error`, `compile…Kotlin FAILED`), confirmed that
`compileDebugUnitTestKotlin` ran, and then read the JUnit XML. The forward branch was never edited for a revert, and after each check
`AvailabilityOfflineMapsUi.kt` on `offline-street-logo` still named `OSM_STANDARD`.

| Branch | Cut from | The one edit | Run | Result |
|---|---|---|---|---|
| `offline-street-logo-revert-1` (`239c541a`) | `76d66f19` | picker back to `Basemap.OPEN_TOPO_MAP` | 39161505843 | 0 compile-error lines. 5 tests, 1 failure: the new `OfflineMapsPanelPickerTest` test, `expected:<[OSM_STANDARD]> but was:<[OPEN_TOPO_MAP]>`. That is the failure this edit can cause. The other 4 tests in the class passed. |
| `offline-street-logo-revert-2` (`8c489e6d`) | `9d8f49d5` | the same edit | 39162835093 | 0 compile-error lines; `compileDebugUnitTestKotlin` ran. 31 tests, 2 failures, each one this edit can cause: `OfflineMapsPanelPickerTest` `expected:<[OSM_STANDARD]> but was:<[OPEN_TOPO_MAP]>`, and `AvailabilityScreenSettingsPanelTest` `expected:<OSM_STANDARD> but was:<OPEN_TOPO_MAP>` (its final assertion; the Topographical premise above it passed). The other 29 passed. |

There is no revert for "follows the Layers choice". The panel has no basemap input, so that variant can't be made with a one-line
edit. The Topographical premise in the rewritten test is what covers it.

## Premises that were wrong

- **The test list.** My first check of the tests (`git grep OPEN_TOPO_MAP -- app/src/test`) missed `src/testDebug`, where
  `AvailabilityScreenSettingsPanelTest` pinned the picker to OpenTopoMap. The full PR run found it. That test asserted the behaviour
  RECORD -1309 replaces, so it was rewritten to the ruling, with the old name and assertion quoted in it. That isn't a weakening: the
  new version also fails a picker that follows the Layers choice, which the old one couldn't. The previous dispatch's report records
  the same `src/testDebug` miss.
- **The logo.** The dispatch expected the logo could be placed with `setLogoGravity`/`setLogoMargins` so that it, the "i" and the
  caption don't overlap. On the app's own screens it can't, without also moving the legend or accepting an overlap on Topo (above).

## Not verified

- Everything about the logo on a phone. The widths above are font-metric estimates.
- What the picker draws on a phone: Street by day, and Street's night paint at night. The tests read the basemap the map slot is
  handed, and the real MapLibre map can't run under Robolectric.
- That the download is unchanged. No download code was touched (`MapLibreOfflineMapRepository` and the Worker are not in the diff),
  but no download was run.

## Phone steps (S22 or S26), item 1 only

1. Records, then Offline maps, with the Maps tab's Layers set to **Topographical**. Pass: the picker map shows the street map
   (roads and labels, no contour lines or hillshading), and the caption reads "© OpenStreetMap contributors". Evidence: a
   description in words.
2. The same with Night Maps on. Pass: Street's night paint, as on the Maps tab with Street chosen.
3. Go back to the Maps tab. Pass: it is still on Topographical. The picker didn't change the Layers choice.
4. Observation, not a gate: the logo is still bottom-left, under the start of the caption. That's unchanged, because item 2 is
   stopped.

The logo steps in the dispatch (2 and 3) wait for the owner's choice above.

## Update: item 2 built on the owner's option 1 (RECORD -1315)

The sections above stand as they were written when item 2 was stopped. The owner then chose **option 1**, relayed by the planner:
the logo goes in a row beside the "i" at the bottom end, and they accept that it overlaps the end of the long Topo caption in
portrait. The planner added: the logo follows the "i" in each of its placements, including the -954 short-landscape case; the logo
stays visible; the legend is left alone.

### What landed

| Commit | What it does |
|---|---|
| `09a402fe` | `SightingsMap.kt`. The attribution effect (the one that already places the "i") now also calls `uiSettings.setLogoGravity` and `setLogoMargins`, using the new pure function `logoBesideAttribution`. The logo gets the "i"'s gravity and margins, with the button's assumed side plus a 4 dp gap (`ATTRIBUTION_BUTTON_DP` + `LOGO_ATTRIBUTION_GAP_DP`, 28 dp) added on the side away from the map's edge. The two share a bottom edge. The logo stays enabled. The legend and the "i"'s own placement are unchanged. New test: `LogoBesideAttributionTest`. |

Where the logo now goes:

| The "i"'s placement | Where the logo goes |
|---|---|
| Bottom end: every map, in portrait and in landscape, except the case below | Bottom end, just inboard (left) of the "i", on the same bottom line. It follows the "i" through the rail inset and the bottom inset. |
| Bottom start beside the credit strip (RECORD -954: the compact Maps tab in a short landscape window) | Bottom start, just past the "i": credit strip, then "i", then logo. **This is not at the right.** It follows the "i", as the planner asked. The owner's phone check should confirm this is what they wanted for landscape. |

What it meets, from the arithmetic above (estimates, not measured):

- Portrait, Topo: the end of the caption, as the owner accepted. Street fits.
- Short-landscape Maps tab: the logo is 88 dp past the "i". With the Topo caption (about 306 dp), the logo would end about 430 dp
  from the left. The landscape navigation card at ROTATION_90 starts at 385.33 dp in the S22 window (`AttributionBesideCreditTest`'s
  doc, from `LandscapeCardMeetsBar823Test`), so while navigating, that card would draw over the logo's end. With Street (about
  190 dp) it ends near 314 dp, clear of the card. Not verified on a phone.
- RTL: margins mirror the convention the existing "i" helpers use. Not checked on a phone.

### Evidence

A real MapView cannot be built under Robolectric (as the existing "i" tests record), so **the logo is not measured**. What is
asserted are the settings `SightingsMap` hands MapLibre. They are laid out the way a FrameLayout lays out a gravity and margins,
with MapLibre 13.5.0's own sizes: the logo 88 x 23 dp, the "i" 21 dp. `LogoBesideAttributionTest` holds:

- at the bottom end: the logo shares the "i"'s gravity, its margins differ only on the end side by the offset, it doesn't overlap
  the "i", and it ends 7 dp short of the "i" (the 24 dp assumed square plus the 4 dp gap, less the 21 dp real button), on one bottom
  line;
- with the rail inset, the logo moves inboard with the "i";
- right-to-left at the bottom end: the offset is on the same index as the "i"'s end inset;
- beside the credit strip (-954): the gravity is bottom-start, the logo is past the "i", and it is clear of both the "i" and a
  306 dp strip, on one bottom line; right-to-left too;
- the "i"'s own margins are not changed by placing the logo.

What no test covers: that `SightingsMap` actually calls `setLogoGravity`/`setLogoMargins` with these values. The call site is in the
`LaunchedEffect` beside the "i"'s two calls, and it can only be checked on a phone.

### CI and revert checks for item 2

For each run I checked: zero compile-error lines in the log, `compileDebugUnitTestKotlin` ran, and the counts come from the JUnit
XML. Each run was filtered to `LogoBesideAttributionTest`, `AttributionBesideCreditTest` and `AttributionClearanceTest`. After both
reverts, `SightingsMap.kt` on `offline-street-logo` still had the forward lines.

| Branch | The one edit, on `09a402fe` | Run | Result |
|---|---|---|---|
| `offline-street-logo-logo-check` (`09a402fe`, the forward commit pushed under its own name while the PR run was in progress) | none | 39163951254 | 15 tests, 0 failures. |
| `offline-street-logo-revert-3` (`40970d2e`) | `margins[side] += offsetPx` becomes `+= 0`, so the logo would sit on the "i" | 39163954023 | 15 tests, 5 failures: every `LogoBesideAttributionTest` case that places the logo, each off by exactly the 84 px offset (for example `expected:<96> but was:<12>` at index 2). The test that the "i"'s margins are unchanged passed, as it should. |
| `offline-street-logo-revert-4` (`d941a18f`) | the bottom-end side index in left-to-right becomes 0, so the logo would go to the wrong side | 39163956446 | 15 tests, 2 failures: the two left-to-right bottom-end tests (`expected:<276> but was:<360>` at index 0). The right-to-left and beside-credit tests passed, as they should for this edit. |

The report commit `42690d7b` ran green as well (run 39163912646: 684 files, 5,095 tests, 0 failures, 24 skipped). The full PR run at the item 2 head is reported to the planner. This report can't contain its own commit's run.

### Phone steps for item 2 (S22 or S26)

1. Maps tab, portrait, Street. Pass: the MapLibre logo is at the bottom right, just left of the "i", on the same line, touching
   neither the "i" nor the caption. Evidence: a description in words.
2. The same with Topo. Pass: the logo is beside the "i" as in step 1. Expected and accepted: it overlaps the end of the caption.
   Record how much of the caption it covers.
3. A Journal entry's map and the Offline maps picker, portrait. Pass: as step 1. The picker is always Street now.
4. Maps tab, short landscape, Street then Topo. Pass: credit strip, then the "i", then the logo, at the bottom left, on one line,
   none touching. Tell the owner this is where the logo goes in landscape (it follows the "i", not the right edge).
5. The same at the other rotation (rail on the other edge). Pass: as step 4.
6. Landscape while navigating, Topo, at ROTATION_90. Observation, not a gate: whether the navigation card covers the logo's end.
7. Tap the "i" in each of the above. Pass: it opens the attribution dialog, as before.
