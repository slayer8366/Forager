# Compass clash: why #450's real-screen compass test fails once combined (dispatch 2026-10-30-22): STOPPED before any fix

Hazel, 2026-10-30 (written 2026-10-31 04:39 UTC). Diagnosis only, as the dispatch asks. No PR branch was pushed to. Nothing was fixed.

## Answer in four lines
- **The clashing PR is #440 (altitude-sea-level, `bb25405c0`).** #450 with #449, #448 or #447 passes; #450 with #440 fails the same way as the combined build.
- **The cause:** since #440 the strip's altitude comes from `AvailabilityUiState.shownHeight`, not from the fix. #450's new test gives a fix and no shown height, so the strip reads "Alt unavailable". That long text leaves no room at 360 dp, so the strip's fit rule drops the heading, the readout it gives up first. With no heading node in the tree, the test's `stripText()` returns "".
- **Test-only, not a screen break.** The test fails at its first strip check, before any screen-off. Nothing in #450's screen-off or 10 s behaviour is involved. Given the fix's own height, as #440 did in 20 other screen test files, the same merge passes 9 of 9.
- **Proposed fix:** one line in `CompassScreenOffTest.kt:309`, `AvailabilityUiState(liveFix = fix)` becomes `AvailabilityUiState(liveFix = fix).withFixHeight()`. Which PR it goes in depends on merge order, and that is the planner's or owner's choice (below).

## Base and heads checked
- origin/pre-main at `1ac67f12b`, as the dispatch states (checked after `git fetch origin`).
- #450 `331c88214` sits directly on `1ac67f12b`, so "pre-main + #450" is `331c88214` itself.
- **One head has moved since the dispatch:** origin/street-v4 (#448) is now `307d3c2ac`, not `e9be0bcdd`. I used `e9be0bcdd`, the commit the dispatch names and the combined build merged. #448's newer commits were not tested against #450. The other three heads match the dispatch: #449 `bcb123bc7`, #447 `1cc689166`, #440 `bb25405c0`.

## Step 1: which PR clashes
Each scratch branch is `331c88214` plus one PR, merged with no conflicts. Each was run with `gh workflow run ci.yml -f tests='*CompassScreenOffTest'`. For every run I read the build log for `e:` lines first, then the JUnit XML from the `unit-test-report` artifact.

| Branch (merge commit) | PR merged onto #450 | Run | `e:` lines | JUnit | Result |
|---|---|---|---|---|---|
| scratch/compass-clash-449 (`ec5c9f4d1`) | #449 route-plot-ui `bcb123bc7` | 41168157467 | 0 | 9 tests, 0 failures | pass |
| scratch/compass-clash-448 (`c4c50c5bd`) | #448 street-v4 `e9be0bcdd` | 41168161709 | 0 | 9 tests, 0 failures | pass |
| scratch/compass-clash-447 (`73fe84880`) | #447 trail-heights `1cc689166` | 41168166758 | 0 | 9 tests, 0 failures | pass |
| scratch/compass-clash-440 (`6bc122026`) | #440 altitude-sea-level `bb25405c0` | 41168173103 | 0 | 9 tests, 1 failure | **fail** |

On #440, the failure is the same test with the same message as the combined runs 41165385746 and 41166716655: `expected:<[30° NE]> but was:<[]>`. The run count is 9, which is the class's full count. All four builds compiled, so none of these results is stale output from an earlier run.

The combined build report guessed #449. That guess is wrong: #449 passes with #450.

## Step 2: the cause
Read in the #450 + #440 merge, `6bc122026`:
1. The test builds its screen state with a fix and nothing else: `uiState = AvailabilityUiState(liveFix = fix)` (`CompassScreenOffTest.kt:309`). The fix has `altitude = 50.0`.
2. #440 changed where the strip's height comes from. `liveAltitudeMeters` now reads `shownHeight` and no longer the fix's own altitude: `if (liveFix == null) null else (shownHeight as? ShownHeight.Known)?.meters` (`AvailabilityUiState.kt:426`). `shownHeight` defaults to null, so the strip gets no height.
3. With no height, the strip's altitude text becomes `ELEVATION_UNAVAILABLE_TEXT` (`AvailabilityMapControlsUi.kt:343`), which is "Alt unavailable" (`NavigationHudText.kt:59`). Before #440 it was "164 ft" or "50 m" from the fix.
4. `readoutsFitBeside` (`AvailabilityMapControlsUi.kt:360`; rule at `CompassStripMeasurement.kt:109`) drops labels first, then the heading, then the altitude, keeping the coordinates whole (RECORD -713). In the test's 360 dp window, "Alt unavailable" leaves no room for the heading, so `fit.shown[0]` is false. The heading block at `AvailabilityMapControlsUi.kt:386` to `:399`, which carries `COMPASS_STRIP_HEADING_TAG`, is never composed.
5. `stripText()` (`CompassScreenOffTest.kt:145` to `:148`) joins the text of every node with that tag. With no node it joins an empty list, so it returns "". That is the "" in the failure.

**Probe (scratch/compass-clash-440-probe, `838ef2c41`, run 41168825528, 0 `e:` lines, 9 tests, 1 failure).** This is the #440 merge with a deliberate throw added just before the failing check. The throw reports what the strip holds. The message read: `PROBE elevation=[Alt unavailable] headingNodes=0 coordinateNodes=1`. So the strip is composed and shows its coordinates and "Alt unavailable". It has no heading node at all, counted in the unmerged tree too, so the heading has not been merged into a parent.

**Positive control (scratch/compass-clash-440-fixheight2, `11cd8fa0c`, run 41168858919, 0 `e:` lines, 9 tests, 0 failures).** This is the #440 merge with one change: `CompassScreenOffTest.kt:309` gives the state the fix's own height through #440's test helper `withFixHeight()` (`LiveHeightTestSupport.kt:14`). All 9 tests pass, including the screen-off steps after line 350. So the shown height is the whole cause.

**Real screen or test only: test only.** Evidence:
- The failing assertion (`:350`) comes before `screenOff()` (`:352`), so the screen-off path is never reached. The only thing that differs is the screen state the test builds.
- On a phone, `AvailabilityLiveLocation.begin()` converts every accepted fix and sets `shownHeight` (#440's `AvailabilityLiveLocation.kt`, `showHeight`). The test's state, a fix with no shown height, is one the live path fills in.
- #440 already hit this same mismatch in 20 other screen test files (19 tests and one shared helper) and fixed them the same way. Its commit `1aa2a91bb` says those tests "built a state with a live fix and no shown height, so the strip read 'Alt unavailable' where they had measured a height". #450's test was written after that, on a base without #440, so it missed the change.

**Inferred, not tested:** a phone with no height to show (no altitude in the fix and no conversion) would also drop the heading from the strip at narrow widths. That is RECORD -713's rule, and it predates both PRs: pre-main's unavailable text is the longer "Elevation unavailable". #440 may also leave `shownHeight` null for a moment between the first fix and its conversion, which could hide the heading for that moment. That is #440's own behaviour, not this clash, and I did not check it on a device.

## Step 3: the proposed fix, and merge order
**The fix:** `CompassScreenOffTest.kt:309`, change `AvailabilityUiState(liveFix = fix)` to `AvailabilityUiState(liveFix = fix).withFixHeight()`. It is one line. It weakens nothing: the test still checks the same heading text and the same subscription counts. Run 41168858919 shows it works on the #450 + #440 merge.

**Where it can go.** `withFixHeight()` exists only once #440 is in, so the line cannot go into #450 on its current base. There are two orders. Choosing between them is the planner's or owner's call, not mine:
- **(a) #440 first, then #450.** Bring pre-main into #450 after #440 lands, and add the line in #450. The test then lives with its own fix in the PR that added it.
- **(b) #450 first, then #440.** Bring pre-main into #440 after #450 lands, and add the line in #440. This matches what #440 did in `1aa2a91bb` for the 20 test files it changed.

**Would pre-main go red? Yes, in either order, unless the line goes in with the second merge.**
- The failure needs both PRs. Each passes on its own: #450 in run 41142139782, and #440 alone has no `CompassScreenOffTest` at all.
- So whichever PR merges second turns pre-main red at that merge commit, failing 1 test, the same one.
- The second PR's existing green CI is stale once the first PR has landed. A fresh `pull_request` run builds the merge with the current base, so it would show the red before merging (inferred from how GitHub `pull_request` runs work, not tried here).

## Housekeeping
- **One slip of my own, disclosed.** My first control branch, scratch/compass-clash-440-fixheight, ended on a commit (`6ad56579a`) that undid its own one-line change. A shell chain kept going after a failed `git checkout -b`, and a following `git commit -a` committed the restored file. `git diff` against the #440 merge came up empty, which is how I caught it. I cancelled its run (41168795653) and cite none of its output. The control above comes from the correct commit `11cd8fa0c`, on a new branch, and I checked its diff before citing it.
- All scratch branches (`scratch/compass-clash-449`, `-448`, `-447`, `-440`, `-440-fixheight`, `-440-fixheight2`, `-440-probe`) are deleted locally and on origin now that their results are recorded here. CI logs and XML are in `/mnt/work/scratch/compass-clash/`.
- No PR branch was pushed to. No `docs/audits/README.md` row, since the planner writes those.

## Not done or not verified
- #448's newer head, `307d3c2ac`, was not tested with #450.
- No device check. The test-only verdict rests on the code path and the CI runs above.
- The full suite was not run on the #450 + #440 merge, only `CompassScreenOffTest`. Whether #440 clashes with any other new test from #447, #448 or #449 is outside this dispatch and untested.
