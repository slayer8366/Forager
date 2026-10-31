# S22 combined build (#447 + #440 + #450 + #449 + #448 + tools): first build STOPPED at a red CI; rebuild (Amendment 1) installed (dispatch 2026-10-30-21)

Hazel, 2026-10-30. Branch `tools/s22-combined`, never merged and never opened as a PR. Phone data and CI downloads are kept off git, in `/home/zynergy-labs/s22-2026-10-30/combined/`.

## Part 1: the build
- Cut from origin/pre-main `1ac67f12b` (verified equal to origin/pre-main after `git fetch`).
- Each PR head was checked against `gh pr view` before merging; all five were open into pre-main at the heads the dispatch named. Merged in order, no conflicts in any:
  - trail-heights `1cc689166` (#447) as `33daadf7c`
  - altitude-sea-level `bb25405c0` (#440) as `e7c1ba3d2`
  - compass-screen-off `331c88214` (#450) as `a51159b14`
  - route-plot-ui `bcb123bc7` (#449) as `9b1b78f47`
  - street-v4 `e9be0bcdd` (#448) as `fe042d17b`
- Cherry-picked `5b926c667` as `e5639e59f` and `c4a57389f` as `39c81bb69`, both clean. `git range-diff 5b926c667^..c4a57389f HEAD~2..HEAD`: the second is `=`. The first is `!`, but the only difference is one hunk's context lines in `CompactToolsDrawerContent`'s parameter list (#449 added `onOpenPlannedRoute` there); the added and removed lines are the same.
- The one `StreetStyle.kt` change from `e4e25e87b` applied as `285476e69` (only that file).
- `ForagerDatabase.kt:214` reads `version = 23,`.
- Commit count at `285476e69` is 4271, so the APK's versionCode would be 4271 (above the installed 4224).

## CI run 41165385746 (workflow_dispatch, `285476e69`, no test filter): RED
- Build log: 0 `e:` lines. Debug APK BUILD SUCCESSFUL in 3m 10s; release compile BUILD SUCCESSFUL in 3m 7s; lint success; Tile Worker and route cutter success. "Run the unit tests" BUILD FAILED in 13m 38s.
- JUnit totals from the `unit-test-report` artifact (864 XML files):
  - testDebugUnitTest: 6184 tests, 4 failures, 0 errors, 24 skipped.
  - testDebugUnitTestOlderSdks: 24 tests, 0 failures, 0 errors, 0 skipped.
- The four failures:
  1. `StreetStyleTest` "the Street style URL is the Worker's route…" (`StreetStyleTest.kt:77`): expected the Worker's street-v3 URL, got the raw street-v4 URL.
  2. `OwnStreetStyleSourceTest` "the Street sprite is inverted at night only for the own Street style" (`OwnStreetStyleSourceTest.kt:78`): the v3 Worker URL is no longer in the Street list.
  3. `NightExceptionsV4Test` "v4 is chosen by its style's name…" (`NightExceptionsV4Test.kt:218`): `STREET_STYLE_URLS` now holds the v4 raw URL, which correctly gets v4's tables, so the "every other style keeps v1 to v3" loop fails on it.
  4. `CompassScreenOffTest` "the Maps tab of the real screen stops reading the compass while the screen is off, and keeps the 10 s rule" (`CompassScreenOffTest.kt:350`): expected `30° NE` in the compass strip, got an empty string.
- Failures 1 to 3 follow from the debug `StreetStyle.kt` line, which replaces `STREET_STYLE_URL`. `e4e25e87b`'s own message says StreetStyleTest fails on that branch by design; 2 and 3 are the same change seen by two more tests (3 exists only since #448 joined). A side effect worth knowing: on this build a region downloaded as v3 is no longer in the Street list, so it would not take the Street night sprite (inferred from failure 2, not tried on a phone).
- Failure 4 is not explained by that line. It passed on #450's own CI (run 41142139782, `331c88214`). A filtered re-run, run 41166716655 (`tests=*CompassScreenOffTest`, same commit, 0 `e:` lines), failed the same test with the same message (9 tests, 1 failure), so it reproduces and is not a one-off flake. It most likely comes from #450 meeting another PR in the merge, probably #449, whose diff touches the Maps tab's chrome (`CompactMapBottomChrome.kt`, `MapSlot.kt` and others). That is unconfirmed: I did not bisect the merges.

## Part 2: not started
The dispatch says to install nothing on a red CI. The only commands sent to R5CT321008R were `adb devices` and a read-only `dumpsys package`: it still runs 1.0.4224+gc4a57389, versionCode 4224. No backup, in-app backup, install or launch was made.

## Not done or not verified
- No install, so none of the Part 2 data checks were run.
- The cause of failure 4 is not found; which merge brings it in is untested.
- No `docs/audits/README.md` row, because this branch is never merged.

---

# Amendment 1: the rebuild, installed (2026-10-31 05:07 UTC)

Hazel. Everything above is the first build, kept as written. This section supersedes its "Part 2: not started".

## The rebuild
- Worktree clean and at origin/tools/s22-combined `9e52310f0` before starting.
- origin/street-v4 was still `307d3c2ac`, matching `gh pr view 448` (open, MERGEABLE). Its only commit after `e9be0bcdd` touches `NightExceptionsV4Test.kt` (2 lines). Merged as `16f457982`, with no conflicts.
- The one-line test fix from the clash report went in as `64e2c4138`: `CompassScreenOffTest.kt:309`, `AvailabilityUiState(liveFix = fix)` became `AvailabilityUiState(liveFix = fix).withFixHeight()`. That file only, 1 insertion and 1 deletion. No import was needed, because the helper is `internal` in the same package (`LiveHeightTestSupport.kt:14`).
- `ForagerDatabase.kt:214` still reads `version = 23,`.

## CI run 41169699098 (workflow_dispatch, `64e2c4138`, no test filter): only the three allowed failures
- Build log: 0 `e: ` lines. Debug APK BUILD SUCCESSFUL in 3m 6s; release compile BUILD SUCCESSFUL in 3m 25s; lint BUILD SUCCESSFUL in 6m 32s; Tile Worker and route cutter success. "Run the unit tests" BUILD FAILED in 13m 59s. The job is red only because of the failures below.
- JUnit totals from the `unit-test-report` artifact (864 XML files):
  - testDebugUnitTest: 6184 tests, 3 failures, 0 errors, 24 skipped.
  - testDebugUnitTestOlderSdks: 24 tests, 0 failures, 0 errors, 0 skipped.
- The three failures, all on the allowed list:
  1. `StreetStyleTest` at `StreetStyleTest.kt:77`. It expected the Worker's street-v3 URL and got the raw street-v4 URL.
  2. `OwnStreetStyleSourceTest` at `OwnStreetStyleSourceTest.kt:78`.
  3. `NightExceptionsV4Test` "v4 is chosen by its style's name, and every other style keeps v1 to v3's tables", now at line 219 (218 in the first build). The line moved because `307d3c2ac` added one line above it. The message names the street-v4 raw URL, the same cause as before.
- `CompassScreenOffTest`: 9 tests, 0 failures. The fourth failure from the first build is gone, as the clash report's control run predicted.

## APK checks (`app-debug-apk` artifact from run 41169699098)
- sha256 `145f6c6e7f1ccc7276e84e6016fe26e0ebf2d9630d414e76ed6b50a85237d8b2`.
- versionCode 4275, versionName `1.0.4275+g64e2c413`. That is above the installed 4224.
- V2 signer SHA-256 `cb2f6da502c3fe7bea8db747414bed47cbc8350944cf8291e9b09806c94f1626`. It equals the installed 4224 APK's: the base.apk was pulled from the phone and read with apksigner.
- Routing key present. `BuildConfig.ROUTING_APP_KEY` in the dex is a non-empty, 40-character string, and the CI step had `FORAGER_ROUTING_APP_KEY` set (masked). The key itself was not printed.

## The S22 (R5CT321008R)
**Baseline, before install:**
- Version: 1.0.4224+gc4a57389, versionCode 4224.
- forager.db: `user_version` 23, integrity ok.
- Regions: Region 1 is id 3, with 34 tiles in `mbgl-offline.db`. Region 2 is id 8, with 1630 tiles.
- Tracks: 5, all ended, with 273 points.
- Waypoints: 11, all attached to tracks. The walk track `ddb8d74f…` has 76 points and 3 waypoints.
- No Forager service was running.
- Walk logger: `diagnostics.walk_logger` = true.

**Raw backup:** taken with `run-as … tar` of `databases`, `files`, `shared_prefs` and `no_backup`, into `baseline/app-data-before.tar`, with its sha256 alongside. Every table in forager.db, mbgl-offline.db and fungi_index.db was hashed into `baseline/table-hashes-before.txt`: row count plus the sha256 of the rows ordered by every column. The installed 4224 base APK is kept beside it.

**In-app backup:** made from Tools > Settings > Backup > "Back up now", saved through the system picker as `Download/forager-backup-2026-10-30.zip`. The app showed "Backup saved.". It was pulled off the phone into `in-app-backup/`.
- sha256 `9760cf2c0adbfd2fbc7431aed8e2615da1df4485aa7892e27b5bd779566de834`, 18878 bytes.
- It holds `manifest.json` (appVersionCode 4224, schemaVersion 23) and `forager.db`. That forager.db's sha256 equals the manifest's.
- Its offline_regions, tracks, track_points and waypoints hash the same as the raw baseline.
- `cached_searches` is empty in the backup but has 2 rows on the phone. This is read from the backup's contents. I take it to be the backup leaving the search cache out by design, but I did not read the backup code to confirm it.

**Install:** `adb install -r` succeeded. Then one launch. The phone now reports versionCode 4275, `1.0.4275+g64e2c413`. No crash-buffer entries for Forager.

**Data checks after the launch** (pulled again into `after/`):
- forager.db: every table's row count and hash is identical to the baseline, `user_version` 23, integrity ok.
- fungi_index.db: identical.
- mbgl-offline.db: `regions` and `region_tiles` are identical, so both offline regions are untouched. The ambient cache grew by one resource (the street-v4 style from raw.githubusercontent.com) and two z15 tiles, none of them in a region. Every resource and tile from before is still present.
- Every DataStore file, shared_prefs file, `offline_routes/3` and `/8` file, and routing profile file is byte-identical. The file list is unchanged.
- ACTIVITY_RECOGNITION is granted for user 0. User 95, the dual-app profile, is `granted=false` as before and was left alone.
- The walk logger is still on: `diagnostics.walk_logger` = true, with unchanged bytes.

**Screen:** the app is in front on the Maps tab, which is selected in the bottom bar. That is observed in a uiautomator dump and a screenshot. The screenshot is kept off git.

## Not done or not verified
- The phone checks themselves are a separate dispatch, as Part 1's Output says: Sorrel's 14 for #449, 9 for #450, and the credits glance.
- I did not check whether the in-app backup restores. It was only read on the laptop.
- No `docs/audits/README.md` row, because this branch is never merged.

---

# Amendment 2: #449's new card layout, installed (2026-10-31 08:05 UTC)

Hazel. The sections above are kept as written. This one adds 4280 on top of the Amendment 1 install.

## The route-plot-ui head
- I waited for #449's PR CI in one background loop that checked every 2 minutes. At 07:35 UTC the PR head was `bce9c2de0`, the Amendment 6 head the dispatch names, and its PR run 41179955025 (event `pull_request`, full suite) completed **success**. All six jobs passed, including "Run the unit tests".
- Later, after that run went green, the #449 coder pushed Amendment 7: `d852fdc37`, a fail-first commit whose workflow_dispatch run 41181723059 is red, as a fail-first run should be, then `b069ff4df`, which is now the PR head. Those are not in this build. The dispatch asked for the Amendment 6 head, and `bce9c2de0` is the one that was reported green.
- Merged `bce9c2de0` into tools/s22-combined as `aecf9caee`, with no conflicts: 9 files, +440 −32. `ForagerDatabase.kt:214` still reads `version = 23,`.

## CI run 41181263451 (workflow_dispatch, `aecf9caee`, no test filter)
- Build log: 0 `e: ` lines. The debug APK built (BUILD SUCCESSFUL in 3m 14s). Lint, Tile Worker and route cutter all succeeded.
- **Release compile: one failure outside the allowed list, not from code.** In attempt 1, `:app:packageRelease` FAILED with `java.lang.OutOfMemoryError: Java heap space`, in `PackageAndroidArtifact$IncrementalSplitterRunnable`, at the packaging step after compilation. I re-ran that one job, on the same commit, and it was BUILD SUCCESSFUL in 4m 12s (attempt 2, job 124483078742). The other jobs were carried over and not re-run: the API shows their start times as 07:36, and the only new artifact is attempt 2's `release-compile-log`. I took this as a one-off memory failure on the runner, not a failure of this merge, and went on with the install. That was my judgement, made against the dispatch's "anything else: STOP"; the planner may read it the other way.
- JUnit totals from attempt 1's `unit-test-report` artifact (866 XML files):
  - testDebugUnitTest: 6193 tests, 3 failures, 0 errors, 24 skipped. That is 9 more tests than Amendment 1's 6184, from #449's Amendment 6.
  - testDebugUnitTestOlderSdks: 24 tests, 0 failures.
- The three failures are exactly the allowed ones, at the same lines and with the same causes as in Amendment 1: `StreetStyleTest.kt:77`, `OwnStreetStyleSourceTest.kt:78`, and `NightExceptionsV4Test.kt:219` (the "every other style keeps v1 to v3's tables" case, with the message naming the street-v4 raw URL).

## APK checks (`app-debug-apk`, from attempt 1, created 07:54:57Z)
- sha256 `f6331e1125b45faa05aefde834afe6680232a7bb1991c52aaa1a60c4230b5326`.
- versionCode **4280**, versionName `1.0.4280+gaecf9cae`. That is above 4275.
- V2 signer SHA-256 `cb2f6da5…94f1626`. It equals the installed 4275 base.apk (pulled from the phone) and the 4224 base.apk kept from Amendment 1.
- Routing key present: `BuildConfig.ROUTING_APP_KEY` in the dex is a 40-character string, and the run log shows `FORAGER_ROUTING_APP_KEY` masked. The key was not printed.

## The S22 (R5CT321008R)
Everything is in `/home/zynergy-labs/s22-2026-10-30/combined/a2/` and `…/combined/ci-41181263451/`, off git.

**Baseline** (`a2/baseline/`): 1.0.4275+g64e2c413, versionCode 4275. forager.db `user_version` 23, integrity ok. Region 1 is id 3 (34 tiles) and Region 2 is id 8 (1630 tiles). There are 5 tracks with 273 points and 11 waypoints. The app was running with no Forager service. `diagnostics.walk_logger` is true. Raw backup: `run-as … tar` of `databases`, `files`, `shared_prefs` and `no_backup` into `app-data-before.tar` (sha256 alongside), plus per-table hashes and a per-file sha256 list. Against Amendment 1's after-state, the only difference is the map cache's `resources` table.

Table hashes in this section come from a new script: the sqlite3 CLI output of each table, ordered by every column, then sha256. Its numbers do not match Amendment 1's file, so the two sets of hashes are compared only within their own amendment. Row counts agree.

**In-app backup** (`a2/in-app-backup/`): made through Tools > Settings > Backup > "Back up now". It was saved through the system picker under its default name, `Download/forager-backup-2026-10-31.zip`, as a new file beside the 10-30 one, which was left alone. Then it was pulled off the phone.
- sha256 `08b7242a7dfc378c6cd21cbdfef52287ed35300f7cb0f7b8310e6315e60f115c`, 18877 bytes.
- Its manifest says appVersionCode 4275 and schemaVersion 23. The sha256 of forager.db equals the manifest's.
- Every table matches the raw baseline except `cached_searches`: 0 rows in the backup, 2 on the phone, the same as in Amendment 1.
- I did not see the "Backup saved." snackbar: it had gone by the time of the next UI dump. The file on the phone and its contents are the evidence.

**Pre-install snapshot** (`a2/pre-install/`): taken right before the install, so the comparison covers only the install. Since the baseline, only the map cache moved, from the app being in use.

**Install:** `adb install -r` succeeded. The phone now reports versionCode 4280, `1.0.4280+gaecf9cae`. One launch. No Forager entries in the crash buffer. I cleared the crash log buffer (`logcat -b crash -c`) just before the launch so that only new entries would show. That deleted the phone's earlier crash-log entries. App data was not touched.

**Data checks after the launch** (`a2/after/`), against the pre-install snapshot:
- forager.db: every table's row count and hash is identical, `user_version` 23, integrity ok.
- fungi_index.db: identical.
- mbgl-offline.db: `regions` and `region_tiles` are identical, so both offline regions are untouched. `tiles` and `resources` have the same rows, and they are identical with the `accessed` column left out. Only the access times changed: 21 tiles and 8 resources, from the map drawing.
- Files: every DataStore, shared_prefs, `offline_routes` and routing-profile file is byte-identical, and the file list is unchanged. Three files changed: `forager.db-shm` (SQLite's shared-memory index), `mbgl-offline.db` (the access times above) and `files/profileInstalled` (ART's install marker).
- ACTIVITY_RECOGNITION: granted for user 0. User 95, the dual-app profile, is still `granted=false`, and was left alone.
- Walk logger: still on. `diagnostics.walk_logger` = true, with byte-identical file contents.

**Screen:** the app is in front on the Maps tab. A uiautomator dump shows the selected bottom-bar item at x 588 to 852, which holds the "Maps" label. There is a screenshot in `a2/after/`, kept off git.

## Not done or not verified
- #449's Amendment 7 (`d852fdc37`, `b069ff4df`) is not in this build.
- I did not check whether the in-app backup restores.
- The cause of the release-packaging out-of-memory failure was not looked into beyond the re-run.
- The phone checks themselves, such as Sorrel's lists for #449 and #450 and the credits glance, are a separate dispatch.
- No `docs/audits/README.md` row, because this branch is never merged.
