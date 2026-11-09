# Offline regions on the S22 after the protect-and-fix build (intent 2026-09-28-120)

**Coder:** a Claude Code session configured as `claude-sonnet-5-5` (session configuration; not read back from the serving side).
**Worktree/branch:** `forager-wt/device-offline`, `device-offline`, cut from `origin/journal-redesign` at `cc9a30b`. **APK built at `7d17a5c`** as the dispatch says, by a detached checkout in this worktree. `git diff 7d17a5c cc9a30b` over everything except `docs/`, `prompts/` and `RECORD.md` is empty, so the source is the same either way. (`versionName` carries the commit sha and `versionCode` the commit count, so building at the branch tip would have produced a different APK.)
**Device:** the S22, `R5CT321008R`, only. The tablet is not touched.
**Governing text, verbatim from the dispatch (`prompts/preserved/2026-09-29-06.md`, intent 2026-09-28-120):** "Create, edit and delete nothing. Restore every setting you touch, and read it back." and "Any row missing is an abort. Report it at once and restore nothing without the owner."

## State before the install (read, not inferred)

| Item | Value |
|---|---|
| Installed | `versionName=1.0.1457+gb358a4aa`, `versionCode=1457`, `firstInstallTime=2026-09-22 11:15:05`, `lastUpdateTime=2026-09-28 15:28:49` |
| New APK | `app-debug-7d17a5c.apk`, sha256 `dac26e11b23d16ef6019379e744f5ef2c9ece4eb79aaff9ac5a5f3a83d4555da`, `versionName=1.0.1577+g7d17a5c4`, `versionCode=1577` |
| Certificate | new APK and the installed base.apk both: `CN=Android Debug`, SHA-256 `cb2f6da502c3fe7bea8db747414bed47cbc8350944cf8291e9b09806c94f1626`. **Match.** |
| Build | `LC_ALL=C.UTF-8 ./gradlew --offline assembleDebug`, exit 0, 0 compile errors, after the Gradle and memory check |

### Backup (taken with the app force-stopped, through `run-as`; evidence outside the repository)

Every file's sha256 on this machine equals the device's:

| File | Bytes | sha256 |
|---|---|---|
| `databases/forager.db` | 229376 | `e88effc0cdcde55dd96cae939b27a9d7b539c2a377c375a51cdac8ad61af7c93` |
| `databases/forager.db-wal` | 466944 | `0ca51dbf6511f985d75a47672c9aeec31076b03f60c4edd78e932f114e77cbac` |
| `databases/forager.db-shm` | 32768 | `b042a2fdb808c1b9d6211aa10adc14c86d5c3ac9a093b9a36aa595e4f53d4450` |
| `files/mbgl-offline.db` | 23674880 | `c0d0d87d9763bbce87afcafb26f2c1fec2a72e4067724b91241a971ea37dad6b` |

No side files for `mbgl-offline.db` exist on the device; `files/maplibre-offline/` is an empty directory. Analysis ran on a scratch copy with the three `forager.db` files together (so the WAL is applied), leaving the backup untouched.

- **`forager.db`:** SQLite 3 header; `integrity_check` = ok; `user_version` = 16. Row counts: `android_metadata` 1, `cached_searches` 2, `cartography_entries` 7, `cartography_entry_find_refs` 2, `cartography_entry_offline_region_refs` 1, `cartography_entry_photo_refs` 1, `cartography_entry_track_refs` 1, `cartography_entry_waypoint_refs` 3, `log_entry_photos` 1, `log_photos` 3, `mushroom_log_entries` 3, `offline_regions` 2, `planned_trips` 0, `room_master_table` 1, `track_points` 23, `tracks` 1, `waypoints` 3.
- **`offline_regions` rows:** id 1 "DEVICE CHECK" (45.3262696, -122.6338591, 1 km, zoom 10 to 15, created 1790569326998, `isEntryCapture` 0); id 2 "DEVICE CHECK 2026-09-28 B" (45.3198262471555, -122.624714326657, 5 km, zoom 10 to 15, created 1790591727547, 0). These are the device checks' own regions.
- **`mbgl-offline.db`:** SQLite 3 header; `integrity_check` = ok. `regions` 2, `resources` 2, `tiles` 967, `region_resources` 4, `region_tiles` 261. Region ids 1 and 2, whose metadata names and coordinates equal the two Room rows.

## Pre-registration (pushed before the install and before the first launch)

Citations are [read] at `7d17a5c`. The planner's outcome predictions are quoted from -120; the mechanism prediction is "not authored" in the dispatch and I add none.

**Predictions (planner, verbatim):** "1. No region-read error on any cold start, and one init line per process. 2. Regions show from launch. 3. Every row kept, with no P1 warning, because Room and MapLibre agree. 4. Offline tiles still render from files/mbgl-offline.db."

**Pass conditions, per check:**

1. **Cold start, three times** (`am force-stop`, `am start`, `logcat -d`, no `logcat -c`; the log is one ring buffer, so each start's lines are separated by process id):
   - no line containing `Couldn't read offline regions.` (`ui/availability/AvailabilityViewModel.kt:934`);
   - **exactly one** `MapLibre initialised.` per process, tag `MapLibreStorage` (`map/MapLibreInitializer.kt:31`; the tag is `map/MapLibreStorage.kt:24`);
   - no `MapLibre initialisation failed` (`map/MapLibreInitializer.kt:27`) and no `MapLibreConfigurationException`;
   - no `FATAL EXCEPTION`.
   - Note: `initializeMapLibreAtStart` (`ForagerApplication.kt:41`, `:53-55`) is the only start-up caller. A later `initializeMapLibre` from `offlineManager()` or `SightingsMap` returns early once initialised, so it must not add a second line.
2. **Regions from launch**, without opening Offline maps first: the Records chip count (`ui/log/RecordsTab.kt:320` passes `visibleOfflineRegions`) includes the 2 regions; the All logbook lists them; a region's bubble on the Maps map opens its card, and `MapBubble` logs no `No offline_region … in the host's lists` (`ui/map/MapBubble.kt:286`, from `MapBubbles.kt:270`, tag `MapBubble`).
3. **Nothing lost**, after force-stop and a fresh copy of both databases: `offline_regions` has ids 1 and 2 with every column identical to the backup's rows above; every other table's count equals the backup's; `mbgl-offline.db` has `regions` = 2, `integrity_check` ok, and I record its `tiles`/`region_tiles` counts against 967/261. The file may differ in bytes (SQLite touches it on open, and MapLibre keeps an ambient cache), so byte equality is not a pass condition; the counts and rows are. **Any missing row or region is an abort, reported at once, and I restore nothing.**
4. **Offline tiles still work:** a region's map opened with Wi-Fi and data left as they are (no network setting changed) and the tiles captured on screen. Caveat recorded in advance: with the network still up, a screenshot of tiles cannot by itself prove they came from the store rather than the network; I will say what the evidence does and does not show.
5. **Logs:** any `Room region row N has no MapLibre region in this read; kept, not shown.` (`map/OfflineRegionReconciliation.kt:41`), with its id, is recorded. Prediction 3 says there are none, because the backup's Room ids (1, 2) equal MapLibre's (1, 2).

**Abort conditions (verbatim in the dispatch):** an unverifiable backup; uninstall, `-d` or a clear; a signature mismatch; any region missing afterwards; a new crash; any prompt over the app; a locked phone; touching the tablet. None has occurred at the time of writing.

---

# Resumed: results (same session, after the pre-registration above was pushed)

Install: `install -r` only, `Success`. After it: `versionName=1.0.1577+g7d17a5c4`, `versionCode=1577`, `firstInstallTime=2026-09-22 11:15:05` (**unchanged**), signature the same (`signatures=[d59f30b8]`). Evidence directory: `/home/zynergy-labs/Zynergy/device-evidence/2026-09-29-offline-regions/` (outside the repository).

## Verdicts against the pre-registered pass conditions

| # | Check | Verdict |
|---|---|---|
| 1 | Three cold starts | **Pass** on all three (pids 26794, 26991, 27179), with the caveat under "What the log check cannot show" |
| 2 | Regions from launch, Offline maps panel never opened | **Pass** |
| 3 | Nothing lost | **Pass** |
| 4 | Offline tiles still work | **Pass**, with the network caveat pre-registered and the store evidence below |
| 5 | Kept-but-missing warnings | **None occurred**; prediction 3 held |

### 1. Cold starts (`am force-stop`, `am start -n .../.MainActivity`, `logcat -d`, no `-c`; lines separated by pid)

| Start | pid | `Couldn't read offline regions` | `MapLibre initialised.` (tag `MapLibreStorage`) | `MapLibre initialisation failed` | `MapLibreConfigurationException` | `FATAL EXCEPTION` |
|---|---|---|---|---|---|---|
| 1 | 26794 | 0 | 1 | 0 | 0 | 0 |
| 2 | 26991 | 0 | 1 | 0 | 0 | 0 |
| 3 | 27179 | 0 | 1 | 0 | 0 | 0 |

The whole ring buffer (16:27 to 18:58, 313136 lines) has the `MapLibre initialised.` line exactly once per pid for these three and none elsewhere. No system or first-run prompt appeared over the app (screenshots `screen-start1.png`, `screen-start3.png`).

**What the log check cannot show.** The buffer also holds 12 starts of the *old* build (16:27 to 18:13) and none of them logged `Couldn't read offline regions` or any MapLibre line either (checked pids 20142, 18843, 12298: no MapLibre or offline lines). So "no read-error line" would have passed on the old build in this buffer too; it has no positive control here, and I could not make one (reinstalling the old build needs a downgrade, which the dispatch forbids). The pulse's claim that the old build logged the error at every S22 cold start is therefore not reproduced by this buffer, and I did not investigate why. What carries the claim that the start-up read now succeeds is check 2's positive data, not the absence of a line.

**Observation, not a gate (debug build).** Each start logs about 8 StrictMode `DiskReadViolation` traces (about 25 to 52 ms each) whose stack passes through `MapLibre.getInstance` from `MapLibreStorage.kt:27` and `ForagerApplication.onCreate` (`:39`), i.e. main-thread disk reads from the new start-up initialisation. `logcat-start{1,2,3}-pid.txt`. Also 284 `Mbgl-NativeMapView: You're calling getMetersPerPixelAtLatitude after the MapView was destroyed` errors in the buffer; not attributed to this change and I did not compare against the old build.

### 2. Regions from launch (start 3, pid 27179, still the same process throughout; the Offline maps panel was not opened)

- **Records chip counts** (`ui-records-chips.xml`): All 8, Finds 2, Tracks 1, Waypoints 3, **Offline maps 2**. 2 + 1 + 3 + 2 = 8, so the regions are counted in All.
- **All logbook** (`ui-records.xml`, `screen-records.png`) lists both "DEVICE CHECK 2026-09-28 B" (3 mi, 244 tiles, 3.1 MB) and "DEVICE CHECK" (1 mi, 17 tiles, 0.3 MB).
- **Bubble:** my first tap hit a find's card ("DEVICE CHECK find 1"), which does not count. Tapping the region's dashed outline (single tap; no long-press) opened the region's card "DEVICE CHECK", "1 mi · 0.3 MB", with a Details button (`screen-region-bubble.png`, `ui-region-bubble.xml`). Zero `No … in the host's lists` lines in the log after the tap or anywhere in the buffer. Both cards closed.
- The screenshots show `Mbgl` and Records rendering; nothing was created, edited or deleted.

### 3. Nothing lost (app force-stopped, both databases copied out again; every file's device sha256 equals the local copy, `after/sha256-verify.txt`)

- `forager.db`: `integrity_check` ok, `user_version` 16. **All 17 table counts identical** to the backup. **`offline_regions` rows 1 and 2 identical in every column.** `forager.db`, `-wal` and `-shm` are byte-identical to the backup.
- `mbgl-offline.db`: `integrity_check` ok. Counts identical to the backup: `regions` 2, `resources` 2, `tiles` 967, `region_resources` 4, `region_tiles` 261. Both region definitions and descriptions identical. **Bytes differ** (sha256 `561d2906…` after against `c0d0d87d…` before), as pre-registered. The difference I found is only the `accessed` timestamps: `max(tiles.accessed)` went from 1790644397 (18:13:17 PDT, the backup) to 1790647084 (18:58:04 PDT); 28 tile rows and both `resources` rows were touched. I did not diff every page, so "only timestamps" is what I found, not what I proved.

### 4. Offline tiles

Journal, the entry "DEVICE CHECK 2026-09-28 (L0a)" report, "Offline map" switch on (`screen-entry-offline-on.png`): the map re-rendered as shapes only (buildings, roads, no labels) with the attribution "Protomaps © OpenStreetMap", against "© OpenStreetMap, SRTM, OpenTopoMap" with the switch off (`screen-entry-report.png`). Wi-Fi and data were left on, so **the screenshot alone cannot show where the tiles came from.** The store shows it did read them: at 18:57:46 PDT, the second of the toggle, `mbgl-offline.db` rows were stamped `accessed`: `resources` id 1 `…/style/offline.json`, `resources` id 2 `…/us.json`, and three vector tiles that belong to a region (`tiles` ids 54, 65, 66 at z15, z12, z11 from the Protomaps worker). Those are the only three of the store's 611 worker tiles touched since the backup. That shows the store was hit; it does not show the network was not also used. The switch was turned back off and read back (`ui-entry-restored.xml`: the switch node at `[889,1278][1035,1413]` reads `checked=false`; the attribution is the basemap's again).

### 5. Logs

Whole buffer: `has no MapLibre region` 0, `is incomplete; kept` 0, `Deleting region` 0, `listOfflineRegions returned no list` 0. Room ids (1, 2) equal MapLibre's ids (1, 2) in both databases, so no mismatch existed to warn about, which is what prediction 3 said. That also means **P1 (kept-but-missing) and P3 (never-finished delete) were not exercised on the device**; only the agreement case ran.

## Restores and final state

No app setting was changed; the one in-memory switch (`CartographyEntryReportScreen.kt:299`, `remember`, not persisted) was flipped and flipped back. No network setting was touched (`airplane_mode_on` = 0 at the end). Nothing was created, edited, deleted, uninstalled or cleared; no `-d`. The app was left force-stopped. The tablet was not touched.

## Predictions (planner, verbatim) against what was observed

"1. No region-read error on any cold start, and one init line per process." **Held** (see the control caveat). "2. Regions show from launch." **Held.** "3. Every row kept, with no P1 warning, because Room and MapLibre agree." **Held.** "4. Offline tiles still render from files/mbgl-offline.db." **Held**, with store rows stamped at the toggle.

## Not tested

- Any disagreement between Room and MapLibre (P1) and any incomplete region (P3) on the device.
- Offline rendering with the network off (the dispatch forbade changing network settings).
- The old build's behaviour on this device (no downgrade allowed), so nothing here shows the fix *caused* the improvement, only that the new build behaves as pre-registered.
- The S26 and the tablet.

## Decisions I made

1. Built at `7d17a5c` by a detached checkout, then returned to `device-offline` to commit. Both are in this worktree.
2. Used `am start -n com.zynergylabs.forager.app/.MainActivity` (resolved through `cmd package resolve-activity`).
3. For the bubble, tapped the region's dashed outline after the first tap opened a find; I read `MapBubbles.kt:78` (the outline layer maps to `OFFLINE_REGION`) to choose that.
4. Read the store's `accessed` columns as evidence for check 4, which the dispatch did not ask for.

## Flags outside scope

- The StrictMode main-thread disk reads from `getInstance` in `Application.onCreate` (debug builds), above.
- The log-control gap above: the pulse's premise that every S22 cold start logged the error is not reproduced in this buffer.
- The 284 `Mbgl-NativeMapView … after the MapView was destroyed` errors.
