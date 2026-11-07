# Dispatch 2026-11-06-04, Amendment 1: a ring around the icon bar's locate row (completion report)

Hazel Cairnwright (coder, Opus), 2026-11-06 (Pacific). RECORD -2186. Branch `journal-locate-outline`, cut from origin/pre-main
1f6901a11 (checked against the remote). PR #506 to pre-main, ready, not merged.

The owner, to "The journal map's locate button is one row of the shared icon bar, which already has a thin outline as a
whole. What do you want?": "(b) Ring it on both maps" (the option: "A 1 dp Bark/cream ring around the locate row on the
journal map and the Maps tab, so both bars stay the same").

## Where it is drawn

The locate control is the "Center on my location" row of `MapIconBar` (`ui/map/MapIconBar.kt`), a `MapBarIconButton`.
`MapIconBar` has two production callers:

- the Maps tab, `CompactMapIconCluster.kt:58` (portrait, landscape and the landscape L, fullscreen or not; one call);
- the journal entry's map, `CartographyEntryReportScreen.kt` (about line 625), only while that map is full screen.

Both draw the same row, so the one line gives both the ring.

## What landed

- `ui/map/MapBarIconButton.kt` (172 to 183 lines): an optional `ring: BorderStroke?`, default `null`. When it is set, a
  circle the size of the bar's round badges (`MAP_ICON_BAR_FILL_DIAMETER`, 36 dp, centred, 6 dp in) is drawn with that
  border inside the button's face, under the icon. It has no click or pointer input, so the 48 x 48 touch box is unchanged.
  It dips with the icon on a press, as the badges do.
- `ui/map/MapIconBar.kt` (225 to 232 lines): the locate row passes `ring = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)`.
  That is #505's Re-center outline, the colour the selected map type chip's outline uses. No hex is copied. The bar's own
  outline, its fill, the other rows, sizes, places and opacity are unchanged.
- `app/src/test/.../ui/map/LocateRowRingTest.kt` (new, 223 lines): five cases.

## Evidence

All runs are on CI. No Gradle was run on the laptop.

- **Red before.** Run 42338309084 (workflow_dispatch, filter `*LocateRowRing*`, tests only, 45867485). 5 tests, 4 failed,
  as predicted: on both maps, day and night, "a #3B2E24 (or #EDE3D0) ring is drawn; the edge reads [...]". The edge read
  only the bar's outline composite and the bar's fill. The touch case passed, as predicted.
- **Green after.** PR run 42339187561 at fe2f49c9, every job success. Its JUnit XML, compared by task, class and test name
  with pre-main's latest run (42337576510 at 1f6901a11, this branch's base):

  | Run | Cases | Pass | Skip |
  | --- | --- | --- | --- |
  | pre-main | 6846 | 6822 | 24 |
  | this PR | 6851 | 6827 | 24 |

  No case is only on pre-main. The five `LocateRowRingTest` cases are only on this PR. No case changed status. Pre-main's
  artifact has 1001 XML files, this PR's 1002.
- **What was measured** (the cases' MEASURED lines): on both maps, day and night, every side of the ring reads the ruled
  colour exactly for 3 px, starting 18 px in. At xxhdpi that is ceil(1 dp) wide at exactly 6 dp. The locate row stayed
  48 x 48 dp. The reset-orientation row beside it carries no ring on any side.
- **Revert check.** Throwaway branch `journal-locate-outline-revert` at 75efc514: fe2f49c9 with the one `ring = ...` line
  deleted (`git diff --stat`: one file, one deletion). It was made in a separate worktree, so the forward file was never
  edited. A copy was saved first anyway; afterwards the forward file matched the copy, and origin/journal-locate-outline
  still carries the line.
  - Run 42339188735, filter `*LocateRowRing*`. Its build log has no `e: file` compile error, and the test task ran ("5 tests
    completed, 4 failed"; the XML is timestamped 2026-11-07T04:41:27 UTC, inside that run).
  - The four failures are the ones this revert can cause: "Maps tab, day, locate row, left side: a #3B2E24 ring is drawn;
    the edge reads [#7E7368 x3, #F5F1E5, ...]", the same for the journal map, and the same at night for #EDE3D0.
  - The touch case passed.
- **Coordinate touches.**
  - The journal map: real touches at five points across the locate row (centre, and 8% in from each side, inside and outside
    the ring). Each asks for the location once (`getCurrentLocation`).
  - The Maps tab: the existing coordinate touches in `AvailabilityScreenMapIconStackTest` pass on this PR's run, unchanged:
    the locate row's centre, 12 dp toward the map, 11 dp toward the screen edge, 12 dp above and 12 dp below. Its semantic
    `the locate-me icon calls onLocateMe` is skipped, as on pre-main.
  - These checks pass the same before and after the ring, by design: they guard the touch area and are no evidence of the
    ring itself.
- **Scan.** `python3 -I ~/.forager-private/scan.py stdin-diff` read 0 on every count on every push.

## Device check steps for the planner

Device-only: Robolectric's pixels are its own renderer's. On the S22, with the app's Night Mode off:

1. Maps tab: screenshot the icon bar. The locate row (crosshair) has a thin round ring inside it, about the size of the green
   Add badge. Zoomed in, sample it: #3B2E24, about 4 px wide (3.75 px per dp). The other rows have none, and the bar's own
   outline is as before.
2. Tap the ring's edge and the row's corners: the map centres on you, as before.
3. Open a journal entry with a map, tap the map to go full screen: the same ring on its locate row, sampled #3B2E24. Tap it:
   the map centres on you.
4. Turn Night Mode on (Settings) and repeat 1 and 3: the ring samples #EDE3D0.
5. Landscape, Maps tab (the L): the ring as in 1, on the locate row.

## Disclosure

### Confirmed and inferred

- Confirmed from code: one row draws locate on both maps; `MapIconBar` has two production callers; the ring reuses
  `onSurface` at 1 dp, as #505.
- Confirmed by CI: the ring's colour, width and place on the drawn pixels on both maps, day and night, in portrait; none on
  the reset row; red before, green after, revert red for its own reason; touches reach the row.
- Inferred, not tested: landscape, the L and the Maps tab's fullscreen. The row takes no orientation input, so they draw the
  same row; no case of this test composes them.

### Could not determine

- How the ring reads over real map tiles, and beside the bar's outline, on a phone. That is device-only (steps above). The
  phone was not touched.

### Premises that were wrong

- The first amendment's premise, that the journal map has its own locate button in `CartographyEntryReportScreen.kt`. It is
  the shared bar's row. Reported as a stop, and corrected by the owner's "(b) Ring it on both maps". The corrected base
  (1f6901a11 or later) matched the remote.

### Decided beyond scope

- **The ring's shape and size:** a circle at the badge diameter (36 dp). The owner's words were "ring" and "1 dp Bark/cream".
  The circle is the bar's existing round shape (the Add and Record badges, `MapBarHighlight.BADGE`). A rounded square would
  instead follow the press highlight. If the owner wants a different shape or size, it is one constant.
- The ring is a parameter on `MapBarIconButton`, not a branch inside it, so no other row changes. `1.dp` is written in place,
  as #505 did, rather than shared.
- Files checked against nav-card-kind-line (#498), next-turn (#500), revert-chip-colours (#488) and the other open PR
  branches before each edit: none touches `MapIconBar.kt`, `MapBarIconButton.kt` or the new test.
  `docs/audits/README.md` was not touched.

# Amendment 2: the ring only as landscape navigation's re-center (Hazel, a fresh session)

The owner's ruling (prompts/preserved/2026-11-06-04-amendment-2.md on records-after-173): the ring only in landscape while
navigating when re-center is needed, replacing the pill there ("Ring replaces the pill"); tapping it re-centres; no ring
anywhere else. The tests (fdf22dd85) and code (da61c9dc4) were pushed by an earlier session that was stopped before it
reported. Its PR run, 42344323056, failed. This section covers that failure, the fix and the evidence.

## Why run 42344323056 failed

No compile errors: neither test job's log has an `e:` line. 6836 tests ran, 14 failed, and the same 14 failed in both the
main job and the Street-map-deleted job. They fell into two groups:

1. **One new test: `LocateRingReCenterLandscapeTest`, "real touches across the ringed locate row...".** The touch at the
   centre passed. The touch at (0.08, 0.5) failed with "expected:<2> but was:<1>".
   - Cause, confirmed by measurement on the fixed run: in the landscape L, the minimise handle's tap box ("Hide map
     controls", 20 dp wide, one row tall, centred on the locate row) covers the row's outer 12 dp. The row spans 8..56 dp and
     the handle 0..20 dp. The sample sat 3.8 dp in, so under the handle. The handle is composed after the bar, so it wins
     that overlap.
   - This layout is the owner's ruling (c), continuation -172 (`AvailabilityMapIconCluster.kt`, `MapIconBarHandles.kt`).
     While navigating, the bar is fixed to the left (-955).
   - Inferred, not observed: the touch hid the controls rather than doing nothing.
   - So the test was wrong, not the build.
2. **13 existing tests pinned Re-center in landscape navigation**, which is the old behaviour the owner changed. Each failed
   with "could not find any node ... TestTag = 'return-to-route'":
   - `ReCenterOutlineLandscapeTest`: 3 tests (#505's outline in landscape)
   - `RouteCardRecenter{Landscape, TabletLandscape, S22Landscape, S22LandscapeFont20, ShortLandscape, ShortLandscapeFont20}Test`:
     D17 (beside the L) and D27 (icon alone)
   - `RouteCardRecenterCoversNoCard{,AfterSunset}Test`: D34
   - `RouteCardRecenterCoversNoCardWideTest`: D34's counter-case
   - `RouteCardRecenterLeaveRoomTest`: D32
   The earlier session's tests-first commit had not converted them.

## What was fixed (f0c743e99, tests only; no app code changed)

- `LocateRowReCentreTouches.kt` (new, a shared test helper) holds five real touches across the locate row:
  - the centre;
  - 8% in from the top, the bottom and the inboard side;
  - on the outer side, 2 dp inboard of the handle's measured box, or 8% in where no handle overlaps.
  Each touch must bring the navigation view back (`restoreRequestId` + 1).
- `LocateRingReCenterTest` uses that helper. Its claim is unchanged: before each touch the ring is there and the pill is
  not; after it the camera follows and the ring is gone.
- The 13 tests now check Amendment 2's claim, at the same windows, stop counts and peek/expanded walk:
  - Re-center does not show;
  - the locate row stays clear of the cards and the search bar;
  - real touches across the locate row re-centre.
- `ReCenterOutlineLandscapeTest` now extends `LocateRingReCenterTests` and also reads the ring's pixels at 915 x 412, day and
  night. The outline itself is still read on the pill in portrait and on the icon alone.
- **The claims on the cards are kept unchanged:**
  - D32: unnarrowed where the icon fits;
  - D32/D38: both cards narrowed at 640 x 360 font 2.0 with the S22's insets;
  - D38: "Nothing moves".
- No test was skipped or loosened, and no allowlist was changed. Every test whose claim changed was renamed to say so; the
  by-name list is below.

## Evidence

- **Full CI on f0c743e99: run 42380446880, all 8 jobs green.**
  - 6836 tests: 6812 passed, 24 skipped, 0 failed.
  - pre-main's latest run (42337576510 on 1f6901a11) had 6822 tests: 6798 passed, 24 skipped.
- **By name against pre-main:** no test changed status, and the 24 skipped tests are the same.
  - 13 names exist only on pre-main. They are the 13 renamed tests above.
  - 27 names exist only on this branch: the 13 renamed tests, the 9 `LocateRingReCenter*` tests and 5 `LocateRowRingTest`
    cases (Amendments 1 and 2, not yet on pre-main).
- **Revert checks.** Each revert was a one-line edit on its own throwaway branch from f0c743e99, run with the filter
  `*LocateRing*;*ReCenterOutline*;*RouteCardRecenter*;*LocateRowRing*`.
  - Each build log shows BUILD SUCCESSFUL for compilation and no `e:` line.
  - Each run had 34 tests, which is every test in those classes.
  - Every failure message belongs to its own edit.

| Rule | Revert (one line) | Run | Result |
| --- | --- | --- | --- |
| The ring's condition | `locateRinged = false` (CompactMapIconCluster.kt) | 42380490966 | 7 of 34 failed, all "a #3B2E24 / #EDE3D0 ring is drawn" (landscape day, night, fullscreen, before a touch). The no-ring, portrait and pill cases passed |
| The pill's absence | `returnToRouteShown = isNavigating && !navigationFollowing` (CompactMapBottomChrome.kt) | 42380493111 | 17 of 34 failed, all "Re-center does not show" (expected 0, was 1) across every landscape window |
| The tap's routing | `onResumeTracking()` in place of `if (isNavigating) onReturnToRoute() else onResumeTracking()` | 42380495623 | 12 of 34 failed, all "a touch at (0.5, 0.5) on the locate row brings the navigation view back" (expected n+1, was n) |

- After the checks, the forward change was confirmed still present in the PR worktree: `locateRowStandsInForReCenter(` is in
  `CompactMapIconCluster.kt`, the tree is clean, and the reverts lived only in other worktrees.
- **Scan:** `git diff origin/pre-main HEAD | python3 -I ~/.forager-private/scan.py stdin-diff` read 0 on every count before
  every push, the throwaway branches included.

## Device check steps (S22)

Device-only: Robolectric reports no insets, and its pixels come from its own renderer.

1. Rotate to landscape and start a navigation (Return). The camera follows; the locate row (crosshair) has no ring and no
   Re-center shows.
2. Pan the map away. The locate row gets a thin round ring, sampling #3B2E24 by day. No Re-center pill appears anywhere,
   with or without a route card open.
3. Tap the ringed crosshair near its centre and toward its inner side. The camera re-centres on the navigation and the ring
   goes. Pan away again and the ring comes back.
4. Note what a tap on the ring's outer edge does (the side toward the screen edge). The minimise handle owns the outer 12 dp
   there (see below), so it may hide the controls. Report which.
5. Fullscreen landscape: repeat steps 2 and 3.
6. Portrait, navigating, pan away: Re-center shows as before, with its outline; no ring on locate. Tap Re-center: it
   re-centres.
7. Not navigating, in both orientations, and the journal map: no ring.
8. Night Mode on: repeat steps 2 and 6. The ring and Re-center's outline sample #EDE3D0.

## Disclosure

### Confirmed and inferred

- Confirmed from the logs: the failed run had no compile errors, and its 14 failures were the two groups above.
- Confirmed by the fixed run's printed bounds: the handle's box covers the locate row's outer 12 dp in the L.
- Inferred from code: the handle took that touch and hid the controls.
- Confirmed from code (`ReCenterOnLocate.kt`, `CompactMapBottomChrome.kt`):
  - "Landscape" is `MapIconClusterState.landscape` (rail and punch-hole edges present), the same flag D17 uses, so a tablet
    in landscape counts.
  - The ring and the pill's absence come from one function.
  - The tap goes through Ruling D's existing `onReturnToRoute`.
- Inferred from code: with the pill gone in landscape navigation, D17's "beside the L" place, D27's icon alone and D34's
  word-covers-card choice are no longer reached on the Maps tab. They were landscape-only, and the portrait nav card does not
  overlap the word. The code is still there; nothing was removed.

### Could not determine

- How the ring and the missing pill look and feel on the phone, and what a tap on the ring's outer arc does there (steps
  above). The phone was not touched.

### Premises that were wrong

- The dispatch's state matched: origin/journal-locate-outline was da61c9dc4; the worktree was clean with nothing unpushed;
  pre-main had not moved (1f6901a11).
- The stopped session's test premise was wrong: it assumed every point across the 48 dp locate row is the row's. In the L,
  the outer 12 dp belongs to the minimise handle, under a ruling (c) that predates this PR.

### Decided beyond scope, and questions for the owner

- **The outer arc of the ring hides the controls.**
  - The ring is drawn 6 dp in from the row's edge, so its outer 6 dp, at the row's middle, lies under the handle's box.
  - A tap there most likely hides the bar instead of re-centring. This is unchanged from before (the locate row's outer
    sliver was always the handle's in the L), but the row is now the re-center button there.
  - Not changed. The options are the owner's:
    - (a) leave it;
    - (b) narrow the handle's box in landscape navigation so it ends at the bar's edge;
    - (c) move the handle off the locate row while the ring shows.
- **The cards keep a gap for a pill that no longer shows.**
  - D32/D38 narrow the route card and the navigation card from the L's side by Re-center's 48 dp icon plus gaps
    (`routeCardLeaveRoomPx`, `RouteCardPlacement.kt`), whenever navigating in landscape. In narrow windows (640 x 360 at font
    2.0 with the S22's insets) that is about 73 dp left empty.
  - Kept, and the tests still pin it: Amendment 2 did not rule on it, and removing it would change what a user sees.
  - Options: (a) keep the gap; (b) let both cards take the strip's width again in landscape navigation.
- **Tablet landscape counts as landscape** (the planner's reading, recorded as open to the owner). On a tablet, Re-center
  used to fit with its word at bottom centre, and now the ringed locate row stands in there too. The test is renamed to say
  so (`RouteCardRecenterCoversNoCardWideTest`).
- Test files only. Checked against nav-card-kind-line (ea4b6e7e8), next-turn (5d39c9f01) and route-card (c8ac2519d): none of
  them changes any file this PR touches. `docs/audits/README.md` was not touched.
