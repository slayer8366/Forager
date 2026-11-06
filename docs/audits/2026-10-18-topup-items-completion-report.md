# Top-up items: completion report (dispatch 2026-10-18-03)

Dispatch `prompts/preserved/2026-10-18-03.md`, Forager RECORD -1269 (decision) and -1270 (intent). Branch `topup-items`, PR #256 into
pre-main, **not merged**: it waits for the owner's word after the S22 check below. Written by Hazel (app coder), 2026-10-18, finishing
the work a previous Hazel session left when that session was stopped by mistake. The build commits are that session's. The CI
readings, the revert checks and this report are this session's.

## What landed

Base: pre-main `e61e248a`, checked against the remote before anything else was done. `git merge-base HEAD origin/pre-main` gives
`e61e248a`. The branch head, `392846f9`, matched `origin/topup-items` and the tree was clean.

| Commit | What it does |
|---|---|
| `61c3b181` | The tests, written first: offline screen with Download Maps only, the first-visit landscape pill, the search pill kept across tabs, and other pickers keeping OK and Cancel. |
| `13caecf5` | Offline maps: Download Maps only in both orientations, with no OK and no Cancel. It downloads where the pin sits. `CentrePinConfirmActions` gets `showConfirmButtons`, which defaults to `true`. |
| `5c8d0c16` | The tabs' landscape pill takes the Maps pill's size from the first frame. The search pill is no longer re-created on a tab switch. |
| `7fa5d2fb` | Known bugs: row 4 (the pill's evening-line height gap) and row 5 (the tablet camera's unmeasured ROTATION_0 smoothness). |
| `6580edd1` | Test rework. The first-visit test now turns in place on a configChanges host. The arrival test counts frames from the tab's selection. |
| `27b1b11c` | `OfflineMapsPanelTextTest`: Download Maps is clicked semantically, as OK was, because in landscape it is pinned outside the scrolling part. |
| `392846f9` | `AvailabilityScreenSettingsPanelTest`: two tests rewritten to the Download-Maps-only ruling, with the old assertions quoted. |
| this commit | This report. |

All of them are pushed to `origin/topup-items`. Revert branches `topup-items-revert-1` to `-12` are pushed for the checks below. They are
not for merging.

## Verification before building

This was done by the previous session and reported to the planner, who accepted it. The planner's relay is the source here. I did
not re-read that message.

- `showConfirmButtons` defaults to `true`, and only the offline panel passes `false`.
- The tile figures read the pin.
- A previous-tab flag replaces `key(compactTab())`.
- The height uses route H2. Under H2, a first landscape visit while a sundown or Back-by line shows leaves the pill a little short.
  That gap is on known-bugs as row 4.

Premise found wrong after building: the list of existing tests that needed rewriting missed `src/testDebug`. The two tests in
`AvailabilityScreenSettingsPanelTest` were found by the full PR run (CI 39134560943) and rewritten in `392846f9`. They had asserted
the behaviour that RECORD -1269 replaces: Download Maps greyed until OK. They now assert the opposite. That is a rewrite to the owner's
ruling, not a weakening, and the old assertions are quoted in the test.

## CI on this branch

| Run | Head | What it was | Result |
|---|---|---|---|
| 39133261456 | `61c3b181` | tests first, filtered | 34 tests, 25 failed. Expected: all 25 are the new tests on the unchanged code. The 9 that passed are `CentrePinLocationPickerTest`, whose new test guards behaviour that already existed. `6580edd1` records that two of the 25 failed for the wrong reason: the first-visit turns re-created the activity, and the arrival frames were counted wrongly. Reverts 6, 7 and 10 below show that the reworked tests now fail for the right reason. |
| 39133915535 | `6580edd1` | filtered, 210 tests | 1 failed: `OfflineMapsPanelTextLandscapeTest` (`performScrollTo` on a Download Maps pinned outside the scroll). Fixed by `27b1b11c`. |
| 39134560943 | `27b1b11c` | PR, full | 5079 tests, 2 failed, 24 skipped. The 2 failures were the `AvailabilityScreenSettingsPanelTest` pair above. Fixed by `392846f9`. |
| **39135958402** | **`392846f9`** | **PR, full** | **Green.** All three jobs passed: build/test/APK, lint, and release package. Read from the JUnit XML in the run's artifact: 683 files, **5079 tests, 0 failures, 0 errors, 24 skipped**. The 24 skipped match run 39134560943's count; this report does not compare them by ID. All 25 new tests pass. |

Run 39135958402 was restarted once after an earlier mistaken cancel. That happened before this session and is not mine to account for.
No run was cancelled in this session.

## Revert checks

Method: each revert branch was cut from `392846f9` with a single edit, pushed, and run on CI by `workflow_dispatch` with a test
filter. Each check was read as follows:

1. The run's log has no Kotlin compile errors: zero lines matching `e: file`, `Compilation error`, or `compile…Kotlin FAILED`. The
   debug, lint and release builds all succeeded on every revert run.
2. The JUnit XML comes from that run's own artifact, on a fresh CI machine, so no stale output is possible.
3. Each failure was checked to be one that this particular edit could cause.

Before each edit, the file was saved to `/tmp/topup-revert/save-N/`. After switching back to `topup-items`, it was restored from that
copy and checked with `cmp`. `git status` was then clean. The forward change was confirmed present on `topup-items` by grep after
every check: both `showConfirmButtons = false`, plus `landscapeTabPillFit`, `searchHeaderArriving`, the `rememberSaveable` height and
`?: tabPillStripLineHeight`. The head stayed `392846f9`, the same as the remote.

| # | Edit (one line unless noted) | Run | Result |
|---|---|---|---|
| 1 | Portrait offline panel: `showConfirmButtons = false` dropped | 39136902381 | 11 tests, 1 failed: Portrait "no OK and no Cancel", `no OK on the offline-maps screen (found 1)`. The landscape tests pass, as they should, because the landscape call was untouched. |
| 2 | Landscape offline panel: `showConfirmButtons = false` dropped | 39136904014 | 11 tests, 4 failed. L90 and L270 "no OK and no Cancel": `(found 1)`. L90 and L270 pinned part: `Download Maps starts at the controls side's padding expected:<388.0> but was:<520.0>` (OK and Cancel are back on its line). Portrait passes. |
| 3 | Enabled state back to the old guard: `pickedLat != null && pickedLng != null && !isDownloading` | 39136905672 | 37 tests, 8 failed. All three orientations' "enabled with only a position": `Failed to assert the following: (is enabled)`. All three pan-and-touch tests: `'Download this area?' … is not displayed!` (the button is greyed, so touches do nothing). Both rewritten `AvailabilityScreenSettingsPanelTest` tests: `(is enabled)`. |
| 4 | Download Maps' tap no longer picks the pin (`onRegionPicked(pinState.cameraCenter)` removed) | 39136907431 | 11 tests, 3 failed: the three pan-and-touch tests, `java.lang.NumberFormatException: empty String`. The download read blank lat and lng text. |
| 5 | `CentrePinConfirmActions` default changed to `showConfirmButtons = false` | 39136909266 | 9 tests, 4 failed. The new defaults test: `… contains 'OK' … is not displayed!`. Three existing picker tests: `Failed to inject touch input.` (their clicks on OK and Cancel have no target). All four are caused by OK and Cancel being gone. |
| 6 | Tabs' pill back to half the area (the old before-Maps fallback) | 39136911635 | 8 tests, 8 failed. For example, 823: `right expected:<628.0> but was:<371.66666>`; 780: `right expected:<585.0> but was:<350.0>`. This includes the restart test. |
| 7 | Height: no computed one-line height (`?: tabPillStripLineHeight` removed) | 39136914030 | 8 tests, 6 failed. Every first-visit test: `bottom expected:<36.0> but was:<40.0>`. **The two restart tests pass**: the Maps-measured height survives through `rememberSaveable`. |
| 8 | Measured height held by `remember` instead of `rememberSaveable` | 39136915855 | **8 tests, 0 failed.** The computed one-line height covers the restart. |
| 12 | Reverts 7 and 8 together (two lines) | 39138050587 | 8 tests, 8 failed, all `bottom expected:<36.0> but was:<40.0>`, the two restart tests included. The restart test can fail, but only when both height paths are gone. |
| 9 | Search pill keyed by the tab again (`key(compactTab()) {` … `}`, two lines) | 39136917828 | 6 tests, 5 failed. Every focus-and-cursor test: `the field is still focused after switching from List to Seasonal`, and the other pairs likewise. The arrival test passes, as it should: the key gave no slide on arrival. |
| 10 | Arrival plays the slide again (`|| searchHeaderArriving` removed from enter and exit; one token, twice) | 39136919580 | 6 tests, 1 failed. The arrival test: `two frames after the Journal was selected (frame 1) the pill is gone, not sliding up (pill: DpRect(… top=-0.6666667.dp …))`. The focus tests pass. |
| 11 | Tile figures read the last picked point (`pinRegion` changed to `pickerRegion`, twice) | 39136921592, 39138003596 | **74 tests, 0 failed** across `OfflineDownloadMapsOnly*`, `OfflineMapsPanel*`, `AvailabilityScreenSettingsPanelTest`, `LandscapeOfflinePicker*`, `OfflineDownloadConfirmationTest`, `OfflineMapRadiusBudgetGuardTest` and `EstimateOfflineTileCountTest`. |

What the checks show:

- **Unguarded: the tile figures reading the pin (revert 11).** No test holds the "~N tiles" line or the confirmation's tile figure
  following the pin. The planner accepted this change at verify-first, but the dispatch's evidence list did not ask for a test of it,
  and none was written. Whether to add one is the planner's call. The S22 check's step 2 looks at it by eye.
- **Not held on its own: the `rememberSaveable` on the measured height (revert 8).** On its own it matters only when the strip has an
  evening line, and no test models that (known-bugs row 4). The restart claim itself is held: revert 12 shows it.

## What was not changed

The diff touches only these files:

- `AvailabilityOfflineMapsUi.kt`, `CentrePinLocationPicker.kt` (the parameter only) and `AvailabilityCompactScaffold.kt`;
- `docs/process/known-bugs.md`;
- tests;
- this report.

Every other picker keeps OK and Cancel (revert 5). Nothing on the Maps tab, main, dev, RECORD.md or the audits index was touched (see
the question below). No test was silenced or skipped. Two tests were rewritten to the new ruling, as recorded above.

## Not tested, said plainly

- **The Journal's first-visit pill is not tested.** `TabsFirstVisitPillTest` covers List (ROTATION_90 and 270) and Seasonal
  (ROTATION_90) only. The dispatch names the Journal too. It goes through the same code path (`tabLandscapePillPlace`), but that is
  read, not run. S22 step 6 covers it on the phone.
- **The pill's height while an evening line is on the strip, before Maps is shown.** This is known short under H2, and it is
  known-bugs row 4.
- **Real system-bar insets.** Robolectric reports zero insets (CLAUDE.md, Known pitfalls), so the first-visit pill matching the Maps
  pill beside a real navigation bar holds only on a device.
- **The keyboard staying up across a tab switch.** The tests check the field's focus and cursor, not the IME window.
- **Tablet (sw ≥ 600) and the largest font.** Not run here, and not on the S22.

## S22 device check (owner)

Run this on the S22 Ultra, on the debug APK from CI run 39135958402 (`app-debug-apk`), or a later green run on this branch. Leave the
font at the phone's default.

Order of the steps: 1 to 4 need only portrait; 5 to 8 need the phone turned. If step 1 or step 4 fails, stop there and report it,
because the rest depends on them. If a later build changes this screen, run the check again from step 1.

1. **Portrait offline maps.** Records → Offline maps.
   - *Pass:* there is no OK and no Cancel anywhere on the screen, "Pin at: …" shows under the map, and Download Maps is not greyed on
     arrival.
   - *Evidence:* pass or fail.
2. **Download at the pin.** Pan the map somewhere new inside the continental US and note the "~N tiles" line. Change the radius and
   watch the line. Tap Download Maps.
   - *Pass:* "Download this area?" opens and its tile figure matches the line. Tap its Download, and the new region appears in the
     list, centred where the pin was.
   - *Evidence:* the region's place as the list shows it, and whether the tile figures followed the pan. No test holds the tile
     figures (revert 11), so this step is their only check.
3. **Cancel in the dialog.** Tap Download Maps, then the dialog's Cancel.
   - *Pass:* nothing downloads, and Back then leaves Offline maps.
   - *Evidence:* pass or fail.
4. **Another picker keeps OK and Cancel.** Open the Journal, then a find, then set its location on the map.
   - *Pass:* OK and Cancel are both there, and each works as before.
   - *Evidence:* pass or fail.
5. **Landscape offline maps.** Turn the phone on Offline maps.
   - *Pass:* the bottom of the right-hand panel is "Pin at: …", then one wide Download Maps, with no OK and no Cancel. The Name box
     and the slider are in view on open.
   - *Evidence:* pass or fail.
6. **First-visit pill.** Open List in portrait. Without visiting Maps, turn the phone to landscape and note the pill's width and
   height. Then tap Maps on the rail.
   - *Pass:* the pill does not change width, height or place when Maps opens.
   - Repeat from a fresh app start on Seasonal, then on the Journal (tap its search icon to show the pill). Try both turn directions.
   - Do this with no recording running. A sundown or Back-by line on the strip is known-bugs row 4.
   - *Evidence:* pass or fail for each tab and each direction. The Journal is the case that no test covers.
7. **The pill keeps focus across tabs.** In portrait, on List, tap the search pill, type two letters, and put the cursor between them.
   Tap Seasonal.
   - *Pass:* the two letters and the cursor's place are kept, and the field is still active.
   - *Observation, not a gate:* whether the keyboard stays up, and whether that feels right. Either answer is a finding.
8. **No slide on arrival.** In landscape, go List → Journal (the pill should vanish at once), then back to List (it should appear at
   once, in place). Then tap the search icon on the Journal.
   - *Pass:* the tab switches show no slide, and the Journal's own reveal still slides down.
   - *Evidence:* pass or fail.

**Row to record:** S22 Ultra, Android version, build (versionName), and the result of each step. Add anything unexpected, including on
steps that passed.

Not covered on the S22: the tablet (sw ≥ 600) for step 6, the largest font for step 5, and the evening-line case (row 4).

## Questions for the planner and the owner

1. **The audits index row.** The dispatch's scope says *must not touch* "the audits index". The planner's later instruction for
   finishing the task asks for a row in `docs/audits/README.md`. The two disagree, so this report is committed without a row. The row,
   ready to add on the planner's word:
   `| 2026-10-18 | Top-up items (dispatch 2026-10-18-03; RECORD -1269, -1270), PR #256 not merged: offline maps Download Maps only; landscape pill right from the first frame; search pill kept across tabs; known-bugs rows 4 and 5. CI 39135958402 green (5079 tests, 0 failed, 24 skipped); twelve revert checks, tile figures reading the pin unguarded | \`2026-10-18-topup-items-completion-report.md\` |`
2. **The unguarded tile figures (revert 11).** Should a test be added for the tile figures following the pin, or should the S22 step
   stand as the check?
