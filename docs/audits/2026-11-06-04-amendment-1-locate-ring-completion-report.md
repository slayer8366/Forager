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

# Amendment 3: the pill where it fits (Hazel, a fresh session)

The owner (prompts/preserved/2026-11-06-04-amendment-3.md on records-after-173): "Tablets keep the pill". On the first pass I stopped
because D17's condition was not a fit test. The owner then chose "(b) Pill where it fits" (the option: "A real fit test: where the
Re-center word fits at bottom centre without covering a card, the pill shows there and there's no ring; where it doesn't fit, the
ring"), relayed by the planner. "Leave it" (the ring's edge under the handle) and "Keep the gap" need no code.

## D17's condition, as found

- D17 is `reCenterBesideTheL` (`RouteCardPlacement.kt:241`, called at `AvailabilityCompactMapUi.kt:716`): the route card showing
  and resting on the navigation card, in landscape navigation. It measures nothing.
- The only fit test is D34, `reCenterWordCoversCard` (`RouteCardPlacement.kt:274`): Re-center's word at its own place, bottom centre,
  against the route card and the navigation card.
- Before this change the pill went beside the L when either held (`CompactMapBottomChrome.kt`). pre-main's
  `RouteCardRecenterTabletLandscapeTest` pinned the pill beside the L on a tablet whenever the route card was open.

## What was built (ed15feb32, 7493d03a1)

- `ReCenterOnLocate.kt`:
  - `reCenterWordFitsAtBottomCentre(word, cards)`: D34's word rect overlaps no card. A word not yet measured counts as not fitting,
    so Amendment 2's ring holds until the measurement exists.
  - `locateRowStandsInForReCenter` and `reCenterPillShown` take that answer: the ring, and no pill, only where the word does not fit.
  - `reCenterGoesBesideTheL`: a pill whose word fits stays at bottom centre, including where D17 would have put it beside the L.
- `CompactMapBottomChrome.kt` computes the answer once. It writes it through `SideEffect` to a new field,
  `MapIconClusterState.reCenterWordFitsAtBottomCentre`. `CompactMapIconCluster.kt` reads that field for the ring.
- The locate row's tap is unchanged. While navigating it calls `onReturnToRoute`, as it did before Amendment 2 (Ruling D).
- **`AvailabilityCompactMapUi.kt` was not changed.** For #507: this branch touches no route-card file.

## Figures: where the word fits

Measured on run 42405188355, a throwaway build that forced the pill to bottom centre in landscape. No insets unless stated.

| Window | Word at bottom centre (dp) | Cards' left edge (dp) | Result |
| --- | --- | --- | --- |
| 1318 x 824, tablet | 565..673 | 894.5 | fits: pill |
| 1280 x 800, insets 25 / 48 | 534.5..642.5 | 808.5 | fits: pill |
| 915 x 412, phone | 363.3..471.7 | 492.3 | fits: pill |
| 823 x 384, S22 size | 317.3..425.7 | 400.3 | covers: ring |
| 823 x 384, with the S22's insets | 306..414.3 | 352.3 | covers: ring |
| 640 x 360 (font 1.0 and 2.0) | 226..334.3 / 175.3..336 | 288 / 193 | covers: ring |
| 823 x 384, fullscreen (run 42409624489) | 355.7..467.3 | 476.3 | fits: pill |

- The route card rests on the navigation card at the same width, in its column. So opening it never changed the answer at any
  window above, at 1, 3 and 6 stops, peek or expanded.
- The pill never rose for the card: their horizontal spans never overlapped.

## Evidence

- **Tests first: run 42405924782 on dd33240d0.**
  - 6868 tests, 14 failed. The build log has no `e:` line.
  - All 14 failures were the new or changed tests: "Re-center shows, its word fitting at bottom centre", "no ring", or no
    `return-to-route` node.
  - The 14, by class:
    - `LocateRingReCenterTabletLandscapeTest`: 4
    - `LocateRingReCenterWidePhoneLandscapeTest`: 4
    - `ReCenterOutlineLandscapeTest`: 3
    - `RouteCardRecenterCoversNoCardWideTest`: 1
    - `RouteCardRecenterLandscapeTest` (915): 1
    - `RouteCardRecenterTabletLandscapeTest`: 1
- **Run 42407642764 on ed15feb32 failed one test.**
  - The test: `LocateRingReCenterLandscapeTest`'s fullscreen case, which still read Amendment 2's ring at 823.
  - Measured on run 42409624489:
    - in fullscreen the rail leaves and the navigation card moves from 396.3 dp to 476.3 dp;
    - the word, at 355.7..467.3 dp, clears it by 9 dp.
  - So the build was right under the ruling and the test's claim was not. It was rewritten to read the pill (7493d03a1). See the
    question below.
- **Full CI on 7493d03a1: run 42410272354, all 8 jobs green.**
  - 6868 tests, 0 failed, 24 skipped. pre-main's latest run, 42337576510 on 1f6901a11, had 6846 tests and 24 skipped.
  - By name, no test changed status, and the skipped tests are the same.
  - `RouteCardRecenterCoversNoCardWideTest` and `ReCenterOutlineLandscapeTest` are back to pre-main's names and claims. So against
    pre-main:
    - 9 names exist only on pre-main: Amendment 2's renames.
    - 31 names exist only on this branch: Amendments 1, 2 and 3.
- **Revert check: run 42407661820**, on a throwaway branch from ed15feb32.
  - The one-line edit: `val reCenterWordFits = false` in `CompactMapBottomChrome.kt`.
  - The filter: `*LocateRing*;*ReCenterOutline*;*RouteCardRecenter*;*LocateRowRing*`.
  - The build log shows `compileDebugKotlin`, BUILD SUCCESSFUL, and no `e:` line.
  - 42 tests ran; 14 failed. They are the same 14 as the tests-first run, each with this edit's own message.
  - The forward change was confirmed still present in the PR worktree afterwards. The revert lived only in its own worktree.
  - The fullscreen test was changed after this check, so the revert never ran against it. Under the reverted build it read the
    ring and passed, which is Amendment 2's behaviour.
- **Scan:** `git diff origin/pre-main HEAD | python3 -I ~/.forager-private/scan.py stdin-diff` read 0 on every count before every
  push, the throwaway branches included.

## Device check steps (S22 and tablet)

These are device-only. Robolectric reports no insets, and the result can turn on a few dp: 9 dp in fullscreen at the S22's size.

1. **S22**, landscape, navigating, panned away, not fullscreen. Robolectric shows the ring and no pill. Note which shows on the
   phone. Session 9 (b1-n-closed) saw the word sit clear of the navigation card there, so the phone may show the pill instead.
2. **S22**, the same in fullscreen. Robolectric shows the pill at bottom centre and no ring. Note which shows on the phone, and
   whether it changes on entering and leaving fullscreen.
3. **S22**: wherever the pill shows, tap it, then tap the locate row. Each re-centres. Wherever the ring shows, Amendment 2's steps
   apply.
4. **Tablet** (the owner's J6 tablet), landscape, navigating, panned away: the pill with its word at bottom centre and no ring on
   locate.
   - Open a route (3 stops). The pill stays where it is, peek and expanded.
   - Tap the pill, then the locate row. Each re-centres.
5. **Tablet**, fullscreen: the same as step 4.
6. Portrait, on both devices: the pill as before, no ring.
7. Night Mode: steps 2 and 4. The pill's outline samples #EDE3D0, and no ring shows.

## Disclosure

### Confirmed and inferred

- Confirmed from the code: D17 is not a fit test, and D34 is.
- Confirmed by measurement under Robolectric: the figures in the table.
- Confirmed by the runs: the tests failed for this reason before the code, the code passes, and the revert fails the same 14.
- Inferred from the code: Re-center beside the L (D17, D27 and D34's place) is now reached on the Maps tab only by a pill that does
  not show. Those code paths remain; nothing was removed.

### Could not determine

- What the S22 and the tablet show. The phone was not touched.
- Whether the one-frame gap through `SideEffect` is ever visible. Robolectric settles before reading. The answer is computed whenever
  the cards are measured, not only once the user pans away, so it should already be current when the pill or ring appears. This is
  inferred, not observed.

### Premises that were wrong

- The planner's first reading ("D17's condition" = "can't fit at bottom centre"): D17 does not measure fit. This was reported, and
  ruled as (b).
- The state matched on arrival (87e20f9d0, clean). pre-main (82d4a091a) was merged in with `-m` as c4a0d3e71.

### Decided beyond scope, and questions for the owner

- **A 915 dp landscape phone shows the pill, not the ring.** The fit test is not tablet-only. Its word fits there, so Amendment 2's
  ring is gone at that width. The tests were changed to read the ruling.
- **At the S22's own size, fullscreen changes the answer under Robolectric**: the ring out of fullscreen, the pill in fullscreen, by
  9 dp. This follows the ruling. The question: is a switch between the ring and the pill on entering fullscreen acceptable, or should
  one answer hold for both? No code is needed if it stands.
- `docs/audits/README.md` was not touched.
- One coder per file, checked against `origin/pre-main...` for locate-jump (#508, 54e4829ff), route-card (c6d7fb352), next-turn
  (7a5ad452f) and nav-card-kind-line (097ca1add): none of them changes `ReCenterOnLocate.kt`, `CompactMapBottomChrome.kt`,
  `CompactMapIconCluster.kt`, `MapIconClusterState.kt` or the test files here.

# Amendment 4, built: Re-center and the locate ring take the map chrome's outline (Hazel, a fresh session)

The owner (prompts/preserved/2026-11-06-04-amendment-4.md on records-after-173, RECORD -2186), on the route card's outline: "Yes
that's it, but it'll still same as re-center. If re-center is different, then fix that too"; to the locate ring: "Yes, same
outline". Build go: "Build it". Base checked on arrival: journal-locate-outline at 7e5ed4785 as the dispatch states, pre-main
(82d4a091a) already contained.

## What changed (761c3ebf2)

- `ReturnToRoutePill.kt`: Re-center's `border` is `mapChromeOutline()` (`MapChromeOutline.kt`: 1 dp in `mapIconStackBorderColor()`,
  Bark at 40% by day, white at 40% at night), in place of `BorderStroke(1.dp, onSurface)`. One line, plus its comment and the
  `BorderStroke` import it no longer needs. The pill and the icon alone are the same surface, so both changed.
- `MapIconBar.kt`: the locate row's `ring` is `mapChromeOutline()` while `locateRinged`, in place of `BorderStroke(1.dp, onSurface)`.
  The three imports Amendment 2 added for that line (`BorderStroke`, `MaterialTheme`, `dp`) went with it, so the file's imports are
  pre-main's again.
- Nothing else: when the ring or the pill shows, where, their sizes, the 36 dp ring circle and every touch area are unchanged.

## Tests whose claims changed

- New helper `app/src/test/.../ui/map/MapChromeOutlineRead.kt`. The outline is translucent, so a test reads the ruled colour (written
  out: `0x663B2E24`, `0x66FFFFFF`) composited over the fill just inside the line on the same row or column, within 4/255 a channel.
- `ReCenterOutlinePortraitTest` (4 outline cases) and `ReCenterOutlineLandscapeTest` (2): until now they read solid #3B2E24 / #EDE3D0.
  They now read the map chrome's outline, the same width (exactly ceil(1 dp) on straight edges; ceil(1 dp) or one less on the round
  ends, which until now allowed "at most"). Renamed from "a 1 dp Bark / pale outline" to "the map chrome's 1 dp outline".
- `LocateRingReCenterLandscapeTest`, the two ring cases: the same change of colour, the ring still found on the badge circle 6 dp in,
  now ceil(1 dp) or one less wide (until now "at most"). Renamed from "a 1 dp Bark / pale ring" to "the map chrome's 1 dp outline as
  its ring". Its touch test reads the ring through the same helper.
- Every "no ring" case (`LocateRingReCenter*`, and `LocateRowRingTest`'s four): until now they looked for the solid `onSurface`
  colour only, which the new ring would never have matched, so they would have passed with the ring drawn everywhere. They now read
  the ring's place (6 to 7 dp in, a pixel either side) as the fill beside it, within 12/255, so a ring of any colour fails them.
  12/255 rather than 4/255 because a fading press highlight shades the row about 1/255 a pixel (red run, below); the faintest ring
  steps about 70/255 from the fill.

## Evidence

- **Tests first: run 42440600462 on e8883baa1** (throwaway `hazel-lo4-red`, filter
  `*ReCenterOutline*;*LocateRingReCenter*;*LocateRowRingTest*`). 30 tests, 10 failed:
  - The 6 Re-center outline cases, each reading the old line, e.g. "portrait, day, left edge: the map chrome's outline is drawn,
    #ABA398 over the fill #F5F1E6; the edge reads [#3E3228, #3B2E24, #3B2E24, #F1EDE1, ...]", and at night [#E8DECB, #EDE3D0, #EDE3D0,
    ...] against #797979 expected.
  - The 3 ringed cases (day, night, the touch test), each finding the solid ring at 18 px in ([..., #3E3228, #3B2E24, #3B2E24, ...])
    and no #ABA398 / #797979 there.
  - 1 that was the test's fault, not the code's: `LocateRingReCenterTabletLandscapeTest`'s touches beside the pill, a no-ring case,
    read "11 px in reads #E0DBD0" against a fill of #D9D3C9: a smooth shade from a fading press highlight, no ring. The no-ring check
    was too strict (exact fill); it became the 12/255 step above (70922d900, before the fix). That check was not re-run red on its
    own; it passed on the green run on every no-ring case, as it must, and a ring of any colour steps far past it.
- **Full CI on 761c3ebf2: run 42442214975 (PR #506), all 8 jobs green.** 6868 tests, 0 failed, 24 skipped. Measured there, at xxhdpi:
  Re-center's edge #ABA398 over #F5F1E6 by day and #797979 over #1F1F1F at night, 3 px from its edge; the icon alone #ABA399 / #949494;
  the ring #ABA398 / #797979 for 3 px from 18 px in (6 dp), 2 px on one touch-test side where a shade sits under it.
- **By name.** Against #506's previous green (42410272354 on 7493d03a1): no test changed status; 8 names only there and 8 only here,
  the 8 renames above, nothing else. Against pre-main's latest run (42337576510 on 1f6901a11; pre-main's head 82d4a091a has no CI
  run): no shared test changed status; 15 names only on pre-main (Amendment 2's 9 renames and the 6 Re-center renames here) and 37
  only on this branch (Amendments 1 to 4); skipped tests the same 24.
- **Revert: run 42442242031 on 756baf6dc** (throwaway `hazel-lo4-revert`, same filter). The one-line edit: Re-center's
  `border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)`. The job log shows
  `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran with no `e:` line; 30 tests, 6 failed: exactly the 6 Re-center outline
  cases, each reading the solid #3B2E24 / #EDE3D0 line, which this edit is what draws. The ring cases passed, as they must: this edit
  leaves the ring alone. The file was restored from a copy saved before the edit; `git status` was clean against 761c3ebf2 after it,
  and `border = mapChromeOutline()` was present. The ring's half was not reverted on its own (one revert, as the dispatch asks); its
  red-run failures are the evidence that its tests bite.
- **Scan** read 0 on every count before every push. **One coder per file:** the six files here share none with route-card's own
  changes or nav-card-kind-line's.

## Device check steps (S22)

1. Day, portrait, navigating, pan away: Re-center's outline looks like the navigation card's and the icon bar's (a soft brown hairline),
   not the darker solid line of build 4844. Robolectric's pixel at its edge: #ABA398 over a #F5F1E6 fill, over a flat test map.
2. Night Mode, the same: a soft light-grey hairline (white at 40%), not the cream line. Robolectric: #797979 over #1F1F1F.
3. Landscape navigation, not fullscreen, pan away: the ring on the locate row has the same hairline as the bar's own edge, day and
   night. Tap it: the camera follows and the ring goes (unchanged).
4. Fullscreen landscape and the journal map: no ring, as before.
Over real map imagery the outline's colour depends on the map under it, so these are judged by eye against the bar's own edge, not
by a sampled hex.

## Disclosure

### Confirmed and inferred

- Confirmed by the runs: the new tests failed on the solid line for that reason, pass on the outline, and the revert fails the 6
  Re-center cases with its own message after a clean compile.
- Confirmed from the code: both lines now call `mapChromeOutline()`, the function the bubbles, the icon bar and the navigation card
  call, so they cannot drift from it.
- Inferred: the outline reads fainter than the solid line on the phone. That is the ruling, but nobody has seen it yet.

### Could not determine

- How it looks on the S22 over real tiles; the phone was not touched.
- Whether the revert of the ring's half alone would fail only its own tests; not run (one revert asked).

### Premises that were wrong

- None found: Re-center was at `ReturnToRoutePill.kt:119` as stated, and the ring at `MapIconBar.kt:201` drew the same solid line.

### Decided beyond scope, and questions for the owner

- The "no ring" checks were tightened, as described above, because with a translucent ring the old ones could no longer fail. They
  pass the same before and after the fix, by design.
- No question for the owner from this build. The visual step (1 to 3 above) is the one thing left.
