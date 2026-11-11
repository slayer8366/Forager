# Completion report: a failed or loading sightings search no longer replaces the Maps tab

Dispatch: `prompts/preserved/2026-11-11-01-maps-tab-search-failure.md` (on `records-after-173`), with its Amendments A to D.
Known bug 30, audit finding H1. Coder: Hazel Cairnwright. Branch `maps-tab-search-failure`, cut from `origin/main` at
`137b1427c` (the dispatch's stated base, checked against the remote before starting). The planner writes the index row, the
known-bugs change and the RECORD entries; this report touches none of them.

## The owner's words this builds

From the dispatch: H1, **"Map stays, auto retry (Recommended)"**; the fix's speed, **"Urgent, from main (Recommended)"**. From
Amendment C (after Step 1):

- C1 **"Under the search bar (Recommended)"**
- C2 **"Thin line on the search bar (Recommended)"**
- C3 **"One line (Recommended)"**
- C4 **"At once (Recommended)"**
- C5 **"\"Search failed. Check your connection.\" (Recommended)"**
- C6 **"Top of the map (Recommended)"**
- C7 **"Notice stays, no auto retry (Recommended)"**

## Commits

| Commit | What |
|---|---|
| `e38cd5243` | Step 1: the failing tests (`MapsTabSearchFailureTest.kt`, three layouts), and `RealSearchScreenRig.sightingsFailure`. |
| `6c181833a` | Step 2: the fix and its tests. |
| `183388845` | The loading-line test's tag moved to a Box of its own; `SearchClearAndFrameTest`'s spinner positive control turned to the owner's ruling (below, "Decided beyond scope"). |
| this commit | This report. |

## What changed, and where

- `AvailabilityCompactMapUi.kt` (793 to 780 lines): the spinner branch and the error-text branch of the `when` are gone, so
  the map, and the search bar, the bottom bar or rail, the compass strip, the HUD and everything else drawn in that branch,
  stay whatever the sightings fetch is doing. The `when` keeps its `else` only, so the 400-line block under it keeps its
  indentation and the change merges as those lines.
- `MapsSearchFailure.kt` (new, 181): the notice (`SearchFailedNotice`, the existing banner's look: error container at
  `mapChromeFill(..., overMap = true)`, so 0.8, text opaque, "Try again" a `TextButton` at its right end), the fullscreen
  placement of the same notice, the loading line (`SearchBarLoadingLine`: an indeterminate `LinearProgressIndicator`, 2 dp,
  no track, clipped to the bar's pill), the one-line fold (`mapsTabSearchNoticeState`), and the composable that runs the retry
  when signal returns.
- `SignalReturns.kt` (new, 46): `signalReturns(validated)`, plain coroutines, no Android. One value per return of the signal
  after a loss; the first at once, then at most one per `SIGNAL_RETURN_MIN_INTERVAL_MS` (10 s), returns inside that time kept
  as one.
- `AvailabilityViewModelSearchRetry.kt` (new, 30): `retryFailedSearch()`. Both halves failed: the whole search again
  (`refresh`), so C3's "runs both". Only the map's half failed: the sightings again (`onMapTabSelected`). Nothing unless the
  map's half failed. An extension, as the offline maps' functions are, so `AvailabilityViewModel.kt` stays at 795;
  `refresh` and `searchRuns` were made `internal` for it.
- `ScaffoldMapTabCall.kt` (368 to 398): places the notice under the bar after the existing banner, the fullscreen notice at
  the map's top, the loading line on the bar; the icon cluster's top limit follows either notice. The bar column's modifier is
  now a named value (`searchColumnModifier`), shared with the fullscreen notice: the same lines, reindented.
- `AvailabilitySearchRuns.kt`: the ranked list's failure text is a constant, `LIST_SEARCH_FAILED_MESSAGE`, with the same
  words, so C3 folds that failure and nothing else `errorMessage` carries.
- `AvailabilityScreenSearchInputs.kt`: `onRetryFailedSearch` and `networkValidated`, both defaulted (as `onClearSearch` is).
- `AvailabilityScreen.kt` (757 to 760): provides `LocalRetryFailedSearch`, and runs the signal watcher, last in the body.
- `MainActivity.kt` (outside ui/availability, under D1): builds `AndroidNetworkSignal(applicationContext)` beside the
  existing `noSignal`, and hands its `validated` flow and `viewModel::retryFailedSearch` to the search inputs.

## D1: which wiring, and why

The raw `NetworkSignal.validated`, from `AndroidNetworkSignal`, handed to the search screen through
`AvailabilityScreenSearchInputs` from `MainActivity`, where the existing signal source is built. Not `LocalNoSignal`: that
is the map's "no signal" after its 5 s and 30 s delays, and C4 is "At once". Not a composition local in `ui/map`: it would
have added a second file outside ui/availability for the same flow. The screen collects it for as long as it is composed;
each collection registers one network callback with Android and removes it on leaving.

Once per return holds: the retry runs only while the map's half is failed, and a retry that fails waits for the next return.
A flapping signal is held to at most one search per 10 s by `signalReturns`, shown at two levels (below).

## D3 and D4

- D3: when only the ranked list fails and the map's sightings load, the Maps tab shows today's banner, unchanged
  (`when only the list fails and the map's sightings load the Maps tab shows today's banner`). The banner's text on other
  tabs is unchanged; only the constant's name is new.
- D4: **the search bar is not hidden while navigating.** Read: the bar's slot is composed with no navigation condition
  (`AvailabilityCompactMapUi.kt:567` at the base), and its only visibility switch is `AnimatedVisibility(visible =
  !isMapFullscreen())` in `ScaffoldMapTabCall.kt`. Test: `AvailabilityScreenNavigationWordsTest.kt:532` (existing) reads the
  bar's bounds while navigating and asserts the HUD does not overlap it. So C6 applies to fullscreen only. No new navigating
  test was written. `MapChromeOverMapTest`'s stale comment is updated; its assertion is unchanged.

## Failing first, then green

Step 1 (run [43027173891](https://github.com/slayer8366/Forager/actions/runs/43027173891), commit `e38cd5243`): 9 of 9 failed
as predicted, in portrait, short landscape and landscape tablet (the Step 1 report has the messages). The same 9 pass at
`183388845` (run [43044145995](https://github.com/slayer8366/Forager/actions/runs/43044145995): 38 tests in the filter, 0
failed, read from the JUnit XML: 3 x 7 `MapsTabSearchFailure*`, 6 `MapsTabSearchRetryTest`, 4 `SignalReturnsTest`, 7
`SearchClearAndFrameTest`).

The Step 2 tests were written with the fix, not before it, so their evidence of biting is the revert checks below.

Tests added (all through the real screen and real ViewModel, real touches at screen coordinates, except `SignalReturnsTest`):

- In each of the three layouts: the notice under the bar, its words, its fill at 0.8 and the old full-tab text gone; five real
  touches across "Try again" (at 15/85 % across, 25/75 % down, and the centre), each running the search again, then a
  successful one handing the map its sightings; two long-presses on the map just below the notice reaching the map; while
  loading, the 2 dp line ending on the bar's bottom edge within its width, gone when sightings arrive, and the bar's text not
  moving.
- Portrait: both halves fail, one line, "Try again" runs both; only the list fails, today's banner; signal returns, the search
  runs again at once; good signal, 60 s pass, no retry and the notice stays; five returns in one second, one retry, then one
  more when 10 s end, then none; fullscreen, the notice moves to the top and "Try again" works.
- Headless (`SignalReturnsTest`): a connection there from the start is no return; a return is told at once; twenty returns in
  four seconds are told twice, at 100 ms and 10 100 ms; returns 20 s apart each told at once.

## Revert checks

One edit per scratch branch, cut from `183388845`. Each runner saved the file first, made one edit, committed, pushed, ran CI
filtered to the classes named, and restored the file from its saved copy, not from git. Every build log was read for compile
errors before its results: each had 0 `e:` lines and ran `compileDebugUnitTestKotlin`. Each failure below names its own edit.
Afterwards the forward branch was checked: clean, equal to its pushed head, and each reverted line still present.

| Behaviour | Edit | Run | Failure (verbatim, from the JUnit XML) |
|---|---|---|---|
| Map stays | a branch put back: `uiState.sightingsErrorMessage != null -> MapMessage(...)` | [43044279330](https://github.com/slayer8366/Forager/actions/runs/43044279330) | 5 of 7. `missing from the Maps tab after a failed sightings fetch expected:<[]> but was:<[the map, the search bar, the tab bar]>`; `after a failed sightings fetch there is no search bar to touch`; `after a failed sightings fetch there is no search-failed notice`; `before the touch at (0.15, 0.25) there is no Try again`; the long-press test's `Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'search-failed-notice')`. The loading test passed, as it must: the edit put back the failure branch only. |
| Try again | `onClick = {}` | [43044313218](https://github.com/slayer8366/Forager/actions/runs/43044313218) | 3 of 13. `a real touch at (0.15, 0.25) of Try again did not run the search again expected:<2> but was:<1>`; `Try again in fullscreen did not run the search again expected:<2> but was:<1>`; `Try again did not run the ranked list again expected:<2> but was:<1>`. |
| Retry when signal returns | `if (failed) Unit` in place of `retry()` | [43044325912](https://github.com/slayer8366/Forager/actions/runs/43044325912) | 2 of 6. `the search did not run again when signal returned expected:<2> but was:<1>`; `five returns in a second ran the search more than once expected:<2> but was:<1>` (no retry at all). |
| No burst from a flapping signal | `delay(minIntervalMs)` commented out | [43044336570](https://github.com/slayer8366/Forager/actions/runs/43044336570) | 2 of 10. `five returns in a second ran the search more than once expected:<2> but was:<6>`; `twenty returns in four seconds were told more than twice expected:<[100, 10100]> but was:<[100, 300, 500, ..., 3900]>` (twenty values). |
| One line (C3) | `mapsTabSearchNoticeState` returns the state unchanged | [43044349940](https://github.com/slayer8366/Forager/actions/runs/43044349940) | 1 of 6. `both failed and the list's own failure line shows beside the notice`. |
| Loading line | `if (false && uiState.isLoadingSightings)` | [43044362310](https://github.com/slayer8366/Forager/actions/runs/43044362310) | 1 of 7. `while sightings load there is no loading line on the search bar`. |

The full suites of the revert runs were cancelled once their unit-test reports were in: they test nothing the filter does not.

## Full CI

Run [43047043043](https://github.com/slayer8366/Forager/actions/runs/43047043043), unfiltered, at `183388845` (the code this
report describes; this commit adds the report only). Every job passed. Read from the JUnit XML:

- `testDebugUnitTest`: 7538 tests in 1124 suites, 0 failures, 0 errors, 24 skipped. `testDebugUnitTestOlderSdks`: 24 tests,
  0 failures.
- "The app builds and its tests pass with the Street map deleted": 7508 completed, 0 failed, 24 skipped. At Step 1 (run
  43027173891, base plus the 9 Step 1 tests) the same job ran 7486 with 24 skipped, so main's own count there is 7477, and
  7477 + 31 new tests = 7508: no test removed, and no new skip.
- Android lint failed on the first attempt in "Install the Android SDK", before lint ran (`curl: (92) HTTP/2 stream 1 was
  not closed cleanly: INTERNAL_ERROR`); the failed job was re-run and passed. Nothing was changed for it.
- Unrelated tests: none failed. One test outside the dispatch's own failed on the way, `SearchClearAndFrameTest`'s RECORD
  -1525 case (run 43039556746), because its positive control asserted the spinner the owner ruled away; see "Decided beyond
  scope", 3.

## On the phone (S22): what only the phone can confirm

Robolectric reports zero window insets, has no real network, and its map is a stand-in Box, so these are device-only:

1. Maps tab, a search showing. Airplane mode on. Search again from the bar. **Pass:** the map, the bar and the bottom bar stay;
   a red-tinted line "Search failed. Check your connection." with "Try again" appears under the compass strip; the map shows
   through it. While the search runs, a thin moving line runs along the bar's bottom edge and nothing in the bar moves.
   **Evidence:** a screenshot of each.
2. Airplane mode off. **Pass:** the search runs again by itself within a few seconds of the phone showing signal, the notice
   goes and the dots appear, with nothing tapped. **Evidence:** a screen recording.
3. Airplane mode on, search, then tap "Try again" at its left and right ends. **Pass:** each tap shows the line and the notice
   comes back. **Evidence:** the recording.
4. Steps 1 to 3 while recording a track, and while navigating. **Pass:** the recording strip, the HUD and the route stay on
   screen throughout; the notice does not cover the HUD (observation, not a gate: where the HUD replaces the strip the notice
   sits right under the bar, as the existing banner does).
5. Fullscreen, airplane mode on, then search fails (search first, then fullscreen). **Pass:** the notice is at the map's top,
   below the strip, clear of the status bar; "Try again" works.
6. Turn the phone (landscape). **Pass:** the notice sits under the bar, clear of the L and the rail.
7. Long-press on the map just below the notice. **Pass:** the add-action menu opens.

## Confirmed vs inferred

- Confirmed by CI: everything in "Tests added" and the revert table, in the layouts named.
- Inferred from code, not tested: the recording strip and the navigation HUD stay during a failed or loading search (they are
  drawn in the branch that is now always drawn); the notice's height, which carries "Try again"'s 48 dp touch target and is
  taller than the existing banner's; that the notice wraps rather than truncates at large font scales.

## Could not determine

- How quickly Android reports `validated` after airplane mode goes off on the S22: the retry is "at once" from that report.
- Whether the fullscreen notice clears the status bar on the phone (insets are zero under Robolectric).

## Premises that were wrong

- Step 1's premise 6 (`MapChromeOverMapTest` refers to both fields): it refers to `isLoadingSightings` only (reported in Step 1,
  confirmed in D4).
- `SearchClearAndFrameTest`'s RECORD -1525 case assumed the spinner replaces the map, as a positive control; the fix makes that
  false by the owner's ruling.

## Decided beyond scope (for the planner to accept or reject)

1. **`MainActivity` wires the retry callback as well as the flow.** D1 lifts the stop for "giving the search screen the raw
   `NetworkSignal.validated` flow". The screen cannot act on the flow without a way to run the search again, and "Try again"
   needs the same one (C3's "runs both" cannot be met by the callbacks already wired), so the one line
   `onRetryFailedSearch = viewModel::retryFailedSearch` sits beside the flow. Read as part of D1's wiring; named here because
   D1 does not say so.
2. **10 s between automatic retries** (`SIGNAL_RETURN_MIN_INTERVAL_MS`). The first return retries at once (C4); this only
   spaces out the ones after it, for D1's "no burst". An operating limit, not a measurement.
3. **`SearchClearAndFrameTest`'s positive control** changed from "the spinner has replaced the map" to "the map stays while the
   new search's sightings load". The claim it guards (an earlier locate is not the new search's pan target) is unchanged and
   passes. This is a changed assertion in a test outside the dispatch's own, made because its precondition is the behaviour the
   owner ruled away; flagged so the planner can rule on it.
4. **"Try again" when both halves failed runs the whole search** (`refresh`): the ranked list and the sightings, and also the
   rainfall, the forecast and the trip windows that a search runs, as searching again from the bar does.
5. **Not ruled, left as they were, for the planner to take to the owner if wanted:** in fullscreen a retry shows no loading line
   (the bar it sits on is hidden), so the notice goes while the retry runs and comes back if it fails; the existing banner's
   other messages (a mistyped coordinate, a species lookup, the trips, location permission) still show above the new notice,
   so two lines can show in those cases; the fullscreen notice appears at once while the bar's column slides away, so the two
   overlap for the slide.
6. `MapMessage` (`AvailabilityMapOverlaysUi.kt`) has no caller now; left in place, since the dispatch says nothing else
   changes.

## Not verified

- Anything on the phone (above).
- A portrait tablet: inferred to match the portrait phone, not run.
- The 24 skipped tests in the full suite were not examined.
