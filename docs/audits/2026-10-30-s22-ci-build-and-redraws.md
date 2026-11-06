# S22: the CI build of c4a57389, Region 2's trails, and the screen-off redraws (dispatch 2026-10-30-12, RECORD -1855)

Written by Hazel (device helper), 2026-10-30, on the S22 (SM-S908U, serial R5CT321008R) only. Raw evidence (logcat, data
copies, screenshots, which carry positions) is kept outside every repository at `/home/zynergy-labs/s22-2026-10-30/ci-build/`.
No Gradle was run and no code was changed.

## Part 1: the CI build

| Step | Result | How checked |
|---|---|---|
| 1 Baseline | Installed 1.0.4224+gc4a57389 (versionCode 4224), schema `user_version` 23. Region 1 (id 3): route data `state=stored`, run id `usca-20261027T221400Z`. Region 2 (id 7): `state=missing`, `reason=no_app_key`. 4 tracks, 271 track points, 9 waypoints. Walk logger on (`diagnostics.walk_logger` = true), ACTIVITY_RECOGNITION granted. | `dumpsys package`; `run-as` tar of `databases/` (db + WAL + shm), `files/datastore`, `files/offline_routes`, `files/routing`, `mbgl-offline.db`, saved to `ci-build/baseline/`; a per-table row count and content hash of every table |
| 2 Download | Artifact `app-debug-apk` (id 13002153572) of run 41107156444 (CI, `workflow_dispatch`, head `c4a57389`, success). Zip sha256 `c369738747cd…3a12` equals the API's digest. APK: versionCode 4224, versionName 1.0.4224+gc4a57389: equal to the installed one, not below, so `install -r` applies with no `-d`. | `gh api …/artifacts`; `sha256sum`; `aapt2 dump badging` on both APKs |
| 2 Signing | Same certificate on both: CN=Android Debug, SHA-256 `cb2f6da502c3…1626`. | `apksigner verify --print-certs` on the CI APK and on the installed APK pulled from the phone |
| 3 Key | CI APK: `BuildConfig.ROUTING_APP_KEY` is present with a 40-character value. Laptop APK (installed before): the same field, empty. The key was not printed; only its length and a short hash prefix were. | `dexdump` of `com/zynergylabs/forager/app/BuildConfig` static field values (the build config injects the key as that field, `app/build.gradle.kts:116-126` at c4a57389) |
| 4 Install | `adb install -r` succeeded; the APK pulled back from the phone has the CI APK's sha256 (`0ccdb945…8b40`). Launched. Schema 23. Every table's row count and content hash identical to the baseline. | same table hash, `diff` empty |
| 5 Region 2's trails | **Not done: the app has no flow that fetches them.** See below. Region 2 still reads `state=missing`, `reason=no_app_key`. | Offline maps screen read with `uiautomator`; code read |
| 6 Logger, permission, tab | Logger still on, ACTIVITY_RECOGNITION granted, the app left in front on the Maps tab, not recording. | datastore file, `dumpsys package`, screen dump |

### Step 5: the premise does not hold

Region 2's row in Offline maps reads "Map only. Trail routes need an app update." with no button. That is by design:

- `offlineRouteLine` (`ui/availability/OfflineRegionRouteLine.kt:30-38` at c4a57389) shows that line with `tryAgain = false`
  for a region missing for a reason `needsAppUpdate()` covers, which is `NO_APP_KEY` or `FORBIDDEN`
  (`domain/routedata/RegionRouteData.kt:152-153`).
- `NO_APP_KEY` is in `NOT_RETRYABLE` (`RegionRouteData.kt:155-161`), so `needsFetch()` is false for it
  (`RegionRouteData.kt:167-171`), and the automatic fetch on opening Offline maps only takes regions where `needsFetch()` is
  true (`ui/availability/AvailabilityOfflineRoutes.kt:29-30`).

So a region recorded as `no_app_key` by a build without the key stays that way after a build with the key is installed: no
retry button, no automatic fetch, and no "download again" except deleting the region and downloading it anew. Deleting
Region 2 would remove the owner's data, and the dispatch's option said "Your data stays", so it was not done. Editing the
stored state through `run-as` would not be the app's own flow. **This is a decision for the planner and owner**, options as
I see them:

1. Delete Region 2 and download it again with this build (loses the region as stored; a new id).
2. A code change so a stored `no_app_key` is fetched again when the running build has a key (`no_app_key` is a fact about the
   build that wrote it, not about the area). Needs a dispatch.
3. Leave it until a build with that change ships.

## Part 2: the screen-off redraws (look only)

### What the lines are

The walk's "redraw" lines are three lines repeated together from the app's main thread:

```
D SV[… MainActivity]: updateSurface: surface is not valid
I SV[… MainActivity]: releaseSurfaces: viewRoot = VRI[MainActivity]@…
D VRI[MainActivity]@…: applyTransactionOnDraw applyImmediately
```

`SV[…]` is the map's `SurfaceView` (`org.maplibre.android.maps.renderer.surfaceview.MapLibreVulkanSurfaceView`, named in the
same log's `windowStopped` lines). The platform's `SurfaceView.updateSurface` runs on each view-tree traversal; with the
screen off the surface is gone, so it logs "not valid" and releases. Each triple is therefore one traversal of the
activity's view tree, that is, one frame the app asked for, with nothing to draw it on. On the 10-30 walk this ran at 14-17
per second (about 940 a minute) for all of the recording.

### Source: the compass, collected in composition at sensor rate

Reproduced on the S22 with this build; each trial turned the screen off with `KEYCODE_SLEEP` and sampled the app's CPU
ticks (`/proc/<pid>/stat`, `/proc/<pid>/task/<pid>/stat`), the compass listener's sensor access (`dumpsys sensorservice`)
and the process state every ~8 s. Phone on a desk, indoors, on USB.

| Trial | Screen off for | Redraw lines | Compass sensor access | Main thread CPU | Whole process CPU |
|---|---|---|---|---|---|
| A3: app in front (Maps), not recording | 175 s | 774, all in the first ~60 s | true until ~60 s, then false | 26-27 % of a core for ~60 s, then 0 | 16.1 s main / 17.4 s process in 175 s, all in the first ~67 s |
| B: backgrounded (Home), not recording | 116 s | 20 | true until ~58 s, then false | ~5.5 % for ~58 s, then 0 | 3.4 s |
| C: app in front (Maps), recording | 116 s | 776, throughout | true throughout (process state 4, foreground service) | ~16 % throughout | 18.9 s main / 31.9 s process |
| D: backgrounded, recording | 117 s | 7 | true throughout | ~4.7 % throughout | 5.5 s main / 16.9 s process |

(A and A2, two earlier runs of trial A, gave the same shape: 794 and 644 lines, both ending about 60 s after the screen went
off.)

Read together:

- The redraws follow the compass. They run exactly while the compass listener has sensor access and stop the moment
  Android withdraws it (about 60 s after screen-off for a process with no foreground service). A recording's foreground
  service keeps sensor access, so during a recording they never stop. The walk log shows the same: ~940 a minute through the
  recording, then 149 and 36 in the two minutes after it ended at 12:37:57 with the screen off, and a burst of 719 in the
  minute after the screen went off at 12:59:52.
- The redraws need the activity to have been in front when the screen went off. Backgrounded, the compass is still
  collected (B and D still spend 4-6 % of a core on the main thread), but almost no traversals happen.
- The rate fits the sensor: the compass registers its rotation-vector listener at `SENSOR_DELAY_UI`
  (`sensor/AndroidCompassProvider.kt:151`), which the sensor service shows as a 66 667 µs period, 15 Hz. The walk's 14-17
  lines a second match; on the still desk it was 6-14 a second, consistent with readings that do not change the shown value
  not causing a frame (inferred, not instrumented).

The code path (read at c4a57389):

1. `rememberTrueHeading` collects `compassProvider.heading` inside `produceState` (`ui/map/TrueHeading.kt:97-100`). That
   coroutine lives as long as the composition, and the composition is not stopped when the activity stops, so the
   collection does not follow the lifecycle. It is called for the Maps tab at `ui/availability/AvailabilityCompactMapUi.kt:417`.
2. Each reading sets the produced state. Its readers then work at sensor rate even with the screen off:
   - the compass strip's readout reads `heading.value` (`ui/availability/CompassElevationStrip.kt:126`), recomposing the
     "304° NW" text;
   - the map's own compass engine is fed through `snapshotFlow { source.value }.collect { engine.update(it) }`
     (`ui/map/SightingsMap.kt:283-287`), which turns the location puck in MapLibre.
3. A changed text or puck invalidates the view tree, the activity's `ViewRootImpl` schedules a traversal, and the map's
   `SurfaceView` logs the triple above.

Which of the two readers asks for most of the traversals was not separated (that would need instrumentation or a code
change). That the strip text alone is enough is inferred from the Compose path, not measured.

Not the source: a render loop off the main thread (in trial A2's 117 s the main thread took 1 584 ticks and no other thread
more than 17, `RenderThread`), the location fixes (no `ForagerFix` lines with the screen off in trial A), and the
barometer (1 Hz with 5 s batching, `AndroidPressureProvider` registration in `dumpsys sensorservice`).

### Is it new?

No. It is older than #440, #447 and the tools build, and pre-main has it.

- `git merge-base` confirms `origin/pre-main` (b493cffeb) is an ancestor of c4a57389, and `git diff origin/pre-main c4a57389`
  touches none of `TrueHeading.kt`, `AndroidCompassProvider.kt` or `AvailabilityCompactMapUi.kt`. The `produceState` collector
  and the `SENSOR_DELAY_UI` registration are at the same lines on pre-main (`TrueHeading.kt:97-100`,
  `AndroidCompassProvider.kt:151`).
- `git log -S"produceState<TrueHeadingReading>"` finds it at the start of this repository's history (7957025fa, 2026-09-09).
- Older logs: the only long screen-off run of these lines in `/home/zynergy-labs/s22-2026-10-2*/` is
  `s22-2026-10-29/pr440/walk/logcat-all.txt` (3 808 lines), which is PR #440's build, so not independent of #440. The
  10-28 PR #432 log has 62 lines in about 4 s at a transition, not a screen-off stretch. **No log on a build without #440 or
  #447 with a long screen-off period exists there**, so the age rests on the code path, not on an older log. A pre-main
  build could not be installed to test it (it would be a downgrade).
- What the tools build adds is the walk logger's own sensors, which raise the process's CPU while recording (C's process
  total is ~11 % of a core above its main thread, against ~2 % in A3's first minute), not the redraws.

### Cost

With the screen off, from the trials above (CPU time, `/proc` ticks at 100 Hz; battery not measured, see below):

- **Recording, app left in front:** main thread ~16 % of one core for as long as the recording runs, against ~4.7 % with
  the app backgrounded. The redraws cost about **11 % of a core, ~7 CPU-seconds a minute**, for the whole walk.
- **Not recording, app left in front:** ~27 % of a core against ~5.5 % backgrounded, so about **21 % of a core
  (~13 CPU-s a minute), for about the first minute only**, then nothing.
- **Underneath both:** with the screen off, the compass collection itself costs ~5 % of a core on the main thread whenever
  sensor access lasts, in front or not.
- **Memory:** the walk log shows the app's GC freeing 26 MB every 24-25 s through the recording (12:20 to 12:37), about
  1 MB a second of short-lived allocation; that is the "memory freed every ~25 s". On the still desk (trial C) one 24 MB GC
  in two minutes, consistent with fewer readings changing the shown value.

Not measured: battery drain per minute. `dumpsys batterystats` would have needed a reset of the phone's battery history to
isolate two minutes, which was not done; the phone was also on USB power, at 100 %.

## What the phone holds now

- Build 1.0.4224+gc4a57389 from CI run 41107156444, carrying the routing key. Schema 23.
- Region 2 unchanged: `state=missing`, `reason=no_app_key` (Part 1 step 5 above).
- **One new track** from trials C and D (dispatch Part 2 step 1, "both recording and not"): started 13:40:50 PDT, ended
  13:46:50 PDT, 2 track points, with its Start and End waypoints. The table hashes otherwise equal the post-install state:
  tracks 4 → 5, track points 271 → 273, waypoints 9 → 11, `sqlite_sequence` changed with them. Nothing was deleted.
- Logger on, ACTIVITY_RECOGNITION granted, app in front on the Maps tab, not recording.

## Disclosure

- **Confirmed:** every Part 1 figure, the redraw lines' identity, their timing against sensor access, CPU per trial, the
  code path's lines, and that pre-main carries the same code.
- **Inferred:** that each triple is one view-tree traversal (from the platform's `SurfaceView` behaviour, not instrumented);
  that unchanged readings cause no frame; which reader causes most traversals.
- **Could not determine:** battery cost per minute; an older-build screen-off log.
- **Premise that was wrong:** Part 1 step 5 assumed Offline maps offers a retry or "download again" for Region 2. For a
  `no_app_key` region it offers neither (above).
- **Beyond scope:** none. No fix proposed in code; the observation that a lifecycle-aware collection would stop this is the
  obvious reading of the cause and is left to the planner.

## Region 2 redone (dispatch 2026-10-30-13, RECORD -1857)

Hazel, 2026-10-30, S22 (R5CT321008R) only, build 1.0.4224+gc4a57389 (the CI build with the routing key), schema 23. Raw
evidence (data copies, table hashes, screenshots, BRouter output, scripts) is at `/home/zynergy-labs/s22-2026-10-30/region-2/`
(`before/`, `after-delete/`, `after-download/`, `scratch/`), never in git. No coordinates here. No Gradle, no uninstall,
no `-d`, no clear data.

| Step | Result | How checked |
|---|---|---|
| 1 Read | Region 2 was id 7, name "Region 2", radius 10 km, zoom 10 to 15, made 12:15:58 PDT today, route state `missing`, `no_app_key`. **No journal entry references it:** `cartography_entries`, `cartography_entry_offline_region_refs` and `mushroom_log_entries` are all empty. | `offline_regions` row and `routes.properties` from the backup; row counts of every entry table |
| 2 Back up | `before/app-data.tar` (databases with WAL, datastore, `offline_routes`, `routing`, `mbgl-offline.db`). Every table's count and hash equals dispatch 12's final state. | `run-as` tar; the same per-table hash script; `diff` empty |
| 3 Delete | Trash button on Region 2's row, "Offline map deleted" snackbar left to run out (Undo not touched). After: `offline_regions` 2 → 1, Region 1's row byte-identical; **every other table's hash unchanged** (tracks 5, track points 273, waypoints 11). `offline_routes/7` gone, `offline_routes/3` files unchanged by sha256. MapLibre: region 7 and its 1630 region-tile links gone, region 3 kept. | backup `after-delete/`; table hash `diff` shows only `offline_regions` |
| 4 Download | Through Offline Maps on Wi-Fi: picker pin panned to the old centre until "Pin at" read it to 4 decimals, name typed "Region 2", radius left at the remembered 10 km ("6 mi"; the confirmation read "Region 2 · 6 mi around the pin · about 815 tiles", the same 815 Region 2 took from the budget before). Download, then "Getting trail routes", then the row "downloaded just now" with no "Map only" line. | screen dumps; screenshots |
| 5a Tiles | New region **id 8**, "Region 2", 10 km, zoom 10 to 15. MapLibre region 8 holds 1630 tiles, the same count region 7 held. Centre 5.4 m north and 0.8 m east of the old one (the pin can be read only to 4 decimals). | `offline_regions`; `mbgl-offline.db` `region_tiles` |
| 5b Trail data | `offline_routes/8/routes.properties`: `state=stored`, **run id `usca-20261027T221400Z`**, one file `W125_N45.rd5`, 739 591 bytes, no missing squares; the file's sha256 equals the recorded one. | the properties file; `sha256sum` |
| 5c Heights | **Every routed node carries a height.** The owner's 10-30 walk (inside Region 2), routed through the new file the way dispatch 11 did (BRouter 1.7.10, the app's own `hiking-mountain` profile copied from the phone, via every 100 m): 132 of 132 nodes have `<ele>`, 169 to 232 m; the route runs on footway (10 segments), service (4) and residential (1). Six more routes out from the centre (1.2 to 14 km): 703 of 703 nodes with heights. | `scratch/route.py`, `scratch/walkkinds.py`, `ground.py` from dispatch 11; GeoJSON way tags |
| 6 Leave | Walk logger on (`diagnostics.walk_logger` true), ACTIVITY_RECOGNITION granted, app in front on the Maps tab, not recording. | datastore file; `dumpsys package`; screen dump |

After the redo, against step 2: the only table that changed is `offline_regions` (id 7 replaced by id 8); in the datastore
only `offline_map.last_picked_lat`/`_lng` changed, which the download writes on success (`AvailabilityOfflineMaps.kt:245` at
c4a57389). Region 1, all tracks and all waypoints hash unchanged.

**An observation, not a gate:** on the walk's 91 matched points, the new file's heights read 9.8 m below the laptop's full
2026-10-03 file that dispatch 11 had to use as ground (sd 3.1 m, range 2.3 to 15.8 m below). Dispatch 11 found that full
file 2.0 m below Region 1's file near home. So the two dated files disagree more here than near home. Which one is nearer
the true ground was not checked; scoring the walk against this file was not in this dispatch.

**Disclosure.**
- **Confirmed:** everything in the table, by the means named.
- **Inferred:** that the run id `usca-20261027T221400Z` is the current server run (it is the same one Region 1 has; the
  dispatch 11 brief expected 10-28+ data, but this file carries heights regardless).
- **Could not determine:** the old centre to better than the "Pin at" line's 4 decimals, so the new centre is a few metres
  off it. The snackbar's exact duration (it was gone at the first check, 3 s after starting the wait).
- **Premise that was wrong:** none found.
- **Beyond scope:** the height comparison above, reported only.
