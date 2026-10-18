# Elder Ford: top-up audit of the promotion, main `969ecf0a` → pre-main `d942e022` (range `e61e248a..d942e022`)

Filed by the planner as received (Forager RECORD -1308). Elder ran on Fable, read-only.

**Verdict for the whole promotion: Pass with items.** Nothing found that risks a user's records, safety or trust. There are five items for the owner to ship or hold, and none is a block. This was a read-only session: no files written, no PR, no merge, no Gradle, no emulator.

## Baseline, confirmed

- `git ls-remote`: main `969ecf0a`, pre-main `d942e022`, both equal to the fetched heads. The records-after-173 local head is `972c88eb` (RECORD -1307).
- **The range `e61e248a..d942e022`:** 22 commits (two PR merges plus one merge of pre-main into #257), 43 files, +2202/−313.
  - 13 app files, 25 test files, 2 completion reports, the audits index, known-bugs, and `server/pmtiles-worker/wrangler.toml`.
  - `.github/workflows/ci.yml` is not in the range (`git diff --stat` and a direct diff: 0 lines), so the skip allowlist cannot have grown.
- **CI at `d942e022`: push run 39159191337, all three jobs green** (Build/test/publish APK, Android lint, Release compiles and packages). It was in progress when I started and finished at 19:45 UTC. I read the result myself, not the branch runs alone.
- **Tests at the promoted tree, read by me:**
  - `d942e022`'s tree is byte-identical to PR #257's head `006ce58b` (`git rev-parse ^{tree}`: `554c35f2…` both; `712ff277` is an ancestor of `006ce58b`).
  - I downloaded run 39156161080's `unit-test-report` artifact (13.5 MB, 684 XML files) into a fresh directory and summed it with a script: **5,094 tests, 0 failures, 0 errors, 24 skipped.** That matches the planner's reading (-1306, -1307).
  - PR #256's run 39141055078 at `fd9a98ce` is also recorded as success (5,079 / 0 per -1293). I did not re-count it.
- **Tests removed and added in the range:** 3 `@Test` removed and 31 added; no `@Ignore` and no `assume` added (grep of the test diff).
  - **The three removed** are `LandscapeOfflinePickerTest`'s two `L1 … OK, Cancel and Download are shown and take real touches…` and `RECORD -1253 OK, Cancel and Download share one line…`. All three asserted the OK/Cancel the owner removed (-1269).
  - **Rewritten:** two tests in `AvailabilityScreenSettingsPanelTest` (Download Maps greyed until OK → enabled), with the old assertions quoted (Hazel's report; `392846f9`).
  - **Replaced:** `MapUserAgentTest`'s install-at-start test, by two under -1283 (1). It is a rename in the diff, so not counted as removed.
  - **Net zero:** `MapLibreInitializerTest`'s "never falls back" test was added and then removed inside the branch. It is in neither `e61e248a` nor `d942e022`.
- **Read from source:**
  - RECORD -1269, -1270, -1273, -1278, -1283, -1284, -1290, -1293, -1295, -1297, -1303 to -1307;
  - dispatch files 2026-10-18-03 and -04, with Amendments 1 to 3;
  - both completion reports on pre-main;
  - `auditor-profile.md`, `qa-philosophy.md`, CLAUDE.md.

## Mechanism prediction (written before the diff) and what the diff showed

**Predicted for #256:**
- a boolean added to `CentrePinConfirmActions` that only the offline panel passes false;
- Download Maps handing the pin's centre to the "picked" callback before the confirm dialog, with the risk that the tile figures still read the last OK'd point;
- the pill's first-frame size computed from the window by Maps' own sizing, with a line-height fallback;
- the `key` replaced by an "arriving" flag, with the risk of swallowing the Journal's own slide.

**Predicted for #257:**
- `glyphs` deleted from the style JSON;
- the client install moved into the initialiser's success path, so an install failure throws;
- a gate around the map composables catching that into state with Try again. The risks: chrome over the message, copy with no next step after a second failure, and a map left outside the gate;
- `MapMode.DEFAULT = STREET`, with the night crossfade keyed on Topographical directly;
- the credit split between the caption and MapLibre's attribution dialog;
- one line out of `wrangler.toml`.

**Result: every shape held.**
- **The confirm buttons:** `showConfirmButtons: Boolean = true` (`CentrePinLocationPicker.kt:239`), passed `false` at `AvailabilityOfflineMapsUi.kt:193` and `:332`.
- **The tile figures** do read the pin (`pinRegion`, `AvailabilityOfflineMapsUi.kt:178-179`, used at `:240` and `:287`). The risk I named is closed in code but, as Hazel's revert 11 showed, by no test (item 3 below).
- **The pill:** `Modifier.landscapeTabPillFit` (`AvailabilityCompactScaffold.kt:1924-1934`) and `rememberLandscapeStripLineHeight` (`:1946-1956`), with the measured height in `rememberSaveable` for the evening-line case.
- **The key:** `searchHeaderArriving` suppresses enter and exit (`:1092-1098`). The Journal's own slide is held by revert 10 (`6580edd1`'s arrival test).
- **The gate** is `MapStartGate` (`MapStartGate.kt:45-57`), wrapping `SightingsMapSlot` (`MapSlot.kt:561`). The only `MapView(` and `initializeMapLibre` in a composable are in `SightingsMap.kt:325-326` (grep of `app/src/main`). `SightingsMapSlot` is the only producer of that composable (`AvailabilityScreen.kt:776` is the one real default), so no map is outside the gate.
- **The night switch** keys on `basemap == Basemap.OPEN_TOPO_MAP` (`BasemapStyles.kt:139`), not on `DEFAULT`.
- **The credit:** `fullAttribution` goes into each style source (`BasemapStyles.kt:174,181,199`), and `attribution` onto the caption (`SightingsMap.kt:1471`).
- **`wrangler.toml`:** four lines out, one comment in.
- **Two things better than predicted:**
  - the gate composes the real map with no wrapper layout when it starts (`MapStartGate.kt:53-54`);
  - Try again is a real retry, because the initialiser remembers nothing on failure (`MapLibreInitializer.kt:38-44`).

## Not seen on a phone

- **"The map couldn't start." / Try again has never been seen on a phone, at all.**
  - It cannot be produced on the S22 (-1295 step 8: "didn't appear"). It exists only as Robolectric semantics nodes (`MapStartGateTest`, 4 tests).
  - Unverified: its look, its contrast, where it sits under the search pill and icon bar, and whether a finger reaches Try again under either. Hazel lists this as untested; I agree, and add it as item 1.
- **The S26 Ultra:** nothing in the range was seen on it. The owner's rule (-1086) asks for the S22 and S26.
- **The tablet:** nothing in the range was seen on it. The rule asks for the tablet when screens change, and these changed: the offline-maps panel in both orientations, the tabs' landscape pill, and the map's opening basemap on every map screen.
- **A plain pre-main build:** every phone result is from a PR build.
  - -1290: `1.0.3445+g64ce403f`, #256's head.
  - -1295: `1.0.3446+g98d5a880`, an earlier #257 head.
  - -1306: `1.0.3461+gef56fc91`, #257's final head `006ce58b`. Its tree is byte-identical to `d942e022`'s (above), so it is the same code as the promoted tree. This is the one place I can say the plain pre-main build *was* checked, by identity.
  - -1290's and -1295's builds are not the promoted tree. -1306 re-ran only four steps on the final build, and -1295's caption and credit steps were not re-run there (recorded as such in -1306).
- **The S22 results are on the owner's word** (-1290 "All 8 pass", -1295 per-step, -1306 "1 to 4 pass"). The planner saw no per-step evidence, and I have none either. -1290's step 2 (the tile figures following the pin) is the only check that exists for that behaviour.
- **Largest font:** not seen for the offline panel's one-line Download Maps (`maxLines = 1`, `AvailabilityOfflineMapsUi.kt:273`) or for the start-failure message.
- **The font-request check over the network** (Hazel's optional step 7): not done (-1295).
- **Whether `MapLibre.getInstance` can succeed on a retry after a real native-library failure on a phone:** unverified. Hazel says so too.

## Items (owner ships or holds each)

**1. "The map couldn't start." with Try again: honest, but with no step beyond Try again, and never seen on a phone.**

- **The path:** Maps (and every other map screen) → MapLibre fails to start → in the map's place: "The map couldn't start." and a Try again button (`strings.xml:34-35`; `MapStartGate.kt:60-78`).
- **Honest:** it says what happened and offers the one action the code can take, so it meets the honest-copy rule's minimum.
- **Where it stops short:**
  - If Try again fails again, the screen is identical. Nothing tells the user whether to keep tapping, restart the app, or that the map is simply off today.
  - The offline regions they downloaded are not mentioned, though the message replaces the map on the Offline maps screen too (it is in the same slot).
  - Principle: honest, then one step beyond; a state the user could misread as broken.
- **Placement (inferred from the layout code, not observed):**
  - The message is centred in the slot's own bounds (`Box … contentAlignment = Alignment.Center`, `:62`). On the Maps tab that is the whole map area, so in portrait the search pill at the top and the icon bar at a side should clear a centred two-line block. In landscape the rail sits beside the content.
  - The compact map (the smaller map with the compass strip) is the case I could not rule in or out by reading.
  - The surface is solid, which the dispatch allows since it covers no map (`MapStartGate.kt:38`).
  - Touch target: Material3's `Button` carries the 48 dp minimum interactive size by default (inferred from the library, not measured here).
- **Evidence:** `MapStartGateTest` (a real touch on Try again, with the retry counted by log lines), CI green, and revert checks R12 to R14, each failing for its own reason.
- **Why it is listed:** it is not seen on a phone and cannot be, which is why I list it rather than pass it silently.
- **Still a clear gain:** before this change the same failure most likely closed the app (`SightingsMap.kt:322-327` uncaught; R12 shows the throw out of composition), and that path was already live on main.

**2. Street becomes the opening map for everyone who never picked one, with nothing in the app saying so.**

- **Who switches:**
  - A new install, and any existing user with nothing stored, opens Maps on Street after the update (`AvailabilityViewModel.kt:775-776`: nothing stored → `MapMode.DEFAULT`, stores nothing).
  - A stored Topographical is kept (`:772-774` reads the key; the new test "a stored topographic opens on Topographical" holds it).
  - An old Satellite still becomes Topographical (`REPLACEMENT_FOR_UNKNOWN`, `MapMode.kt:55`).
  - The Journal's entry maps and Cartography follow the Maps tab's basemap (Hazel: `JournalTab.kt:754`, `forBasemap`), so the whole app changes together, which is right.
- **What could read as surprising:** a tester who has used Topographical since the first beta without ever opening Layers opens the update to a different-looking map, with no note. One tap in Layers brings Topographical back and stores it.
  - Principle: Don't make me think (a change the user did not make).
  - Not a bug; the owner's ruling (-1297) covers it. I list it so the release note or tester message can say one line about it.
- **Night mode on Street:** Street gets the night raster paint as before (`singleLayerStyleJson(basemap, night)`, `BasemapStyles.kt:191`). The Topo→Street crossfade is unchanged for Topographical users (`:139`); on Street there is nothing to crossfade to, as before.
- **Offline regions are unaffected by the default:** the offline style is loaded "regardless of basemap and night" when `useOfflineTiles` is on (`BasemapStyles.kt:227-239`), so a Street-default user in a downloaded region still sees their offline map. The offline picker's own map stays pinned to OpenTopoMap for terrain context (`AvailabilityOfflineMapsUi.kt:187`).
- **The credit line for Street** is "© OpenStreetMap contributors" on both the caption and the "i" list (`Basemap.kt:142`; `fullAttribution` defaults to `attribution`). That is OpenStreetMap's required form for its standard tiles (background knowledge, not re-read here). The owner saw it on the S22 (-1295 step 5 pass).
- **Evidence:** seen on the S22 after a data clear (-1306 step 1) and the force-stop (step 2). Revert R15 failed five tests, each tied to the default.

**3. Offline-maps screen: item 1 of my previous audit now reads right, but the tile figures following the pin are unguarded by any test.**

- **The path:** Records → Offline maps → pan to place the pin → (name, radius) → Download Maps, enabled on arrival (`enabled = pinIsAPlace && !isDownloading`, `AvailabilityOfflineMapsUi.kt:270`) → "Download this area?", naming the radius and tile count (`:285-287`) → Download.
  - There is no OK and no Cancel, in both orientations (`:193`, `:332`). The other pickers keep theirs (revert 5).
- **What the user reads is what they get:** the "~N tiles" line and the dialog's figure both read the pin (`pinRegion`, `:240`, `:287`). This answers my previous item 1: the button is never greyed with nothing saying why.
- **The gap:**
  - Revert 11 left 74 tests green with both figures reading the last picked point. So the only check that the figures follow the pin is the owner's eye at S22 step 2 (-1290, "All 8 pass", no per-step note).
  - The test is deferred to after the release (-1284; known-bugs row 6).
  - If that reading were wrong, the user would be told one tile count and charged another against the budget. Low likelihood (the code is two lines, read), real consequence, so it stays an item.
- **Observation, not a gate:** the confirmation names the radius and tile count but not the place. The "Pin at: lat, lng" line above the button is the only statement of where (`CentrePinLocationPicker.kt:477`). With OK gone, a user can reach the dialog two taps from opening, on the device's location or the Kansas default (`:473-478`). The owner confirmed exactly this step path (-1269), so I record it rather than raise it.

**4. The known-bugs list for this promotion (A6).**

- `docs/process/known-bugs.md` now carries three new rows:
  - row 4: the pill's evening-line height before Maps is shown (new, from the planner's H2 choice);
  - row 5: the tablet camera's unmeasured ROTATION_0 smoothness (an observation);
  - row 6: the deferred tile-figures test.
- Row 3 (the search summary month after a language switch) stands open; rows 1 and 2 are unchanged.
- Each row says what the user meets and its status, in the list's own format.
- **Not listed.** None of these is a bug in the list's sense, but in my reading the owner should know them before shipping:
  - the start-failure message has never been seen on a phone (item 1);
  - the Street default's one-time change for never-picked users (item 2).
- My previous item 4 (the pill re-created on tab switch) is fixed and, correctly, not listed. Documents only.

**5. The Worker configuration change, and a correction to one claim about it.**

- **The change:** `server/pmtiles-worker/wrangler.toml` drops `PI_ORIGIN_URL` (4 lines out, 1 comment in); `src/` is unchanged.
  - With it unset, `fetchFromPi` returns at once and every tile comes from R2 (`src/index.ts:277-280`, read).
  - The owner has already deleted the variable in the dashboard and deployed, and four fresh tiles answered `x-forager-origin: r2` (-1305, the planner's own request). So the merge to main, if it redeploys the Worker from GitHub, reproduces a state that is already live. That redeploy is **inferred** by the planner from the Part 1 report, not verified by me.
  - CI does not build or test the Worker (no step for `server/` in `ci.yml`; Hazel's verify-first, agreed).
- **Correction:** Hazel's Amendment 3 note says "The app's tiles do not come from this Worker yet." That is true of the basemaps (the OpenStreetMap and OpenTopoMap hosts, `Basemap.kt:129,143`) but not of offline maps.
  - `OFFLINE_STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"` (`OfflineStyle.kt:18`) is what every offline region downloads (`MapLibreOfflineMapRepository.kt:115`), and what the map loads when `useOfflineTiles` is on (`BasemapStyles.kt:239`).
  - So the Worker *is* in a user's path today, for offline downloads and for viewing them.
- **Risk to users, in behaviour terms:** if the redeploy went wrong, new offline downloads and the offline map view would fail until it was fixed. Tiles already on a phone are in MapLibre's offline cache and keep working (inferred from how offline regions work, not tested).
  - The config change itself cannot cause that: it only turns a fallback branch into the only branch, and the live Worker is already in that state.
  - I'd call this low, and worth one check after the merge: download a small region on the S22 and open it with data off.

## Walked as step paths, clean

- **Offline maps, portrait and landscape:** as above.
  - Back leaves the screen as Cancel did (Cancel was a no-op, `AvailabilityOfflineMapsUi.kt:190-191`).
  - In landscape, the pinned bottom is "Pin at", then one full-width Download Maps with standard padding (`:331`). The Name box and slider being in view on open are held by the -1253 test that survived.
  - Seen on the S22 (-1290 steps 1 to 5).
- **Landscape first visit:** turn the phone on List → the pill is Maps' width and the strip's one-line height from the first frame (`landscapeTabPillFit`, `rememberLandscapeStripLineHeight`) → tap Maps → nothing moves.
  - Reverts 6, 7 and 12 each fail for their own reason. `TabsFirstVisitPillTest` covers List and Seasonal; the Journal is covered by the same code path and the owner's step 6.
  - Known gap: an evening line on the strip before Maps is shown (row 4).
- **Search pill across tabs:** type two letters on List, with the cursor between them, and tap Seasonal → the field keeps focus and cursor (revert 9 fails on exactly that message).
  - Arriving on a tab plays no slide; the Journal's reveal still slides (revert 10).
  - Seen on the S22 (-1290 steps 7 and 8). The keyboard observation was not reported.
- **Map credits:**
  - The Topographical caption reads "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)" on one line, and the "i" lists "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)" (S22: -1295 steps 3 and 4 pass; landscape the same, step 6).
  - In Topo night below zoom 9.5 the "i" also lists Street's credit, correctly, since both are drawn (`BasemapStyles.kt:174-181`).
  - The Open Sans credit is removed because nothing draws map text any more (`software.json`). Per Hazel, a grep for `text-field`/`SymbolLayer` in `app/src/main` finds none; I confirmed `glyphs` is gone from both builders. No credit a user is owed is lost.
- **User-Agent:**
  - The client is installed inside the initialiser's success path and never before it (`MapLibreInitializer.kt:40-41`). An install failure fails the initialisation, logged and retried (`:42-44`).
  - The real entry-point test (`MapUserAgentTest`: application start under a failed init installs no client) holds the owner's option (i). Reverts R3, R4, R7 and R8 each failed for their own reason.
  - The wiring line `installHttpClient = { installMapHttpClient() }` (`MapLibreStorage.kt:29`) is confirmed by reading only, as Hazel says.
- **Camera rule, 80% rule, Back, no identification:** untouched by this range. The start-failure surface is solid, by the dispatch's explicit allowance (it covers no map).

## What a user meets in the production build made from main after this promotion (969ecf0a → d942e022), on top of what the previous audit listed for e61e248a

- Maps opens on Street unless they chose Topographical before. One tap in Layers switches, and the choice is remembered.
- Offline maps: no OK or Cancel. Download Maps is live on arrival and downloads where the pin sits, after the "Download this area?" question.
- Landscape: the search pill on List, Seasonal and Journal is the right size from the start, and typing in the pill survives a tab switch.
- If the map ever cannot start, they see "The map couldn't start." with Try again, instead of the app closing.
- Tap-to-reveal attribution ("i") shows OpenTopoMap's full credit; the caption is unchanged. No font requests leave the phone.
- No tile request ever carries the library's own User-Agent (previously a logged fallback).
- Nothing changes for them from the Worker file, which only matches what is already live.

## Could not determine

- Anything on the S26, the tablet or the largest font, and the start-failure message's look and placement on a real screen.
- Whether the Maps tab's chrome clears the centred message on the compact map: inferred from the layout code, not composed.
- Whether a Workers Builds redeploy follows the merge to main: the planner's inference from the Part 1 report, not re-read by me.
- The per-step results behind -1290's "All 8 pass" and -1306's "1 to 4 pass".

## Premises in the dispatch I checked

- "Nothing in this range has been seen on a plain pre-main build": true of -1290 and -1295. -1306's build is PR #257's head, whose tree is byte-identical to `d942e022`, so by identity it is the promoted code, though not a build *of* pre-main. I say this as a distinction, not a disagreement.
- "Merging to main redeploys the Worker from GitHub (inferred)": left as inferred; not verified here.
- Hazel's "the app's tiles do not come from this Worker yet": wrong for offline maps (item 5).

## Beyond scope

None. I wrote nothing, ran no Gradle or emulator, opened no PR and merged nothing. One temporary directory, `/tmp/elder-junit-iMI8`, holds the downloaded JUnit XML (13.5 MB), outside every repository.
