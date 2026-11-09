# Directions to Navigate: completion report

Dispatch 2026-10-29-09 with Amendment 1 (`prompts/preserved/2026-10-29-09-directions-to-navigate.md` on `records-after-173`), RECORD -1817 to -1819. Written by Hazel, 2026-10-29, picking up from a coder session that was stopped after the change was pushed. First stop: `docs/audits/2026-10-29-directions-to-navigate-first-stop.md`.

## What landed

Branch `directions-to-navigate`, cut from pre-main `21d846b9e` (the dispatch's base). PR #444 into pre-main, open, not merged.

| Commit | What |
|---|---|
| `6f65412ec` | First stop (look only) |
| `8ea3c49e0` | Tests first |
| `6f0406ccd` | The change |
| (this report) | Completion report |

### The change against Amendment 1 (read in `git diff 8ea3c49e0 6f0406ccd`)

| Amendment 1 says | Where in the change | Verdict |
|---|---|---|
| Directions goes from row 1 (Maps-tab waypoint bubble) and row 2 (entry-map bubble, waypoint still in Records) | `MapFeatureBubble.kt:92-96`: the waypoint bubble's Directions is drawn only `if (!content.hasDetails)`. `hasDetails` is true for a waypoint in Records on both maps, so both lose it | Matches |
| Row 3 (entry-map bubble, waypoint deleted from Records) keeps Directions | Same lines: `hasDetails = false` there (`MapBubbles.kt:312-313`, per the first stop), so Directions is drawn | Matches |
| Row 4 (details sheet) | `RecordDetailsContent.kt`: the Directions `OutlinedButton` and its now-unused imports and `context` removed; Navigate unchanged | Matches |
| Rows 5 and 6 (Records > Waypoints and Records > All) | `AvailabilityTripsWaypointsUi.kt`, `WaypointRow`: the Directions `BouncingIconButton` removed. Both lists use this one row (first stop, rows 5 and 6) | Matches |
| Rows 7 and 8 (planned trip bubble and Trip Planner row) keep Directions | Untouched: `MapFeatureBubble.kt:117`, `AvailabilityTripsWaypointsUi.kt:160-162` | Matches |
| `launchDirections` stays | Still at `AvailabilityPureFunctions.kt:173` and `:190`; production callers `MapBubbleLayer.kt:144` (rows 3 and 7) and `AvailabilityTripsWaypointsUi.kt:160` (row 8) (read by grep of `app/src/main`) | Matches |
| Three stale doc comments fixed | `MapBubbles.kt:198-201` (now: the Maps tab and, since -890, an entry map pass Navigate), `MapFeatureBubble.kt:53-55` (same), `RecordDetailsSheet.kt:48-53` (Navigate only, every host passes one). Also corrected beyond the three: `WaypointRow`'s KDoc and `WaypointContent`'s one-line KDoc (`MapBubbles.kt:232`), which described the removed Directions | Matches; two extra comments, see "Calls beyond the dispatch" |

`RECORD_DETAILS_DIRECTIONS_TAG` (`RecordDetailsFormat.kt:110`) stays, with a comment saying it is kept for the tests that assert the button stays gone. `MAP_BUBBLE_DIRECTIONS_TAG` stays because rows 3 and 7 still use it.

## Every edited test assertion

All in `8ea3c49e0`; nothing in `6f0406ccd` touches a test. Line numbers are at `6f0406ccd`.

**Flipped from Directions present to Directions absent (rows 1, 2, 4, 5, 6 only):**

| # | Class, test | Row | Before | After |
|---|---|---|---|---|
| 1 | `AvailabilityScreenMapBubblesTest`, was `a waypoint's Directions hands the waypoint to a navigation app`, now `a waypoint's bubble offers no Directions` (:390-397) | 1 | Touch Directions; assert `ACTION_VIEW` and the `geo:` URI | Bubble displayed; `MAP_BUBBLE_DIRECTIONS_TAG` does not exist |
| 2 | `AvailabilityScreenWaypointFlowTest`, `every Waypoints drawer control is reachable, ...` (:368) | 5 | "Directions to Reachable Waypoint" scrolled to and displayed | does not exist |
| 3 | `RecordDetailsSheetTest`, `assertWaypointCreekContent` helper (:237), used by four row-tap tests | 4 | `DIRECTIONS` scrolled to and displayed | does not exist |
| 4 | `RecordDetailsSheetTest`, was `the waypoint row's own Directions button starts directions and does not open the sheet`, now `the waypoint row has no Directions button` (:370-377) | 5 | Tap the row's Directions; assert the `geo:` URI and no sheet | "Creek pin" row displayed; "Directions to Creek pin" does not exist |
| 5 | `RecordDetailsSheetTest`, was `the waypoint sheet offers Navigate beside Directions, and its Directions starts directions to that waypoint`, now `the waypoint sheet offers Navigate and no Directions` (:478-492) | 4 | Directions displayed, tapped, `ACTION_VIEW` and `geo:` URI asserted; Navigate displayed and labelled "Navigate" | Directions does not exist; Navigate displayed and labelled "Navigate" (unchanged) |
| 6 | `RecordDetailsSheetTest`, `a real touch anywhere on a walk's waypoint row ...` (:611) | 4 | `DIRECTIONS` displayed | does not exist |
| 7 | `RecordsFilterChipsTest`, `every All row carries its type badge, ...` (:481) | 6 | "Directions to Morning pin" exists | does not exist |
| 8 | `AvailabilityScreenWaypointNavigateTest`, `from a waypoint's bubble on a journal entry's map, Navigate targets that waypoint on the Maps tab` (:704) | 2 | `MAP_BUBBLE_DIRECTIONS_TAG` exists | `assertNavigateNotDirections`: Navigate labelled "Navigate", Directions does not exist |

**Added (strengthen, never weaken):**

| # | Class, test | Row | Added |
|---|---|---|---|
| 9 | `AvailabilityScreenWaypointNavigateTest`, `from a waypoint's bubble on the Maps tab, ...` (:328) | 1 | `assertNavigateNotDirections` before the existing Navigate touch |
| 10 | same class, `from the waypoint's details sheet on the Maps tab, ...` (:350) | 4 | same |
| 11 | same class, `from the details sheet opened in Records, ...` (:395) | 4 | same |
| 12 | same class, `from a Records row, ...` (:362) | 5 | `assertRowNavigateNotDirections`: "Navigate to Creek pin" exists, "Directions to Creek pin" does not |
| 13 | same class, new test `from a Records All row, Navigate targets that waypoint on the Maps tab` (:371-384) | 6 | Through the All chip by a real touch: no Directions, then a real touch on the row's Navigate starts `WaypointNavigation("wp-1")` and the HUD aims at the creek |
| 14 | `CartographyEntryMapBubblesTest` (in `AvailabilityScreenMapBubblesTest.kt`), the snapshot-only waypoint test (:650-654) | 3 | Was `MAP_BUBBLE_DIRECTIONS_TAG` `assertExists()`; now a real touch on it, asserting the `geo:` URI. Stronger: presence plus what the tap does |

**Removed with the behaviour they asserted:** the `geo:` intent assertions in tests 1, 4 and 5 above, and the helper `registerFakeMapsApp` in `RecordDetailsSheetTest` (its only callers were those tests). They asserted what Directions did on rows 1, 4 and 5, which no longer have it. The `geo:` intent itself is still asserted where Directions remains: test 14 (row 3), `a planned trip's bubble shows its row's lines and its Directions` (`AvailabilityScreenMapBubblesTest.kt:399-411`, row 7, unchanged) and `DirectionsIntentTest` (unchanged).

**Weakened: none.** Each flipped assertion is the same node lookup with the opposite expectation, as Amendment 1 rules, and no rows 3, 7 or 8 assertion was touched except test 14, which got stronger. The four Directions-referring classes not edited were checked: `AvailabilityScreenLayoutTest:917` (row 8, trip row, still asserts Directions displayed), `ReferenceTrailBubbleScreenTest:223` (a trail bubble has no Directions, unrelated), `DirectionsIntentTest` and `INaturalistObservationIntentTest` (intent builders, unrelated to the rows) (read by grep).

## Failing first and the revert checks

All runs are CI `workflow_dispatch` runs with the same filter of six classes (`*AvailabilityScreenMapBubblesTest;*CartographyEntryMapBubblesTest;*AvailabilityScreenWaypointNavigateTest;*AvailabilityScreenWaypointFlowTest;*RecordDetailsSheetTest;*RecordsFilterChipsTest`, read from the job log's `TESTS:` line). For each: the "Build, test, publish APK" job log was read first for compile errors (no Kotlin `e:` lines; `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran; the debug APK artifact was uploaded), then the `unit-test-report` JUnit XML was read. Every run's XML holds 98 tests in six classes (23 + 9 + 15 + 27 + 21 + 3), matching the log's "98 tests completed". The one skip is `AvailabilityScreenWaypointFlowTest`'s long-standing `@Ignore` (`:260-261`), not this work's.

Each revert ran on its own scratch branch cut from `6f0406ccd`, one edit each. The forward worktree was never edited by a revert: r1 to r3 by the earlier session (how it restored, I could not determine, but the forward branch head is still `6f0406ccd`, clean, with `if (!content.hasDetails) {` present at `MapFeatureBubble.kt:94`, checked after all four runs); r4 by me in a separate worktree, so nothing needed restoring.

| Run | Commit | Edit | Compile | Result | Each failure one this edit can cause? |
|---|---|---|---|---|---|
| Tests first, 41005290913 | `8ea3c49e0` | (none: the new tests over the old code) | clean | 16 failed of 98 | Yes. All 16 are `assertDoesNotExist` finding a Directions node (`map-bubble-directions`, `record-details-directions`, or "Directions to ..."), which is the old code still drawing it. The 16 are exactly the union of r1's 3, r2's 8 and r3's 5, no overlap and none left over. Tests 13 and 14's new parts: 13 failed (on its Directions check), 14 passed, as expected for behaviour that already existed |
| r1, 41006010621 | `b3489bc08` | `MapFeatureBubble.kt`: `if (!content.hasDetails)` to `if (true)` (the waypoint bubble's Directions always drawn) | clean | 3 failed | Yes. All three find `map-bubble-directions`: MapBubbles `a waypoint's bubble offers no Directions` (:396), Navigate's Maps-tab bubble (:328) and entry-map bubble (:704). Nothing on the sheet or rows failed. **Bites** |
| r2, 41006014190 | `ede1d7f1c` | `RecordDetailsContent.kt`: a button tagged `RECORD_DETAILS_DIRECTIONS_TAG` put back in the sheet's actions | clean | 8 failed | Yes. All eight find `record-details-directions`: Navigate's two sheet tests (:350, :395) and six in `RecordDetailsSheetTest` (four through `assertWaypointCreekContent` :237, plus :489 and :611). No bubble or row test failed. **Bites** |
| r3, 41006017073 | `99f84cbb9` | `AvailabilityTripsWaypointsUi.kt`: a Directions icon button put back in `WaypointRow` | clean | 5 failed | Yes. All five find "Directions to ...": WaypointFlow (:368), Navigate's Records row (:362 via :287) and All row (:378 via :287), RecordDetailsSheet's row test (:376), RecordsFilterChips' All row (:481). Both lists (rows 5 and 6) are covered. **Bites** |
| r4, 41007377403 | `a5eb0ecaa` | `MapFeatureBubble.kt`: `if (!content.hasDetails)` to `if (false)` (the snapshot waypoint loses Directions) | clean | 1 failed | Yes. The one failure is the snapshot-only waypoint test (test 14, `CartographyEntryMapBubblesTest`, `on the entry map a kept waypoint's bubble names it from the entry, with no details for a waypoint gone from Records`): "could not find any node that satisfies: (TestTag = 'map-bubble-directions')" at its touch (:653, via `touchCentreOf` :236). The planned-trip bubble test, which uses the same tag on another branch, still passed, as it should. **Bites** |

r2 and r3 put back a stand-in button with an empty `onClick` rather than the original; that is enough for assertions about presence, which is all the flipped tests assert.

## CI on the PR head

Run 41006092790 (pull_request, head `6f0406ccd`): **green**, all six jobs succeeded (Build, test, publish APK; Android lint; Release compiles and packages; Tile Worker; Route-data cutter; No debug-only sources). The unit-test report holds 807 result files, **5906 tests, 0 failures, 0 errors, 24 skipped** (summed from the JUnit XML), with the job's skip-allowlist check passing. Every class this work edited ran there with no failures (counts as in the filtered runs above), and so did the four unedited classes that refer to Directions: `ReferenceTrailBubbleScreenTest` 7, `DirectionsIntentTest` 5, `INaturalistObservationIntentTest` 4, and `every Trip Planner drawer control is reachable` (row 8) in each of the three `AvailabilityScreenLayout*Test` variants. This report's own commit is documentation only; its run is not cited here.

## Calls beyond the dispatch (for the planner to accept or reject)

1. **Two extra doc comments corrected** besides the three Amendment 1 names: `WaypointRow`'s KDoc (`AvailabilityTripsWaypointsUi.kt:255-268`) and `WaypointContent`'s (`MapBubbles.kt:232`), both of which described the removed Directions. Made by the earlier session.
2. **New test `from a Records All row, ...`** in `AvailabilityScreenWaypointNavigateTest`: the dispatch asks for tapping Navigate through the rows, and Records > All had no such test. Made by the earlier session.
3. **The r4 revert** (mine): r1 to r3 covered the three removals; nothing covered the one rule that keeps Directions (row 3), so test 14 had only been seen passing.
4. **`AvailabilityScreenMapBubblesTest.kt` is 804 lines, over the modular rule's 800,** both before (at `21d846b9e`) and after; this change edits it with no net change in length (read with `wc -l`). The rule says split before adding to anything over 800 lines. Nothing was added in net, so it was not split; flagged rather than decided. `RecordDetailsSheetTest.kt` went from 823 to 799.
5. **The completion report's path** is `...-completion-report.md`, as the planner's pickup message named it; the PR body had said `...-completion.md` and is corrected.

## Not verified

- **No device check has run.** The S22 steps are in the PR body. Nothing here was seen on a phone.
- **That Back from a Records All row's Navigate returns to Records** is not asserted: the new test 13 stops at the HUD. The Records > Waypoints row has its Back test (`Back from a navigation started in a Records row ends it and returns to Records`, `AvailabilityScreenWaypointNavigateTest.kt:436`); the All row uses the same `WaypointRow` and the same `navigateFromRow` (first stop, row 6), so its Back is inferred, not tested.
- **How the earlier session restored files after r1 to r3** could not be determined. What was checked is the result: the forward branch is clean at `6f0406ccd` with the forward change present.
- **What a host passing no `onNavigate` shows** (tests only; every production host passes one, per the first stop) is now an empty action row on the sheet and the row; no production path reaches it, and no test pins it.

## S22 steps

In the PR #444 body.
