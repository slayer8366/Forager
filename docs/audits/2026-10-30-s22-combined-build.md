# S22 combined build (#447 + #440 + #450 + #449 + #448 + tools): STOPPED at a red CI, nothing installed (dispatch 2026-10-30-21)

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
