# S22: Re-center at a larger font, and known bug 14 on a phone

Dispatch `prompts/preserved/2026-11-02-01-s22-recenter-and-bug-14.md` (RECORD -1943, -1944), origin/records-after-173 a263e9b7e. RECORD -1949 (501c88d61) handed C1 to a new session after the first one was stopped partway through, around 23:46 PST on 11-01, and could not be resumed. Phone-only build; this branch is never merged. Evidence is in `/home/zynergy-labs/s22-2026-11-02/`, off git. 3.75 px per dp.

**Provenance.** A1 and B1 were run by the first session. Their sections below say, line by line, what this session **re-read** from that session's evidence files and git, and what it only **relays** from the first session's messages as quoted in the hand-off. C1 (Part 3) was run end to end by this session, 03:23 to 03:48 PST on 11-02, with evidence in `c1b/`.

## A1 (Part 1): build and install

- **Merges (re-read from git).** One merge on tools/s22-combined since aef6f01ac: 070b2bb1a, "Merge origin/route-plot-ui e3a500ebf". One conflict, `StreetStyle.kt`: the branch's own Street v4 raw-file line was dropped in favour of pre-main's Worker URL (#462), per the merge message. There is no separate pre-main merge commit: pre-main 2bc8913f4 and 6522e5d1e both reach 070b2bb1a through route-plot-ui's own merge f2743db46 (`git merge-base --is-ancestor`). origin/pre-main has since moved to faa4c1bd2, 6 commits ahead, and is not in this build.
- **CI run 41403692014 (re-read from `ci/`).** 0 compile-error lines in `ci/run.log`; "Build the debug APK" BUILD SUCCESSFUL; "Run the unit tests" BUILD FAILED. From the JUnit XML: 6331 testcases, 24 skipped, 6307 run, **2 failures**, 0 errors. Both are in `RouteCardRecenterLandscapeTest`:
  - "Re-center stays put for the card in the other column": "precondition: the card 425.33334.dp..835.0.dp is in the other column from Re-center 363.33334.dp..471.66666…";
  - "shown with the card already up, Re-center sits where it does with no card": expected Re-center at 363.3–471.7 dp × 332–380 dp, got a different rect (message truncated in the XML).
  - **Relayed, not confirmed:** the first session attributed both to the "Alt unavailable" strip widening the card at 915×412 dp. It did this by reading the code, not by a run, and did not confirm it on the phone. This session did not re-derive it. What the messages show is narrower: at that size the card's left edge (425.3 dp) falls inside Re-center's span (363.3–471.7 dp).
  - The three Street v4 failures earlier builds allowed do not appear in this run.
- **APK (re-read).** `ci/apk/app-debug.apk` sha256 3c9c1094…6fd19. Installed on the phone: versionName 1.0.4398+g070b2bb1, versionCode 4398 (read from `dumpsys package` this session). Schema: the build's `ForagerDatabase` and the phone are both at user_version 23 (phone read from the snapshots).
- **Data across the install (re-read).** `pre-install/` against `after-install/` table hashes: every forager.db and fungi_index.db table equal. mbgl-offline.db `resources` (301 to 302), `tiles` (same count, content) and `sqlite_sequence` moved: map cache from viewing.
- **In-app backup (re-read).** `in-app-backup/forager-backup-2026-11-01-a3.zip`, sha256 f3a182ed…fafb, off the phone. Not restore-tested.
- **Relayed only:** signing equal, versionCode above the previously installed one, `install -r`, logger pref on. This session did not re-check them.

## B1 (Part 2): Re-center at a larger font

The question was at which font scale, if any, Re-center and the route card share space in landscape while navigating.

- **Relayed:** at font 1.0, Re-center is clear of the search bar (Re-center x 1145–1562 px, card 1563–2608 px), so the -1936 finding is gone at 1.0. From 1.3 the two share space, and Re-center rises under the search bar when the card is expanded. At 2.0 Re-center sits under the strip. Font scale and rotation were restored by reading them back, and the TEST waypoints were deleted.
- **Re-read (UI dumps `ui/f10e4.xml`, `f13e3.xml`, `f20e3.xml`).** These agree with the relayed account:
  - 1.0, 4 stops expanded: the "Re-center" label is at x 1273–1518, y 1193–1268. The "4 Waypoints routed" title starts at x 1608. The search bar ends at x 1196.
  - 1.3, 3 stops expanded: "Re-center" is at x 1233–1559, **y 113–131**, so it has risen to the top edge. That is the search bar's row (y 122–236; the bar ends at x 1210), and partly under the strip (x 1361–2451). The card title starts at x 1345, inside Re-center's x span. The dump alone does not settle whether "under the search bar" or "under the strip" is the better word at 1.3.
  - 2.0, 3 stops expanded: "Re-center" is at x 1113–1567, y 113–139, under the strip (x 838–2340, y 113–253). The card title starts at x 822.
  - The Re-center x 1145–1562 for the whole button at 1.0 is the first session's figure; the dump gives the label's span only.
- **Caveat (relayed):** the strip read "Approximate location, finding GPS…" indoors, so the card's width at each scale came from that status text, not from #440's alt line. A walk with a fix would widen or narrow the card differently.
- **Verdict (from the relayed figures and the re-read dumps): they share space from 1.3, and do not at 1.0.** No fix proposed, as the item asks.

## C1 (Part 3): known bug 14, proven or not

**Verdict: bug 14 seen as written.** A download south of 15.92°N completed with no error and listed a 5.9 MB region. Every tile it stored is empty. Offline, the area draws at zoom 8 (from the ambient cache, not the region) and is blank at zoom 10 and 13. The Street v4 relief 404 was present in the download and did not end it, so the result belongs to bug 14, not to the confound.

### The phone before anything was touched
- Doze on the lock screen; focused app Forager MainActivity. No Forager services running, no Forager notifications: no recording, no walk logger (`dumpsys activity services`, `dumpsys notification`). Airplane off, font 1.0, auto-rotate on, versionCode 4398. This agrees with the planner's read.
- **No TEST area or partial download existed.** A snapshot (`c1b/now-pre/`) against `before-area/` (taken by the first session at 23:43): `regions` 2, `region_tiles` 1664, `region_resources` 594, every forager.db and fungi_index.db table, DataStore and every other file equal. Only the mbgl ambient cache had moved: 38 tiles and 8 resources had `accessed` touched, and 3 tiles had `expires` refreshed with byte-identical data. All of this was between 23:43:45 and 23:45:28 PST, during the first session's setup. Nothing was deleted at this step.
- The screen was the Offline Maps picker in the Journal's Records, with the pin near the earlier test spot, as `shots/o9.png` showed.

### The area
- **TEST: centre 14.7°N, 91.5°W, western Guatemala** (the picker read 14.7174, -91.4969). Radius 1 mi, the slider's minimum. The download's bounds are 14.708–14.726°N × 91.506–91.488°W, about 2.0 × 2.0 km. The picker estimated "~19 tiles". The confirmation read "TEST · 1 mi around the pin · about 19 tiles".
- **How the pin got there.** The picker is pan-only, and `input`'s single-pointer commands could not drive MapLibre's quick-zoom (each `input` is its own process). `sendevent` is denied by SELinux. So a two-finger pinch was injected the way `input` itself injects: a 3 KB dex run with `app_process` as shell, calling `InputManagerGlobal.injectInputEvent` (source in `c1b/tools/pinch-src/`, deleted from the phone afterwards). Pinches register reliably only at ratios of about 4× or more.

### The Worker, read directly (curl, one named user agent)
- At the pin: `us` answers 200 with data at z6, z7 and z8, and **204 (empty) at every zoom from 9 to 15**. `reference-trails` answers 404 to z9 and 204 from z10. `relief` answers **404 at every zoom 6–15**.
- Relief also answers 404 at a tile in Oregon (z11), so the relief confound applies to any v4 download today, not only south of 15.92°N.
- The live v4 style's `dem` source is `/relief/{z}/{x}/{y}.png`, z5–11 (`c1b/worker/style-v4.json`).

### What the app said at the end
- The download took about 1 s (03:32:16 to 03:32:17 PST).
- **No error and no failure line.** `MapLibreOfflineMapRepo`'s "Offline region download failed" does not appear in the log. On success the screen shows no message by design (`OfflineMapsSideBySide.kt:149`: `Succeeded -> Unit`); the new row is the only signal.
- The row: "**TEST · 1 mi radius · 5.9 MB · downloaded 2 minutes ago · Map only. No trail routes for this area yet.**" The tile budget went from 832 to 851 / 6000, and Records "All" from 18 to 19.
- The route step answered 404: "No routes for region 10: the Worker says the area is outside coverage" (`RegionRoutes`).
- The region's details sheet reads "Tiles 19 · Size 5.9 MB" and "**Ready to zoom 15: zoom 10–14 from the archive, zoom 15 detail fetched live from Protomaps when this region downloaded — a region that shows here has both, since a zoom-15 fetch failure fails the whole download rather than silently completing without it.**" That is a positive claim about a region that holds no tile data, and its "fetched live from Protomaps" no longer describes the Worker. Recorded, not acted on.

### What the region holds, by zoom (from `mbgl-offline.db` with TEST present, region id 10, style street-v4)

| Source | Zoom | Tiles | With data |
|---|---|---|---|
| us (basemap) | 10, 11, 12, 13, 14, 15 | 1, 1, 2, 2, 4, 9 (19) | 0 |
| reference-trails | 10 to 15 | 1, 1, 2, 2, 4, 9 (19) | 0 |
| relief | 11 | 1 | 0 |
| zoom 8 and below | none | 0 | 0 |

- 0 bytes of tile data in all 39 rows.
- The 5.9 MB is resources, not tiles: 297 of them, 5,922,549 bytes. 291 are glyph ranges, plus the style, sprites and source JSON.
- The relief row (11/503/939) has null data, no etag and an `expires` about a day out. The download window has exactly one MapLibre HTTP 404, plus 38 responses of 204 and 2 of 200. **MapLibre stored the relief 404 as no content and carried on.** That the one 404 was this relief tile is inferred: the 404 log line names no URL, but no other relief tile was requested.
- Relief z10 was not among the region's tiles; only z11 was. Why (raster-dem tile size against the region's zoom range) was not looked into.

### Which cause the result belongs to
**Bug 14.** The basemap tiles are empty because the Worker answers 204 for z10–15 there, exactly as the bug row says. The relief 404 is present but did not fail the download, and it would leave the region empty of relief in Oregon too. This also answers a question the pre-main sort left open (b39f3e496, "whether a v4 download completes with relief at 404 is not established"): **on this phone, it completes, with relief stored as empty.** `downloadErrorKind`'s comment ("404 included, fails it", `MapLibreOfflineMapRepository.kt:443-452`) is accurate about the observer's error path. A tile 404 just never reaches that path in this download.

### Open offline
The Maps tab, centred on TEST's circle, with airplane mode on (read back: `airplane_mode_on` 1, "enabled", no default network). Zoom was measured from the TEST circle's diameter in px (d ≈ 0.159 × 2^z at this latitude).

| Zoom (measured) | What the map shows | Shot |
|---|---|---|
| about 8 (circle about 40 px) | Full detail: roads, town names (Quetzaltenango, Mazatenango, Santiago Atitlán), Lake Atitlán, protected-area outlines, coast | `c1b/offline-z8.png` |
| 10.25 (193 px) | Flat grey background; only the TEST circle | `c1b/offline-z10.png` |
| 12.95 (1258 px) | Flat grey background; only the TEST circle | `c1b/offline-z13.png` |

Side by side: `c1b/offline-8-10-13.png`.
- The z8 view is drawn from MapLibre's ambient cache (z8 tiles fetched while panning there online), not from the region, which holds nothing below z10. A user who had never viewed the area online at z8 would see whatever their cache holds there. That case was not tested.
- Online, before airplane mode, the same place at about z9 already looked like the bug: where z9 tiles had loaded empty, the sea rendered as grey background beside z8 water (`c1b/live-online-z8ish.png`).
- Airplane mode off, read back: 0, "disabled", the network returned.

### Delete, and the data against the before state
- **Deleted from the row's trash icon.** There is no confirmation, only a snackbar, "Offline map deleted · Undo", left to expire. Budget back to 832 / 6000, TEST gone from the list, `files/offline_routes/10/` removed.
- **Snapshot `c1b/after-delete/` against `before-area/`:**
  - **forager.db: every table equal** (offline_regions 2, same hash). The `-wal` and `-shm` bytes differ while the tables are equal.
  - **fungi_index.db: every table equal.**
  - **mbgl-offline.db:** `regions`, `region_tiles` and `region_resources` **equal**. Three tables differ, each for a named reason:
    - `tiles`: 1841 to 1994. +153 ambient cache from viewing (us z0–15, 34 with data; relief z5–11, 75 rows, all empty; trails 21). Among them are all 39 of TEST's former rows, which MapLibre keeps as cache after a region is deleted. No row was removed.
    - `resources`: 302 to 304: sprites/v6 light.json and light.png.
    - `sqlite_sequence`: the region id counter went from 9 to 10, and the tile and resource counters moved.
  - **DataStore `map_preferences`:** only `offline_map.last_picked_lat`/`_lng` changed. **The picker's remembered pin now points at TEST's centre (14.7174, -91.4969).** Before C1 it pointed at an earlier test spot in Oregon (coordinates kept out of git). It was not reset by hand.
  - Every other file is byte-equal.

## Not run, not verified
- The A1 and B1 items listed above as relayed.
- B1 was not re-run on the phone.
- The cause of A1's two `RouteCardRecenterLandscapeTest` failures.
- A z8 view of an area never seen online.
- Why relief z10 was not in the region.
- Whether a region whose relief was stored empty refetches it after `expires`.
- Restore of the in-app backup.

## Slips and calls beyond the dispatch
- `tapt "Maps"` matched "Download Maps" by substring and opened "Download this area?". Cancel was tapped, and the budget and region count confirm nothing downloaded.
- Twice, a gesture put text in the picker's Name field ("ff6 R5" from the keyboard's swipe-typing, then a space). Both were cleared before any download, and the field read empty after. Neither was submitted.
- The pinch injector (above) is a new tool, not used before on this phone. It needs no permission beyond shell's own.
- `/data/local/tmp/hz-clock-watchdog.sh` is on the phone from an earlier session. It was left alone.

## Phone end state
Forager on the Maps tab, centred on the phone's own location. Airplane mode off, font scale 1.0, auto-rotate on; no Forager service running. Regions: Region 1 and Region 2 only, as before. No TEST data remains outside the ambient map cache and the remembered pin.

## D12: the route card beside the navigation card, landscape, on the S22 (dispatch 2026-10-30-08, C18 and D12; RECORD -1953)

Run by Hazel, 04:50 to 05:21 PST on 11-02. Evidence is in `/home/zynergy-labs/s22-2026-11-02/d12/` (shots, UI dumps, snapshots, CI artefacts), off git. 3.75 px per dp. Re-center follows D10's rule unchanged, and nothing was fixed.

### Build and install
- **Merges.** This branch is 61ae72887: origin/route-plot-ui a04b55048 (C16 to C20) merged into 213769ffa. origin/pre-main 13c872d79 was already included (`merge-base --is-ancestor`), so its merge was a no-op. No conflicts.
- **CI run 41447864352** (workflow_dispatch, head 61ae72887): every job green. 0 compile-error lines in the log. From the JUnit XML: 902 files, 6340 tests, 24 skipped, 0 failures, 0 errors. Nothing needed attributing.
- **APK:** sha256 52e56664…28f7a7, versionCode 4410 (installed was 4398), signing certificate equal to the installed one (cb2f6da5…1626). The routing key is set in the APK step (masked in the log) and was never printed. Schema: this build's ForagerDatabase is 23, and the phone is at user_version 23.
- **Phone before anything:** dozing on the lock screen, versionCode 4398. No Forager service and no Forager notification, so no recording and no walk logger. Font 1.0, auto-rotate on, user_rotation 0.
- **In-app backup:** `forager-backup-2026-11-02.zip` (a new name, older files untouched), sha256 ad81228a…b6e4, manifest 4398 and schema 23, pulled to `d12/in-app-backup/`. Not restore-tested.
- **Across `install -r`:** every forager.db and fungi_index.db table hash is equal (`pre-install-2` against `after-install`). mbgl-offline.db `resources` and `tiles` changed with the same counts: map cache from viewing. Among the other files, only ART's `profileInstalled` changed.

### Setup
- TEST A to F were dropped as waypoints. Navigation to TEST A was started from its bubble. The map was moved off the navigation view, so Re-center showed, using the earlier two-finger pinch injector (`c1b/tools/pinch-src/`, pushed as `hz-pinch.dex`, deleted after). Plotting went E, D, F, A, B, C. At ROTATION_270, Undo took the plot from 6 stops to 3 and then 1.
- Landscape came from `user_rotation` 1 and 3 with auto-rotate off. Both were restored afterwards and read back.

### What the pictures show (`d12/shots/`, contact sheet `d12-contact-sheet.png`)
The card sits left of the navigation card and touches it, as C18 rules. At ROTATION_270 the whole layout is 80 px to the right, behind the system bar on the left. Otherwise it matches ROTATION_90.

| Stops | State | Shot (90 / 270) | Re-center | Walker's dot | Credit line |
|---|---|---|---|---|---|
| 1 | collapsed | r90-1p / r270-1p | clear, raised above the card (label y 653–728) | clear | its text clear; drawn over the card's bottom 76 px |
| 1 | expanded | r90-1e / r270-1e | clear, as collapsed (one stop has no rows, so the two states look alike) | clear | as above |
| 3 | collapsed | r90-3p / r270-3p | clear (label y 653–728) | clear | as above |
| 3 | expanded | r90-3e / r270-3e | clear, raised to y 323–398, below the search bar (bottom about 271) | clear | as above |
| 6 | collapsed | r90-6p / r270-6p | clear (label y 653–728) | clear | as above |
| 6 | expanded | r90-6e / r270-6e | **covered**: raised to y 113–131, under the search bar, and not visible | clear | as above |

- **Walker's dot:** with the map moved off the navigation view, the dot is the phone's position on the moved map, not the navigation view's fixed dot. It was clear in every shot, for this pan only.
- **Credit line:** the credit strip (x 0–1638, y 1364–1440 at ROTATION_90) is drawn over the card. Its text stays readable, but it overlaps the button row: Undo by 9 px and Save by 46 px. The credit rises only for a card in the other bottom corner (`routeCardOverCredit`: landscape with the L on the right). Navigating, the L is fixed on the left, so the credit stays down here. The D9 rule ("the 'i' and other chrome keep clear of the card") is not met in this spot. This is recorded, not fixed.

### For the owner and planner: the card is cramped in this spot at font 1.0
- The room between the L's column and the navigation card is about **205 dp** (card x 520–1287 px). C19's reference card is 247.67 dp, so the card takes the room, as C19's cap says.
- At that width "N Waypoints routed" wraps onto two lines, and the **Save button's label breaks mid-word, "Sav" / "e"**, with the "e" under the credit line. This is the stop the dispatch names: a width that can't serve the reference card within the room. The 205 dp was measured on the phone at font 1.0. Before this run I had inferred about 278 dp at 1.0 from this audit's earlier figures, and that inference was wrong: the navigation card is wider here than those figures implied.
- No change was made. Options for the owner, not chosen: let the card cover the L's column; put the card above the navigation card when the room is short (C11's arrangement); shrink the buttons or let them wrap whole words; or another arrangement.

### Data after the check
- Deleted: the plot (Cancel), navigation (stopped), and TEST A to F, each swiped in Journal > Records > All after reading its name (24 to 18).
- `after-delete` against `pre-install`: **every forager.db and fungi_index.db table equal.** mbgl-offline.db `regions`, `region_tiles` and `region_resources` are equal. `tiles` went from 1994 to 2007, and `resources` and `sqlite_sequence` changed: ambient cache from viewing. The DataStore and other non-database files are equal to `after-install`.

### Phone end state
Forager on the Maps tab: not navigating, no plot, no service running. Font 1.0, auto-rotate on, user_rotation 0. `/data/local/tmp/hz-clock-watchdog.sh` and `pinch.sh` are as found; `hz-pinch.dex` is removed.

### Not verified
- Fonts above 1.0 on the phone, and the L on the rail's side (C20) on the phone. Not navigating was not photographed.
- Whether one stop was actually expanded: one stop shows the same either way.
- The walker's dot in the navigation view itself, because Re-center needs the view left.

## D15: the route card on the navigation card (C21 with C23), the credit (D13), C20, and portrait's credit (D16) on the S22 (dispatch 2026-10-30-08; RECORD -1955, -1956)

Run by Hazel, 10:16 to 10:55 PST on 11-02. The evidence is in `/home/zynergy-labs/s22-2026-11-02/d15/`, off git: `shots/`, `ui/`, snapshots, CI artefacts and scripts. The scale is 3.75 px per dp. Re-center keeps D10's rule unchanged. Nothing about Re-center or the portrait credit was changed; the owner decides from these pictures.

### Build and install
- **Merge.** This branch is 687d517da: origin/route-plot-ui 68ea07b82 (C21 with C23, D13) merged into 2312b363e with no conflicts. origin/pre-main 13c872d79 was already included (`merge-base --is-ancestor`).
- **CI run 41498709875** (workflow_dispatch, head 687d517da): every job green except "Build, test, publish APK". From the JUnit XML: 909 files, 6357 tests, 24 skipped, 1 failure, 0 errors. The APK was still published.
- **The one failure, attributed by the runs' own output:** `RouteCardRecenterLandscapeTest`, "Re-center stays put for the card in the other column". On this branch the card is 425.3..835 dp at 915 x 412, and its precondition fails because the card reaches Re-center (363.3..471.7).
  - The card follows the navigation card (C21), and on this branch that card is wider. `RouteCardLandscapeBarTest` prints it as 333.3..743 dp at 823 x 384 here, against 400..743 in route-plot-ui's own CI (41495921575, where the same test passes).
  - This branch's tools-only navigation HUD changes (the altitude work) widen the strip. So the failure belongs to the merge, not to route-plot-ui. It was not silenced.
- **APK:** sha256 6a067287…1598, versionCode 4415 (4410 was installed). Its signing certificate equals the installed one (cb2f6da5…1626). Schema: this build's ForagerDatabase is 23, and the phone is at user_version 23.
- **The phone before anything:** dozing, versionCode 4410. There was no Forager service and no Forager notification, so no recording and no walk logger was running. Font 1.0, auto-rotate on, user_rotation 0. The forager.db and fungi_index.db tables were equal to D12's `after-delete`.
- **In-app backup:** `forager-backup-2026-11-02-d15.zip`, a new name with older files untouched. sha256 c8056754…c321, manifest 4410 and schema 23. Pulled to `d15/in-app-backup/`. Not restore-tested.
- **Across `install -r`:** every table hash and every file hash is equal (`pre-install-2` against `after-install`).

### Setup
- TEST A to F were dropped as waypoints (+ > Waypoint > OK > name > Drop). Navigation to TEST A started, but A was at the walker and read "Arrived" at once. So it was stopped, and navigation to TEST C (0.9 mi) was started from its bubble instead.
- A pan took the map off the navigation view, so Re-center showed. The pinch injector was used for zoom only; it was pushed as `hz-pinch.dex` and deleted after.
- Plotting went E, B, F, D, C, A. Overlapping pins were reached through their fans.
- One slip: a tap meant for the handle at one stop landed on the map and added "Waypoint 1", the owner's record, as a stop. Undo removed it before any shot. Nothing was saved.
- At ROTATION_270, Undo took the plot from 6 stops to 3, then to 1.
- Landscape came from user_rotation 1 and 3 with auto-rotate off. Portrait was user_rotation 0, at font 1.0 and then 2.0.

### What the pictures show: landscape, navigating, font 1.0 (`d15/shots/r90-*`, `r270-*`)
The card rests on the navigation card at its width (x 1290 to 2604 px at ROTATION_90, about 350 dp), edge to edge, with Save above that card's top. At ROTATION_270 the layout is 80 px to the right. No line wraps, and Save is whole.

| Stops | State | Shot (90 / 270) | Re-center | Walker's dot | Credit line |
|---|---|---|---|---|---|
| 1 | collapsed | r90-1p / r270-1x | clear, raised above the card (y 334–409 px) | clear, just above the card's top | clear: down at y 1364–1440, below the card |
| 1 | expanded | r90-1e / r270-1t | clear, as collapsed (one stop has no rows, so the two states look alike) | clear | clear |
| 3 | collapsed | r90-3p / r270-3p | clear (y 349–424) | clear | clear |
| 3 | expanded | r90-3e / r270-3e | **covered**: raised to y 114–147, mostly under the search bar | clear | clear |
| 6 | collapsed | r90-6p / r270-6p | clear (y 409–484) | clear | clear |
| 6 | expanded | r90-6e / r270-6e | **covered**: raised to y 113–131, under the search bar, not visible | clear | clear |

- **D13:** the credit no longer touches the route card while navigating. D12's 9 px over Undo and 46 px over Save are gone, and the credit stays down because the card is above it.
- The credit's right end still runs under the navigation card (x 1290 to 1638), as it did before route plotting. That is the navigation card, not the route card, and was not changed.
- **Re-center:** on the navigation card the route card is in Re-center's column, so D10's rule raises it. At 3 and 6 stops expanded that puts Re-center under the search bar. D12 saw this only at 6 stops expanded; now it is 3 and 6.
- Also seen before plotting (`n3`): Re-center (x 1273–1518) overlaps the navigation card's left edge (x 1290). This was not caused by this change and is recorded only.

### C20: not navigating, the L dragged to the rail side (`c20-left-3p`, `c20-right-3p`, `c20-right-3e`)
- **The L on the left (before the drag):** the card is in the navigation card's spot (x about 1690–2604 px). The credit, x 0–1638, is clear and stays down.
- **The L dragged right:** the card takes the other bottom corner, as C20 rules. Its left edge is about 100 px in from the screen's edge at ROTATION_90; Robolectric has it at 0, and the cause was not checked.
  - The credit rises above the card: y 929–1005 collapsed, 599–675 expanded. It is clear of the card in both.
  - Walker's dot: clear collapsed, and just above the risen credit when expanded.
  - Re-center: not shown, because the phone was not navigating.
- The L was dragged back. It sits where it was (Fullscreen at [130,249][310,429]), and the DataStore files are equal to before.

### Portrait, plotting 3 stops, not navigating (D16; `p10-3p`, `p10-3e`, `p20-3p`, `p20-3e`)
- **Font 1.0:** the card's bottom edge lies over the credit strip's top by about 15 px (y 2472 to 2486, about 4 dp), collapsed and expanded. Robolectric gave 4 dp. The credit's text is clear. In the strict sense of D16's "meet", the rectangles overlap.
- **Font 2.0:** the card covers the credit from y 2140 to about 2405 px (about 71 dp; Robolectric gave 68). The credit's first line and a half lie under the card's button row and can be read only through the card's 80% fill: **covered**.
  - Walker's dot: clear collapsed; seen through the card when expanded.
  - The card's "3 Waypoints routed" wraps onto two lines, whole words, as C13 rules.
- Re-center: not shown in portrait, because the phone was not navigating.

### For the owner (not changed, by the dispatch's rule)
- **Re-center in landscape:** with the card on the navigation card it is covered (under the search bar) at 3 and 6 stops expanded.
- **Portrait credit:** it is on the card at both sizes, by 4 dp at 1.0 (its text clear) and 71 dp at 2.0 (its text under the buttons).
- **Seen at font 2.0, outside this dispatch:** the bottom navigation's labels break inside words ("Seas" / "onal", "Journ" / "al").

### Data after the check
- **Deleted:** the plot (Cancel), navigation (stopped), and TEST A to F, each swiped in Journal > Records > All after its name was read (24 to 18).
- **`after-delete` against `pre-install`:** every forager.db and fungi_index.db table is equal. In mbgl-offline.db, `regions`, `region_tiles` and `region_resources` are equal; `tiles` (2007 rows, the same count) and `resources` changed: ambient cache from viewing. Of the other files, only forager.db's -wal/-shm and ART's `profileInstalled` differ. The DataStore files are equal.

### Phone end state
- Forager is on the Maps tab: not navigating, no plot, no service.
- Font 1.0, auto-rotate on, user_rotation 0, read back after the restore.
- `/data/local/tmp`: `hz-clock-watchdog.sh`, `pinch.sh` and `hz-ui.xml` are present as found (hz-ui.xml is rewritten by each UI dump). `hz-pinch.dex` is removed.

### Not verified
- The walker's dot in the navigation view itself, because Re-center needs the view left. The dot's results hold for these pans only.
- Landscape at fonts above 1.0 on the phone, so C23's wrapping is seen only in Robolectric and in portrait at 2.0.
- Whether one stop was actually expanded: one stop shows the same either way.
- Why the C20 card sits about 100 px in from the left edge at ROTATION_90.

## D19 and D21: Re-center beside the L, the portrait credit, and the 640 x 360 window on the S22 (dispatch 2026-10-30-08; RECORD -1958, -1962)

Run by Hazel from 11:50 PST on 11-02. The evidence is in `/home/zynergy-labs/s22-2026-11-02/d19/`, off git: `shots/`, `ui/`, snapshots, CI artefacts and scripts. The scale is 3.75 px per dp. No app code was changed. **This section is in progress:** the phone was paused at 12:33 at the planner's word (the owner was using it), and what is not yet done is listed at the end.

### Build and install
- **Merge.** a00ab3d09: origin/route-plot-ui 51708eeda (D17, D18, D20) merged into 50d501561, no conflicts. origin/pre-main 04746d8f0 was already in route-plot-ui (`merge-base --is-ancestor`).
- **CI run 41513366514** (workflow_dispatch, head a00ab3d09): every job green except "Build, test, publish APK"; "Build the debug APK" BUILD SUCCESSFUL, "Run the unit tests" BUILD FAILED. 0 compile-error lines in the log. From the JUnit XML: 913 files, 6363 tests, 24 skipped, **1 failure**, 0 errors.
- **The one failure, attributed by its own message:** `RouteCardRecenterShortLandscapeFont20Test`, "640 font 2.0, 3 stops, peek: a touch at (-1.0, -1.0) on Re-center expected:<12> but was:<11>". Its printed figures: at 1 stop Re-center is 186.7..347.3 x 0.3..33.3 dp, at 3 stops 0.0..6.0 dp tall, with the card 205..534 dp and the L's right edge at 104 dp. This is the known D20 case under look. D15's merge-only failure (`RouteCardRecenterLandscapeTest`) does not appear in this run, so nothing needed attributing to the tools branch's HUD changes.
- **APK:** sha256 86cf061f…789ca, versionCode 4423 (4415 was installed), signing certificate equal to the installed one (cb2f6da5…1626, both read with apksigner). Schema: this build's ForagerDatabase is 23, and the phone is at user_version 23.
- **The phone before anything:** dozing, versionCode 4415. No Forager service, no Forager notification and no shell logger process, so no recording and no walk logger. Font 1.0, auto-rotate on, user_rotation 0, `wm size` 1440x3088 and `wm density` 600 with no override. forager.db and fungi_index.db tables equal to D15's `after-delete`.
- **In-app backup:** `forager-backup-2026-11-02-d19.zip` (a new name, older files untouched), sha256 7da1247e…244b, manifest 4415 and schema 23, pulled to `d19/in-app-backup/`. Not restore-tested.
- **Across `install -r`:** every table hash and every file hash equal (`pre-install-2` against `after-install`). Between `pre-install` and `pre-install-2` only mbgl-offline.db `resources` and `tiles` changed (same counts): map cache from viewing.

### Setup
- TEST A to F dropped as waypoints (+ > Waypoint > OK > name > Drop); waypoints 11 to 17. Navigation to TEST B (about 1.5 mi) started from its bubble.
- Landscape from user_rotation 1 with auto-rotate off. A pan took the map off the navigation view so Re-center showed; the pinch injector (pushed as `hz-pinch.dex`) was used for zoom only.
- Each pin's name was read from its bubble before use. "Waypoint 1" is the owner's and was never tapped as a stop.
- Plotting went C, B, D, A, C, B (an A→B→A-style repeat, allowed), keeping every stop left of the card's region. 6 stops were shot first; Undo then took the plot to 3 and 1, so the Re-center touches did not need the map re-framed.

### D19, landscape, navigating, font 1.0 (`d19/shots/r90-*`, contact sheet `d19-landscape-sheet.png`)
Re-center sits beside the L at the bottom, as D17 rules, and did not move in any state: its pill is [520,1140][938,1320] px (138.7..250.1 x 304..352 dp). That is 30 px (8 dp) right of the record and stop-navigating pill (right edge 490), 44 px above the credit strip (top 1364), and far below the search bar (bottom 224).

| Stops | State | Shot | Card (px) | Re-center | Real touches (5, at 0.2/0.8 and centre of the pill) | Walker's dot |
|---|---|---|---|---|---|---|
| 1 | collapsed | r90-1p | [1563,671][2608,1211] | clear | not run collapsed | clear |
| 1 | expanded | r90-1e | same (one stop has no rows) | clear | 5/5 reached | clear |
| 3 | collapsed | r90-3p | [1563,746][2608,1211] | clear | 5/5 reached | clear |
| 3 | expanded | r90-3e | [1563,414][2608,1211] | clear | 5/5 reached | clear |
| 6 | collapsed | r90-6p | [1563,746][2608,1211] | clear | not run collapsed | **half under the card's left edge** |
| 6 | expanded | r90-6e | [1563,278][2608,1211] | clear | 5/5 reached | **half under the card's left edge** |

- "Reached" means Re-center was confirmed showing before the tap, was gone after it (the map went back to the navigation view), and the stop count was unchanged.
- **The walker's dot** depends on the pan. At 6 stops the map had been panned so the dot sat at x about 1563, the card's left edge, and half of it is under the card's 80% fill in both states. At 1 and 3 stops (after Re-center touches and a re-pan) it sat about 240 px left of the card, clear. Neither is the navigation view's own dot position, because Re-center needs the view left.
- Re-center keeps clear of the card at every stop count here, so the S22's own landscape size does not reach D20's fallback.

### Slips
- **A first Re-center touch run at 6 stops expanded is discarded.** Its helper did not confirm Re-center was showing before each tap. One of its small pans (40 px) did not leave the navigation view, Re-center was hidden, and a tap at its place went to the map and added a seventh stop. Undo removed it before any shot (the last legs read back as A to C and C to B), nothing was saved, and the helper was rewritten to check before every tap and to compare the stop count. All figures above are from the rewritten helper.
- **Portrait at 12:31 to 12:33 overlapped the owner's use of the phone.** The planner paused the phone at 12:33. In that window a fourth stop (TEST A) appeared after my 3-stop plot (A, F, E), after a tap I meant for the card's handle. Whether it was that tap or the owner's touch is not known. The portrait shots from that window (`p10-3p`, `p10-3e`) are not cited and will be redone.

### Not yet done (paused)
- D19 portrait at fonts 1.0 and 2.0, collapsed and expanded.
- D21 entire.
- Clean-up: the open plot (unsaved), TEST A to F, `hz-pinch.dex`, and auto-rotate (off since 12:14; it was on).
