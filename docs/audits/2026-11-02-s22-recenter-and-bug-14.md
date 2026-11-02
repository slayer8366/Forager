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
