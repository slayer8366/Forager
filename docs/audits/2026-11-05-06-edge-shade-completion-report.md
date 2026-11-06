# Dispatch 2026-11-05-06, completion report: see-through map windows lose their shadow and gain the map-chrome hairline (Hazel Cairnwright, Phase 2)

Built from records-after-173 at 0d6c2832d:
- the dispatch, `prompts/preserved/2026-11-05-06-edge-shade.md`;
- Amendment 1, `prompts/preserved/2026-11-05-06-amendment-1.md` (A1 to A3, B1 to B3, C1 to C4);
- the Phase 1 report, `prompts/preserved/2026-11-05-06-phase1-report.md`, re-checked where built on;
- RECORD -945, -946, -966, -967, -2153 and -2154.

Later rulings, from the planner by message, each the owner's answer to a stop of mine:
- **-2155:** the notice is "App's own notice"; the centre-pin row is "Top and both sides".
- **-2156:** the tail is "Outline around both"; the notice gets the one-row layout only; the action's colour is measured, not fixed.
- **-2160:** the outline test may count the outline over the map ("Allow it"); the tail is a "Clean triangle".
- **-2164, corrected in place:** K-2a, the map-side rule at the strip's end.

**Base.** pre-main ee0651fe0, checked against the remote at the start. pre-main has since moved to 3a3d85089 (PRs #492 and #493), with no file in common with this branch. Every PR run tests the merge, so its counts include their tests.

**C2's base** (a second session of mine, from the interim hand-back at `prompts/preserved/2026-11-05-06-interim-report.md`, records-after-173 a295c406a): PR #494, the trip bubble, merged into pre-main as 7357524b7, and pre-main was then a0535ece1 with its audit row, both checked against the remote. Merged into this branch at **09ee656f0**, a merge commit with parents 2544e080e and a0535ece1, keeping both sides, never a rebase. No file is changed on both sides since ee0651fe0, and `git merge-tree` wrote a clean tree [observed]. The merge's own run, 42151105727, had no compile errors: 6730 tests, 0 failures, 0 errors, 24 skipped, every job a success [observed]. The 50 more are #494's, by its report's counts (6695 against 6645) [inferred].

Marks: **[read]** is source read at a file and line; **[observed]** is CI output or pixels I read; **[inferred]** is reasoning.

## Summary

- **No elevation shadow** on any see-through window over the map, and the map-chrome hairline (`MapChromeOutline.kt:34`) where a window lacked one. In Robolectric every window reads the 0.8 composite to its edge, by day and at night: every sample from 2 dp in within 2/255, and the hairline at the edge within 3/255.
- **The bubble's card and tail are one shape,** filled once and outlined once (T-B). The tail is a clean triangle whose base lies along the card's edge (J-B). The line runs along both sides to the tip, with none where the tail joins the card.
- **The notice along the bottom is the app's own:** Material's one-row layout, rule for rule, held to Material's own side by side. Over the map it has no shadow and has the hairline; off the map it keeps Material's look.
- **Failing first.** Each change was seen red for its own reason before it was built:
  - 21 reds in 42119202488;
  - 4 for the clean triangle in 42126975492.
  - Two design faults in my first tests were found by their run and fixed first.
- **Revert checks.** Six, each on its own branch, none with a compile error, each failing only its own cases. One of them found the outline test's day blind spot at the strip's end, which K-2a closes.
- **CI.** Full CI green at b02687d2e (run 42134399792): 6680 tests, 0 failures, 0 errors, 24 skipped, every job a success. After C2, full CI green at 57adcd4eb (run 42154201578): 6732 tests, 0 failures, 0 errors, 24 skipped, every job a success, no compile errors.
- **C2,** the bubble's entry-count menu, waited for PR #494 and is done (`MapFeatureBubble.kt:208-210`). Its new test failed first for its own reason (42152576917). Its revert check, a seventh, failed only its two cases with no compile error (42154248791).

## What landed

On `edge-shade`, PR #495 into pre-main, pushed at every step. It stays a draft until the head is green with C2 done, and is then marked ready. It is not merged: that waits for the owner.

| commit | what it does |
|---|---|
| 0f7c6ffea | B1, tests first: `SeeThroughEdgePixelsTest` (portrait, day and night), `SeeThroughEdgePixelsLandscapeTest`, `SeeThroughEdgeSupport.kt`, `MapChromeSnackbarParityTest`, a stub `MapChromeSnackbar` (Material's own), and `MapChromeColourPixelsTest` :207-210 corrected |
| cd2a745e0 | Test-only: that commit's two design faults fixed ("Failing first") |
| 8ba253226 | A1, A2: the change |
| f96201a67 | Tests first for -2160: O-1 in `MapChromeOutlinePixelsTest`; the tail's geometry as the clean triangle, and its straight-sides case |
| b21a95ccc | J-B: the tail's base along the card's edge |
| 5c9c97cb1 | Test-only: the straight-sides case reads 2.5 px outside the tail (accepted, -2164) |
| b02687d2e | Test-only: K-2a in `MapChromeOutlinePixelsTest` |
| 09ee656f0 | Merge of pre-main a0535ece1: PR #494 (as 7357524b7), PR #493 and their audit rows (C2's base, above) |
| d61a1e3a2 | C2, tests first: new `SeeThroughEdgeMenuPixelsTest` (day and night); `EdgeShadeRig` takes the Journal's entries, none by default |
| 57adcd4eb | C2: the bubble's entry-count menu, `MapFeatureBubble.kt:208-210` |

## Every surface changed

All of these are over a map.

| Surface | Site | Before | Now |
|---|---|---|---|
| Every map bubble (the sighting, feature and crowded-stack bubbles: `MapBubbleShell` and its three callers) | `MapBubble.kt`, new `MapBubbleOutline.kt` | 6 dp shadow, no line, tail filled under the card | No shadow. Card and tail one shape, filled once (the card's Surface is transparent) and outlined once inside its edge. The tail's base lies along the card's edge, 4 dp either side, clear of the 20 dp corners. |
| Add tile | `AddActionTile.kt:151` | 4 dp | None (it had the hairline) |
| Map type panel (Maps tab and the entry map) | `MapTypePanel.kt:114` | 4 dp | None (it had the hairline) |
| Legend chip | `MapLayersSheet.kt:174` | 2 dp | None (it had the hairline) |
| Icon bar (Maps tab and the entry map) | `MapIconBar.kt:209, :220` | 2 dp | None (it had the hairline) |
| Control pill | `TrailheadControls.kt:221, :232` | 2 dp | None (it had the hairline) |
| Cluster container | `AvailabilityMapIconCluster.kt:616` | 2 dp | None (it had the hairline) |
| Search pill over the map | `AvailabilitySearchUi.kt:208` | 2 dp | None (A2). Its tuck shadow on the card (`RoundedMapCard.kt:152-163`) is untouched. |
| Compass strip, portrait navigation info, landscape navigation card | `RoundedMapCard.kt`, `mapCardSurface` | 2 dp `graphicsLayer` | Layer removed, since its only job was the shadow. Their outline was already there. |
| Centre-pin OK/Cancel row | `CentrePinLocationPicker.kt` (`CentrePinConfirmSurface`, `CENTRE_PIN_ROW_OUTLINE_SIDES`), `CompactMapPickers.kt` (`centrePinRowOutlineSides`) | 4 dp, no line | No shadow. The hairline on its top and both sides, none on its bottom (-2155). The rail's line on its rail side while the rail shows. |
| Waypoint-name dialog | `AvailabilityMapOverlaysUi.kt:224` | 4 dp, no line | None, plus the hairline |
| Save-route dialog | `RouteDialogs.kt:76` | 4 dp, no line | None, plus the hairline |
| Menus: quick settings, the journal entries chip's list | `MapQuickSettings.kt:246`, `JournalEntriesChip.kt:141` | Material's 3 dp, no line | `shadowElevation` 0 and `border` the hairline, through new `MapChromeMenuEdge.kt` |
| Menus that follow an over-map flag: the entry report's menu, the Month menu, the species suggestions | `CartographyEntryReportScreen.kt:473` (`entryMapShown`), `SearchDropdownSections.kt:152` (`overMap`), `SpeciesSearchField.kt:178` (`suggestionsOverMap`) | as above | As above over a map; Material's 3 dp and no line elsewhere, as before |
| The bubble's entry-count menu (C2), the list a bubble opens from "In N journal entries" | `MapFeatureBubble.kt:204` (`KeptInEntries`) | Material's 3 dp, no line | `shadowElevation` 0 and `border` the hairline, through `MapChromeMenuEdge.kt` with `overMap = true` (`:208-210`). It is drawn only where the Maps tab passes `onOpenEntry` (`:178`), so always over the map. |
| The notice along the bottom | `ScaffoldSnackbarHost.kt:83`, new `MapChromeSnackbar.kt` | Material's `Snackbar`: 6 dp, no line, and a parameter for neither | The app's notice. On the Maps tab: no shadow, the hairline. Off it: Material's 6 dp and no line. |

**How the Material pieces are done** [read, material3 1.5.0-alpha26 bytecode]:
- `DropdownMenu-IlH_yew` and `ExposedDropdownMenu-kbRbctU` both take `shadowElevation` and `border`.
- Both `Snackbar` overloads call `Surface` with `shadowElevation = SnackbarTokens.ContainerElevation` and `border = null`, and take neither as a parameter, hence -2155.

**File sizes** (`wc -l`):
- `CartographyEntryReportScreen.kt` is the largest touched file, at 782 lines with this change (778 before).
- The new files are 25 to 223 lines.

## Shared components (A3)

Where a component is shown both over a map and not:
- **The search pill** (`SearchEntryBar`) is the same pill on List, Seasonal and Journal, solid there. It keeps its 2 dp there (`pillCorners != null && !overMap`).
- **The notice** has one host on every tab. Off the Maps tab it keeps Material's 6 dp and no line.
- **The Month menu, the species suggestions and the entry report's menu** each follow their own over-map flag, and are unchanged where no map is drawn.

Changed wherever they appear, all over a map:
- the icon bar and the map type panel, on the Maps tab and the Cartography entry map (`CartographyEntryReportScreen.kt:621, :652`);
- `MapBubbleShell`, for every bubble kind on every map host;
- `mapCardSurface`, for the strip and both navigation cards;
- `CentrePinConfirmSurface`, the Maps tab's pin picker. Its overlay variant has no production caller and takes the default sides.

Material's dialogs, sheets and drawer have no shadow and are untouched (A3).

## The bubble (RECORD -2156, -2160)

**One shape.** The card's rounded rectangle and the tail are joined into one path (`bubbleShape`):
- it is filled once in the shell's `drawBehind`, and the card's Surface is transparent;
- it is outlined once in a `drawWithContent` over the content, by a stroke twice the hairline's width clipped to the shape (`drawBubbleOutline`).

**The single fill (accepted, -2164).** Before, the Surface filled the card and the tail was drawn under it, so the sliver of tail under the card's edge was filled twice [observed]:
- in red run 42119202488 at night it read 242424 against a composite of 3E3E3D (0.96, not 0.8);
- by day the double fill, which is lighter, and the band, which is darker, cancelled to within 3/255, so that sample passed by coincidence;
- after the change both themes read the composite (42122210303).

Under the clean triangle the tail's base lies on the card's edge, so no part of the visible tail overlaps the card. The triangle is carried half a corner radius into the card only so the union never meets along one line. One fill covers it, and the join sample, 1.5 px inside the card at the tail's base, reads the composite.

**The clean triangle (J-B).** The base lies along the card's edge, 4 dp either side of where the line from the card's centre to the tip leaves the card's rectangle, kept on that edge's straight stretch clear of the corners.
- Before, the base was square to the tail and centred on the edge. In the tests' bubble one end of it lay outside the card, at (74.9, 375.0) dp, and the outline traced a step of about 2.3 dp on the tail's lower side.
- The prediction, written in f96201a67's message before its run: the step on the second, lower side; the first side failing too from its small change of angle.

## The notice (RECORD -2155, -2156)

**What it is.** `MapChromeSnackbar` is Material's `Snackbar(snackbarData)` with `shadowElevation` and `border` added. It was read from androidx-main `Snackbar.kt` and checked against the shipped bytecode, whose constants are 600, 30, 16, 8, 6 and 8 dp:
- the 12 dp margin;
- the message in `bodyMedium`;
- the action as a `TextButton` coloured `actionColor`, in `labelLarge`;
- the dismiss as an `IconButton` with a tooltip and "Dismiss";
- `LegacyOneRowSnackbar`'s layout, which is what 1.5.0-alpha26 runs: `ComposeMaterial3Flags.isSnackbarStylingFixEnabled` is false, and its static initialiser never sets it [read].

**Not built:** the action on a line of its own. Material uses it only for `actionOnNewLine = true`, which the host never passes (-2156).

**The action's colour, measured** [observed, 42122210303 and later, `MapChromeSnackbarParityTest`]:
- Material draws the action's text in **#D0BCFF, `inversePrimary`**, not `primary` (#2E5339). Both sides were read from the colour their text was laid out with.
- The host passes only `actionContentColor = primary` (`ScaffoldSnackbarHost.kt:87`). Material colours the button with `actionColor` (the `TextButton`'s `textButtonColors(contentColor = actionColor)` in androidx-main `Snackbar.kt`).
- So the host's comment at :49-51, "the action `primary`", does not describe what is drawn. Unchanged here: the notice keeps Material's behaviour, and the parity test holds the two to the same colour.

## Failing first (B1)

**Predicted before the first run** (PR #495's description):
- every window red from its band;
- the bubble, centre-pin row and notice red from the missing hairline too;
- uncertain whether Robolectric's band from a 2 dp shadow reaches 2 dp in;
- parity green over the stub.

**42116693802 (0f7c6ffea)** [observed]. No compile errors; 6667 tests, 21 failures, 0 errors. 19 of the 21 named a band or a missing hairline as predicted. The other two meant my tests were wrong:
- **The add tile's and the map type panel's side by the icon bar** lies over the bar, not the map: right 5 dp in read E2DDD1 against a map composite of DCD8CD. Now each pixel is held to the panel's fill over what lay under it before the panel opened, under the scrim, and the scrim model is checked at the bar's middle.
- **The rail's side in landscape** was taken from where the window sat. That is right for the narrow card and wrong for the wide row. Now it is read from the rail's bounds.

**42119202488 (cd2a745e0)** [observed]. No compile errors; 6667 tests, 21 failures, 0 errors, 24 skipped. Every failure names a band or a missing hairline:
- **The portrait classes,** `SeeThroughEdgePixelsLightTest` and `DarkTest`, failed all 8 cases each. For example, the bubble by day read left 2 dp in E1DDD3 against E7E3D8, and the top edge E5E1D6 against the hairline's A29B90; the pill read 2 dp in EAE9E9 against F0F0EF.
- **The landscape pair** failed both cases each.
- **`MapChromeColourPixelsLightTest`'s bubble** read (0.925, 0.906, 0.863) against (0.961, 0.945, 0.898).
- **Passed before:** `MapChromeColourPixelsDarkTest`'s bubble. At night over the screen's dark background the band is within 2/255, so the corrected claim bites by day only. `SeeThroughEdgePixelsDarkTest` holds the band at night, over grey.
- **Passed over the stub, as predicted:** the three parity cases. Their bite is revert check r4.

**42126975492 (f96201a67, tests over the stepped tail)** [observed]. No compile errors; 6670 tests, 4 failures, 0 errors: the two tail cases in each theme.
- The step on the second, lower side, as predicted: its 15% inner sample read the bare map, B5B4B0, where the line belongs, and its 35% and 60% outer samples read the old tail.
- The first side failed too, as predicted.
- In the same run the outline cases passed under O-1.

**C2, 42152576917 (d61a1e3a2, tests first, filtered)** [observed]. No compile errors. The filter was `*SeeThroughEdgeMenuPixels*`, `*SeeThroughEdgePixels*`, `*JournalEntriesChipTest` and `*JournalEntriesOnMapPortraitTest`: 8 suites, 51 tests, 2 failures, 0 errors. The two are the new class's cases, as predicted in d61a1e3a2's message before the run, and every other case passed.
- **How the new class reads a menu.** The list opens in a popup window of its own, which the screen's root capture used above does not show. So it reads the whole screen, every window as composited (Robolectric's `UiAutomation.takeScreenshot`, which renders each window with the hardware renderer, shadows included, at its `getLocationOnScreen`) [read, robolectric 4.16.1 `ShadowUiAutomation` and `HardwareRenderingScreenshot` bytecode]. The list's bounds come from `SemanticsNode.positionOnScreen`, in the same frame. Each pixel is held to the fill at 0.8 over what that pixel showed before the list opened, since the list lies over the bubble and the map.
- **By day:** every edge's hairline sample read the fill: left E3DFD4 against the line's A29B90, top EEE9DD against A8A195, right E5E1D6 against A29B90, bottom E3DFD4 against A29B90. The band was there too: left 2 dp in E2DDD3 against E7E3D8, and the same 2 to 5 dp in on the other sides.
- **At night:** every hairline sample read the fill (left 3A3A39 against 8B8B8B), and the band showed 2 to 5 dp in (left 2 dp in 393838 against 3E3E3D).
- **The samples landed on the list.** Those 6 dp in matched the composite over the map at the sides (E7E3D8) and over the bubble at the top (F1EDE0). So the failures are the missing line and the shadow, not a misplaced read.
- **The job that runs every test with the Street map deleted** (it ignores the filter) logged "6678 tests completed, 2 failed, 24 skipped", the same two cases [observed, Gradle's console line; that job uploads no XML].

## Forward runs

Every count below is from the JUnit XML. Each PR run tests the merge with pre-main.

| Run | Commit | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|---|
| 42122210303 | 8ba253226 | 6668 | 2 | 0 | 24 | +1 test, `AvailabilityScreenCrowdedIconsSettingTest`, from #492. The 2 failures are the outline test's walk at the strip's end (below). |
| 42129154367 | b21a95ccc | 6670 | 1 | 0 | 24 | The straight-sides case at night, 1.5 px outside the first side: AFAEAA against B5B4B0, the pixel reaching the side's anti-aliasing. Fixed by 5c9c97cb1. |
| 42132074372 | 5c9c97cb1 | (6656) | | | | Cancelled by my next push partway through its tests: 965 suites. Not evidence. |
| **42134399792** | **b02687d2e** | **6680** | **0** | **0** | **24** | **Every job a success.** +10 tests, `FindWayToWaypointEndLogTest`, from #493, the only per-suite difference from 42129154367. |

## The outline test at the strip's end (RECORD -2160, -2164)

**What failed.** `MapChromeOutlineLandscapeDayTest` and `Landscape780DayTest`, in their walk round the strip's end against the rail [read, `MapChromeOutlinePixelsTest.kt`, `isLine` and `assertWalkFindsLine`]:
- `isLine` counts a pixel as line when it is within 0.07 of the outline over a fill and more than 0.12 from that fill.
- At that corner the arc lies outside both fills, over the map. So by day the line there reads 0.376, which is exactly 0.4 × Bark (0.188) + 0.6 × the map (0.502).
- With the shadow it read 0.341 to 0.349. The failing square had passed on one blend pixel of fill and line, at 0.672, which the shadow had darkened into the window 0.617 ± 0.07. With the shadow gone it reads 0.696.

**O-1 (allowed, -2160).** The bare map near the corner, outside every piece, 24 dp left of the rail and 24 dp below the strip, is a third reference beside the two fills. It reads (128, 128, 128), luminance 0.502, in all four captures. The outline over it is 0.376 by day and 0.701 at night. The bare map pixel is 0.126 from 0.376, so it does not count.

**r5 found a blind spot (42129142994).** The edit: the two corner arcs on a shared right side not drawn. No compile errors; 36 tests; 2 failures.
- The Night variants failed at the arc squares, 2 of 75.
- **The Day variants passed, 0 of 75, with no arc drawn.** In r5's day capture, the strip's rounded fill edge against the map gives blend pixels at 0.57 to 0.66. These sit inside the fill-based windows, and held 60 and 59 of the 62 arc squares. The map reference held 0: a blend of fill and map never goes below the map's 0.502.
- So by day the walk could not tell a missing arc from a drawn one, before O-1 as after.

**K-2a (-2164, corrected).**
- **The rule.** The arc squares from 15° to 69° of each corner count a pixel as line only if it lies on the line's side of the bare map pixel by more than half the gap between the map and the outline over it, read from the capture. By day that is darker than 0.439; at night lighter than 0.602.
- **Elsewhere.** The arc's ends keep the O-1 rule, where the arc joins the rail's line (0° to 12°) or the strip's top and bottom over a fill (72° to 90°). No other part of the walk changed, and no other walk.
- **Why it was scoped.** Measured on the forward captures before building: applied to every arc square, the rule broke 16 of 62 by day and 12 at night, at 0° to 9° and 75° to 90°, where those lines lie over a fill.

| Run | the strip's end, "N of 75" without a line (day 823 / day 780 / night 823 / night 780) |
|---|---|
| With the shadow, 42119202488 | 0 / 0 / 0 / 0 |
| Shadow gone, before O-1, 42122210303 | 2 / 2 / 0 / 0 |
| O-1, 42126975492 | 0 / 0 / 0 / 0 |
| r5 under O-1, 42129142994 | 0 / 0 / 2 / 2 |
| **K-2a, forward, 42134399792** | **0 / 0 / 0 / 0** (predicted 0 in all four) |
| **r6, the same edit as r5 under K-2a, 42134397087** | **38 / 38 / 40 / 40**, each at the arc squares, first at (2224, 34) and (2095, 34) by day (predicted 40 / 40 / 43 / 43 from the CI captures, offline; the direction held, and the counts came in 2 to 3 lower) |

**The four squares per run that 15° to 69° drops,** compared with 12° to 72° (12° and 72° on each corner), measured offline on r5's captures: in all four cases all 4 were broken under the 12° to 72° rule, and 0 of 4 are under the O-1 rule they now keep.

**The other walks' blind spot by day, unchanged** (for a separate ruling):
- **"The strip's far end in fullscreen"** (`MapChromeOutlinePixelsTest.kt:534`, `roundedEndRuns(strip), fill, fill`) **[inferred, not measured]**. The same squares on the same 16 dp corners, with fill-only windows, over the same map. A missing arc would leave the strip's fill edge against the map, whose blends fall inside the fill-based window by day as they did in r5. Not measured: no capture with that arc missing exists.
- **The corner points checked by `assertLineNear`** **[inferred, not measured]**: the pill's, the strip card's, the bottom bar's and the rail's rounded corners (`:263, :264, :279, :313, :314, :343, :348, :367, :368`). Each looks for a line pixel in the 3 × 3 round a corner's 45° point with `isLine` against a fill. Where that corner faces the map, a missing line would leave fill/map blends there that can fall inside the window by day.
- **At night** neither has the blind spot [inferred]: the line is lighter than both fill and map. This matches r5's night failures [observed].

## Revert checks (B2, and -2160)

**The runner** (`/mnt/work/scratch/edge-shade/tools/revert.sh`, deleted with scratch at the end):
- one edit per branch `edge-shade-rN`, each in its own worktree, so the coder's worktree was never edited, and the edited file copied first;
- the build job's log read for compile errors before the JUnit XML;
- after each, the forward change confirmed present in the coder's worktree (`git status` clean, each reverted line grepped);
- r1 to r4 filtered to `SeeThroughEdgePixels*`, `MapChromeColourPixels*` and `MapChromeSnackbarParityTest`, 29 tests; r5 and r6 to `MapChromeOutline*`, 36 tests.

| Branch, run, base | Edit | Predicted | Observed |
|---|---|---|---|
| r1, 42122283675, 8ba253226 | the bubble's `shadowElevation = 6.dp` put back | bubble cases only | 6 failures, all bubble cases: the four edges and the tail in both themes, and `MapChromeColourPixels`' bubble in both. By day, left 2 dp in reads C2BEB5 against E7E3D8. |
| r2, 42122297513, 8ba253226 | the bubble's outline draw removed | its hairline samples; the tail's sides | 4 failures: the four edges' hairline samples only (E7E3D8 against A29B90), and the tail's sides |
| r3, 42122310038, 8ba253226 | the card outlined alone (T-A) | the tail case only | 2 failures, the tail case in each theme: its sides, and "where the tail joins" read A29A8F against E7E3D8, a line across the join |
| r4, 42122322888, 8ba253226 | the notice's two-line minimum 64 dp, not 68 | parity's two-line cases | 1 failure: "2 line(s), action false, dismiss false, 360 dp: the whole notice ... Material bottom=92.0.dp, own bottom=88.0.dp" |
| r5, 42129142994, b21a95ccc | the corner arcs on a shared right side not drawn | the four landscape cases at the arc squares | 2 failures, night only. The day blind spot, above. |
| r6, 42134397087, b02687d2e | as r5 | all four at the arc squares | 4 failures, all four, 38 and 40 of 75 |
| c2-r1, 42154248791, 57adcd4eb | C2's three lines removed: the bubble's entry list back to Material's 3 dp shadow and no line | the two `SeeThroughEdgeMenuPixels` cases only, naming the hairline at all four edges and band samples 2 to 5 dp in | 2 failures of 51 (the filter as C2's tests-first run): those two cases only. By day the left edge reads E3DFD4 against the line's A29B90, and 2 dp in E2DDD3 against E7E3D8; at night the left edge reads 3A3A39 against 8B8B8B. |

**c2-r1, said plainly.** The reverted tree is byte-identical to d61a1e3a2's, C2's tests-first commit (both trees e5e418191), so this check re-ran that state from the forward commit. Its failures are the same values, sample for sample, as 42152576917's. It was run by a separate runner in `/mnt/work/scratch/edge-shade-c2/tools/`, on the same rules:
- one edit, on its own branch and worktree;
- the file copied before the edit;
- the build log read for compile errors first (none);
- afterwards the worktree's file restored from that copy, never from git, and found identical to 57adcd4eb's;
- the forward change confirmed present in the coder's worktree and on origin (`MapFeatureBubble.kt:209-210`).

**r1 at night.** The night bubble check failed though it passed before the change. That is a failure this revert produces. The fill is now drawn by the shell and the Surface is transparent, so a shadow put back on the Surface is drawn over the fill, darker than the old band.

Each failure is one its own edit can produce, and none falls in another surface's cases. The branches and worktrees are deleted.

## CI counts

Every count is from the JUnit XML, the build job's log read first for compile errors (none in any run below). Each PR run tests the merge with pre-main.

| Run | Commit | Kind | Suites | Tests | Failures | Errors | Skipped | Jobs |
|---|---|---|---|---|---|---|---|---|
| 42137248165 | 2544e080e | full, before C2 (from the interim report; not re-read in C2's session) | | 6680 | 0 | 0 | 24 | every job a success |
| 42151105727 | 09ee656f0 | full, the merge of pre-main | 978 | 6730 | 0 | 0 | 24 | every job a success |
| 42152576917 | d61a1e3a2 | filtered, C2's tests first | 8 | 51 | 2 | 0 | 0 | the build and the Street-deleted jobs failed on the two new cases only |
| 42154248791 | f9ae7318d (c2-r1) | filtered, C2's revert check | 8 | 51 | 2 | 0 | 0 | as 42152576917 |
| **42154201578** | **57adcd4eb** | **full, after C2** | **980** | **6732** | **0** | **0** | **24** | **every job a success** |

**Against the merge's run, suite by suite,** 42154201578 differs only by the two new suites, `SeeThroughEdgeMenuPixelsLightTest` and `DarkTest`, each 1 test passed. No other suite's counts changed.

**What the new test read** at 57adcd4eb [observed]. The list lies on screen at 24 to 136 dp across and 313.7 to 521.7 dp down, 112 × 208 dp: four 48 dp items and 8 dp of padding above and below, at Material's 112 dp minimum width.
- **By day,** at each edge: the hairline A39A90 against A29B90 (top A9A095 against A8A195), and 2 dp in E7E3D8 exactly (top F1EDE0, over the bubble).
- **At night:** the hairline 8B8B8B at three edges and 7C7C7C against 7D7D7D at the top; 2 dp in 3E3E3D exactly (top 262626).

## Not tested, said plainly

- **The phone's band.** Robolectric draws shadows on another path (Phase 1 report, section 1). These tests show the band gone where Robolectric drew it. That it is gone on the phone, at the phone's width, is the device check.
- **Menus and dialogs** are not read from pixels, except C2's list: they open in their own windows, which a capture of the screen's root does not composite over the map. Their parameters were read from the code, and the device check covers them. The bubble's list of keeping entries is read from pixels over the map by `SeeThroughEdgeMenuPixelsTest`, through the whole-screen screenshot (C2, above). The same read would likely work for the other five menus [inferred], but it was not built for them: it was C2's test only.
- **The notice's tooltip** (a long press on its dismiss) is Material's public `PlainTooltip`, not the internal one Material's snackbar uses. It is not compared.
- **Four surfaces are not read at their edges by the new classes:** the legend chip, the icon bar, the control pill and the cluster container. B1 named the bubble, tile, panel, pill and navigation card. `MapChromeColourPixelsTest` still reads the icon bar with its looser "darkened by 0.90 to 1" rule, which passes with or without a shadow; its comment says so.
- **Fullscreen for the centre-pin row** (its rail side drawn again once the rail has gone) is not exercised.
- **Font scales other than 1.0** for the notice's parity.
- **Tails near a card's corner,** where the clean triangle's base is held clear of the corner. Only the one bubble geometry the screen gives is read.

## Device check (B3), for the planner

Phone time is scarce, so the CI APK from the PR's last green run is ready before the phone is asked for. Day theme first, then Night, each on a plain stretch of Trail Map (flat grey or cream, no lines). Take PNG screenshots and sample them as Phase 1 sampled the owner's crop.

**For each window,** sample a line from the bare map, through the window's edge, to its middle, at mid-height and at mid-width.
- **Pass, the fill:** every sample from 1 dp inside the edge to the middle is within 3/255 of the middle (no step, no band).
- **Pass, the line:** a 1 dp line at the edge (Bark at 0.4 by day, white at 0.4 at night, over the fill), except on the sides the rulings leave open.
- **Pass, outside:** the bare map 10 dp outside the window equals the map 60 dp away, within 3/255 (no cast shadow).
- **Evidence:** the screenshot and the numbers.

Cheapest first:
1. **The search pill and the strip,** at rest. Also: the card just below the pill is still darker than the card further down, the tuck shadow, as before.
2. **A bubble** (tap a waypoint). Also the tail: a line along both sides, straight from the card's edge to the tip, none across where it meets the card, and the fill even there.
3. **The add tile** (+).
4. **The map type panel** (Layers).
5. **A menu:** the strip's three dots.
6. **The notice:** delete a test waypoint from its bubble's details for "Undo", then Undo.
   - Also: its words, action and close button sit where they did.
   - Also: the action's colour, measured here as #D0BCFF.
7. **The centre-pin row:** + then Waypoint. A line on its top and both sides, and one line, not two, where it meets the bottom bar.
8. **The navigation card:** Return, in portrait (the strip) and in landscape (the card on the bottom edge).
9. **Repeat 1 to 8 at Night.**

Observation, not a gate: the owner's crop had the band about 19.5 dp wide at the bubble's sides. If any band remains on the phone, its width and strength are the finding.

## Decided beyond the dispatch's letter

For the planner to accept or reject:
- **The single fill of card and tail.** Accepted, -2164.
- **The outer samples at 2.5 px** (5c9c97cb1). Accepted, -2164.
- **K-2a's angles,** 15° to 69°, one 3° step inside the last angles that held on the forward captures. Accepted, -2164.
- **A grey stand-in map** under every read: (181, 180, 176), from the owner's crop. Over the screen's own dark background a band changes almost nothing at night.
- **Each panel's side by the icon bar read over the bar,** and the rail's side from the rail's bounds (cd2a745e0).
- **The notice's shadow kept off the map,** Material's 6 dp (A3).
- **Comments.** The icon bar's, control pill's and cluster's say their 2 dp darkened the whole fill rather than drew a band, as Phase 1 found for narrow pieces. `MapChromeColourPixelsTest`'s shadow comments are updated, its assertions unchanged.

## Premises that were wrong

- **"The action on a new line where Material puts it"** (-2155). Material does that only for `actionOnNewLine = true`, which the host never passes. Not built (-2156).
- **The host's comment that the notice's action is `primary`** (`ScaffoldSnackbarHost.kt:49-51`). It is drawn in `inversePrimary`. Reported, not changed.
- **The -2160 question to the owner,** "a missing line would still fail it". By day, at the strip's end, it would not have (r5). The planner corrected -2160 in place.
- **K-2's "the forward run stays green"** (-2164 as first written). Wrong as worded (16 and 12 broken squares, measured); corrected to K-2a.

## Stops

All are resolved by the owner's or the planner's rulings:
1. The notice can't be done cleanly. → -2155, S-C.
2. The centre-pin row's sides. → -2155, R-B.
3. The bubble's tail under the outline. → -2156, T-B.
4. An existing outline test fails, and fixing it means changing it. → -2160, O-1.
5. The tail's step. → -2160, J-B.
6. The O-1 revert bites only at night. → -2164, K-2.
7. K-2 as worded would turn the forward run red. → -2164 corrected, K-2a.

**C2: no stop.** Its premises held when checked against the remote: #494 merged as 7357524b7 and pre-main at a0535ece1. The merge needed no judgement: no file in common, a clean tree. The saved edit's anchor in `MapFeatureBubble.kt` was unchanged by #494. #494 adds no shadow, menu or other surface over the map: a grep of its added `app/src/main` lines finds none. Its one new notice, "Stopped navigating: that trip was deleted.", goes through `logDraftSnackbarHostState`, which `ScaffoldSnackbarHost` hosts (`AvailabilityCompactScaffold.kt:829-830`), already the app's own notice on this branch [read].

## Could not determine

- The phone's look of any of this. That is the device check.
