# Dispatch 2026-11-06-04: an outline around the Re-center button (completion report)

Hazel Cairnwright (coder, Opus), 2026-11-06 (Pacific). RECORD -2186. Branch `recenter-outline`, cut from origin/pre-main
26a5c5955 (checked against the remote before starting). PR #505 to pre-main, ready, not merged.

The owner: "Add real quick: an outline around the re-enter button"; to how it should look, "Same as the chips" (the option
read "1 dp, Bark #3B2E24 by day, pale #EDE3D0 at night, like the selected map type chip's outline"); to where it goes, "Its
own small PR".

## What landed

- `app/src/main/java/com/zynergylabs/forager/app/ui/availability/ReturnToRoutePill.kt` (149 lines): the pill's one `Surface`,
  in `ReturnToRoutePillSurface`, gets `border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)`. That is the colour
  source the selected map type chip's outline uses (`ui/map/MapModeChips.kt`, `mapModeChipBorder`: `selectedBorderColor =
  MaterialTheme.colorScheme.onSurface`, `selectedBorderWidth = 1.dp`). `onSurface` is `Bark` in the light scheme and `Cream`
  (#EDE3D0) in the dark one (`ui/theme/Theme.kt`, lines 44 and 101). No hex was copied. The border is drawn inside the
  surface's bounds, so its fill (`surfaceContainer` at the 80% map chrome alpha), size, place and touch area are not changed.
- `app/src/test/java/com/zynergylabs/forager/app/ui/availability/ReCenterOutlineTest.kt` (219 lines, new): eight cases in two
  classes, described below.

### Where the button is drawn

`ReturnToRoutePill` has one production caller, `CompactMapBottomChrome.kt` (the `MapPopUp` at about line 152), and
`CompactMapBottomChrome` has one caller, `AvailabilityCompactMapUi.kt` (about line 699). That one composition is the Maps tab's
bottom chrome in **portrait, landscape and fullscreen** alike: the pill has no branch on orientation or fullscreen. It has two
places and two forms:

- at the bottom centre, with its word (portrait, landscape, fullscreen);
- beside the L at the bottom (D17, landscape navigation with the route card on the navigation card), with its word or, where
  the word does not fit, its icon alone in a 48 dp square (D27).

Every form is drawn by the same `ReturnToRoutePillSurface`, so all of them get the outline from the one line.
`ReturnToRouteWordProbe` measures the pill's content without a surface and is never drawn, so the border cannot change what it
measures.

## Evidence

All runs on CI; no Gradle on the laptop.

- **Red before the fix.** Run 42332037102 (workflow_dispatch, filter `*ReCenterOutline*`, commit 16b88fcd, tests only): 8
  tests, 6 failed, as predicted. The six outline cases each failed with "a #3B2E24 outline is drawn" (day) or "a #EDE3D0
  outline is drawn" (night), the edge reading only the fill (#F5F1E6 by day, #1F1F1F at night on the Maps tab). The two touch
  cases passed, as predicted (see below).
- **Green after.** PR run 42333465627 at bb58a4ca, every job success. Its JUnit XML, compared by class and test name with
  pre-main's latest code run, 42315368330 (at 3542a0c3; pre-main's head 26a5c5955 differs from it only in
  `docs/audits/README.md`): pre-main 6838 cases (6814 pass, 24 skip); this PR 6846 (6822 pass, 24 skip). None only on pre-main;
  the eight new `ReCenterOutline*` cases only on this PR; no case changed status. Both artifacts carry both test tasks' XML
  (1004 files from `testDebugUnitTest`, 7 from `testDebugUnitTestOlderSdks` on the PR run).
- **What was measured** (the cases' own MEASURED lines, PR run): on every form, day and night, the top and bottom edges at
  the centre column read the ruled colour exactly for 3 px from the edge, which is ceil(1 dp) at xxhdpi; the round ends at
  the middle row read it for 2 px from 1 px in, the outermost pixel softened by anti-aliasing on the curve. The pill's height
  stayed 48 dp, and the icon alone 48 x 48 dp.
- **Revert check.** Throwaway branch `recenter-outline-revert` at 0858f3f8: bb58a4ca with the one `border = ...` line deleted
  (`git diff --stat`: one file, one deletion), in a separate worktree, so the forward file was never edited; a copy of it was
  saved first anyway, and afterwards the forward worktree's file matched the copy and origin/recenter-outline still carried
  the line. Run 42333434671, filter `*ReCenterOutline*`. Its build log has no `e: file` compile error, and the test task ran
  ("8 tests completed, 6 failed"; the XML is timestamped inside that run, 2026-11-07T03:25 UTC). The six failures are the
  ones this revert can cause and nothing else: "portrait, day, left edge: a #3B2E24 outline is drawn; the edge reads
  [#F5F1E7, #F5F1E6, ...]", and the same for night (#EDE3D0 against #1F1F1F), landscape, and the icon alone.
- **Coordinate touch.** Both classes touch Re-center at five real points across what it draws (`ACROSS_ROUND`) on the real
  Maps tab, and each touch raises the navigation view's `restoreRequestId` by one. This passes the same before and after the
  outline, by design: it guards that the outline does not change the touch area, and is no evidence of the outline itself.
- **Scan.** `python3 -I ~/.forager-private/scan.py stdin-diff` on every push: 0 on all four counts each time (tests push,
  fix push, revert push, report push).

## Device check steps for the planner

Device-only: Robolectric reports no real insets, and its pixels are its own renderer's. On the S22, with the app's Night Mode
off:

1. Maps tab, start navigating (a waypoint's Navigate), then drag the map: Re-center shows at the bottom centre.
2. Screenshot. Zoomed in on the pill's top edge, at its middle: a 1 dp line (about 4 px, at the 3.75 px per dp
   `RouteCardBarRig.kt` records for the S22; its outermost pixel may be softened) sampled #3B2E24, then the pill's fill. The round ends show the same line.
3. Tap Re-center: the navigation view comes back, as before. The pill's size and place match a pre-main screenshot taken the
   same way.
4. Turn Night Mode on (Settings), repeat 1 and 2: the line samples #EDE3D0.
5. Landscape, navigating with a route card open on the navigation card: Re-center beside the L, word or icon alone; the
   outline as in 2 (day) and 4 (night).

## Disclosure

### Confirmed and inferred

- Confirmed from code: one surface draws every form of the pill; one production caller; the chip outline is `onSurface` at
  1 dp; `onSurface` is Bark by day and Cream at night.
- Confirmed by CI: the outline's colour and width on the drawn pixels in portrait and landscape on the real Maps tab, and on
  the icon alone, day and night; red before, green after, revert red for its own reason.
- Inferred, not tested: fullscreen. The pill takes no fullscreen input, so fullscreen draws it the same way; no case composes
  the Maps tab in fullscreen. The icon alone was composed on its own inside `ForagerThemeWithNoSignal`, not reached on the
  Maps tab beside the L; the place is covered by `RouteCardRecenterTest`, unchanged here.

### Could not determine

- How the outline reads on the phone over real map tiles: device-only (steps above). The phone was not touched, per the
  dispatch.

### Premises that were wrong

- None found. The dispatch's base (26a5c5955 or later) matched origin/pre-main.

### Decided beyond scope

- The 1 dp is written as `1.dp` in the pill, as the chip writes it, rather than shared through a new function: sharing it
  would have meant editing `MapModeChips.kt`, which open PR #488 (revert-chip-colours) changes. The colour is reused through
  the theme's `onSurface`, not copied.
- The journal entry's fullscreen map has its own locate button (`CartographyEntryReportScreen.kt`). It is not the map's
  Re-center (no "Re-center" label, a different composable) and was left as it is.
- Files checked against PRs #498 and #500 before editing (`git diff --name-only origin/pre-main...origin/<branch>`): neither
  touches `ReturnToRoutePill.kt` or the new test file. `docs/audits/README.md` was not touched.
