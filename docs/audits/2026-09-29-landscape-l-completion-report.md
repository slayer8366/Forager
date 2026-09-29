# Dispatch 2026-09-28-160: the landscape icon cluster as an L — coder report

**Status (rewritten at the resume; the first stop, on the disk, is recorded below and was resolved): STOPPED AGAIN on three conflicts with existing behaviour that are the planner's to decide (see "Resumed", section 6). The L is built and its own tests pass; the full suite is red at 13 tests. Everything is on `landscape-l-wip`; nothing is on `journal-redesign`.** Sections below are appended as work lands; nothing above a "Resumed" heading is rewritten.

**Coder session.** The owner's launch prompt names `/model claude-sonnet-5-5`. The session's configured model id is `claude-sonnet-5-5`; the serving model was not independently read, so that is the configured identifier, not a verified one.

**Base.** `origin/journal-redesign` at `9c337f19` (fetched at the start of the session; it is the commit that carries `prompts/preserved/2026-09-29-26.md`). Worktree `/home/zynergy-labs/Zynergy/forager-wt/landscape-l`, branch `landscape-l`.

**Paths** (all under `app/src/main/java/com/zynergylabs/forager/app/ui/`): CMU = `availability/AvailabilityCompactMapUi.kt`, MCU = `availability/AvailabilityMapControlsUi.kt`, MC = `map/MapChrome.kt`.

## Premises re-verified at `9c337f19`

Read, not inferred:
- The landscape switch is `val landscapeCluster = railPortEdge != null && punchHoleEdge != null`, CMU:409; the branch is `ShortLandscapeClusterRow(...)`, CMU:1096; the composable is at CMU:1535. (Dispatch cites CMU:1095-1105 and :1534-1548: off by one line each; same code.)
- The container `Surface` opens at CMU:1025 (dispatch: 1024), `.testTag(MAP_ICON_CLUSTER_TAG)` at CMU:1041. It carries `color = mapIconClusterContainerColor()` (0.6), `shadowElevation = 2.dp`, a 1 dp border, and the measuring `onGloballyPositioned`.
- The bar is `MapIconBar`, MC:382; its `Column` has `padding(vertical = Spacing.xs)` and `spacedBy(Spacing.xs)`, MC:450-453: 5 × 48 + 4 × 4 + 2 × 4 = 264.
- Each row is `MapBarIconButton`'s 48 dp `Box` with `clickable` (MC:726-731), inside the `Surface`'s 24 dp rounded shape.
- The pill is `ControlPill`, MCU:178, a private composable with a `Column` of two `MapBarIconButton`s, the same padding and spacing, filled at `mapIconClusterChildColor()` (0.5).
- `mapIconBarRowAnchorOffset` hardcodes `Spacing.xs` row pitch, MC:90-95; `ADD_TILE_ANCHOR_OFFSET` is MCU:612; its only production caller for the compact map is CMU:1409. `AvailabilityScreen.kt:334` imports `mapIconBarRowAnchorOffset` (used by the Cartography-entry and wide-tree callers; not touched).
- `MapIconBar` has a second caller, `ui/log/CartographyEntryReportScreen.kt:532`, per the dispatch. Not opened or edited by this work.
- The remembered offsets, side and minimised flag are keyed on `landscapeCluster` at CMU:410-441; unchanged by this work.

## Design used (from the dispatch, unmade decisions listed in "Decisions I made" at the end)

- In landscape only: the container `Surface` becomes a plain `Box` (same padding, same measuring, same tag) that draws nothing and consumes no pointer input; inside it a `Column` holds the bar and, 8 dp beneath, a horizontal pill. Both are aligned to the cluster's outer edge.
- The bar gets a landscape row pitch of 0 and no end padding (240 dp); rendered at the standing 0.8 chrome alpha as one layer.
- The pill gets a horizontal form (record, then return, 48 dp thick) at the same 0.8, and the same zero pitch/padding.
- `mapIconBarRowAnchorOffset` gains a defaulted row-pitch parameter; the landscape AddActionTile anchor passes 0.
- Portrait Column, wide tree, `CartographyEntryReportScreen`, `MapChromeAlphaTest` untouched.

## Pre-registration (written and pushed before any test or code)

All at `w823dp-h384dp-land`, `ROTATION_90` (cluster on the left) and `ROTATION_270` (cluster on the right), `isRecording = true` unless stated.

**Predictions against the base (`9c337f19`), tests-first.** Every new test below fails at base for the reason named; a pass at base is a stop.

| # | Claim | Pass condition | Fails at base because |
|---|---|---|---|
| P1 | L shape | bar 48 × 240 (five contiguous 48 dp rows), gap 8, pill 48 thick × 96, pill outer edge flush with bar outer edge, return inboard of record, cluster 296 tall × 96 wide | base bar is 264 tall, pill is beside the bar, cluster 264 tall |
| P2 | Empty corner and gap reach the map | a real long-press at the point inboard of the bar's mid-height, and one in the 8 dp gap, each increments the map stub's long-press count; a long-press on the bar itself does not (positive control) | base container `Surface` covers both |
| P3 | Nothing drawn | pixels in the corner and the gap equal a reference map pixel (native graphics) | base container fill (0.6) is drawn there |
| P4 | Single-layer 0.8 | bar and pill interior pixels equal `0.8·chrome + 0.2·background` per channel, within 2/255 | base children composite over the container (0.92 over background) |
| P5 | End-row touches | five real touches across each of the fullscreen row and the add row (fractions 0.2/0.8 corners and centre of the row's own 48 dp bounds), all reach it; touches on rows 2 and 3 never reach the map | passes at base for touch routing where the rows already reach; the bar geometry asserted in the same test is what fails — flagged: this one may pass at base on its touch half and is not the discriminating test |
| P6 | Drag, snap, minimise, restore | after a snap the pill stays flush with the bar's outer edge and inboard-extending; 40 dp drag moves the cluster 40 dp; minimise and restore returns to the same place | shape assertion fails at base |
| P7 | Clamps | with the cluster dragged to the bottom, `bottom == mapArea.bottom` and height 296; with a search notice showing and the cluster dragged to the top, `top >= notice bottom` and height 296 | height is 264 at base |
| P8 | Anchor | `mapIconBarRowAnchorOffset(5, rowPitch = 0.dp)` is +96 dp from the bar's centre; the default call is unchanged at +104 dp | the parameter does not exist at base (compile error is expected; recorded, not a stop) |

**The corner-clipping stop (dispatch).** Prediction: five touches at fractions 0.2/0.8 of an end row's 48 dp box lie inside the rounded end. From the 24 dp corner radius on a 48 dp wide bar, the point at fraction (0.2, 0.2) is at distance √2 × 14.4 = 20.4 dp from the arc's centre (inside 24); at (0.1, 0.1) it is 27.0 (outside). So the gate samples are inside by geometry, and the extreme corners of the 48 dp box are outside the drawn shape by geometry. Whether Robolectric's Compose hit-testing follows the rounded clip is undetermined (the map, §6). A separate, non-gating probe of the extreme corners is run once and its result reported here rather than committed as a test.

**Machine.** 2332 MB available at the first check, under the 2.5 GB rule; no Gradle run started until both the memory and the Java-Gradle-process checks pass.

## Stop: no space left on device

**What happened, read from the build logs.**
- First run of the three test classes at the unmodified base (`:app:testDebugUnitTest --tests '*LandscapeLClusterTest' --tests '*LandscapeLClusterPixelsTest' --tests '*LayoutFixesShortLandscapeTest'`, `--offline`): `BUILD FAILED in 1m 2s`, `Could not add entry ':app:bundleDebugClassesToRuntimeJar' to cache executionHistory.bin`, then `Could not receive a message from the daemon`.
- Second run: `BUILD FAILED in 769ms`, `java.io.IOException: No space left on device` while resolving `incomingCatalogForLibs0`.
- `df` at the time: `/dev/nvme0n1p5`, 67 G, 100% used, 0 available; a byte count read a minute apart showed about 120 MB free, and it moves as other sessions build.

**Consequence.** Neither run reached compilation or a test; there are no JUnit results, so nothing in this report is a test result. The predictions in the pre-registration are untested.

**What I did not do, and why.** The space is held by other sessions' and the owner's work, none of it mine to delete: `~/Zynergy/forager-wt` is 11 G across some 30 worktrees, `~/.gradle` 9.8 G, `~/Android` 6.1 G, `~/.android` 4.9 G, `~/Zynergy/device-evidence` 3.8 G, `~/Zynergy/forager-repo-backups` 1.1 G. My own worktree is 165 MB, and clearing it would not free enough for a Gradle build. Removing another worktree's `build/` or the shared Gradle cache would be outside this dispatch's scope ("work only in your named worktree") and could break a build another coder or the device session is running. So this is a stop for the owner or planner to resolve, not something I worked around.

**What is pushed** (branch `landscape-l-wip`; nothing on `journal-redesign`):
- `LandscapeLClusterTest.kt` (geometry, real coordinate touches, corner and gap long-presses with positive controls, bottom and notice clamps), `LandscapeLClusterPixelsTest.kt` (native graphics: nothing drawn in the corner or the gap; bar and pill each one layer at 0.8), and edits to `LayoutFixesShortLandscapeTest.kt` replacing TR1, TR2 and TR4 (and the `bar()` helper's 4 dp), each carrying the owner's ruling verbatim in its comment.
- Not compiled yet. Unverified: that these tests compile, and that they fail at base for the reasons pre-registered.

**To resume.** Free roughly 2 GB (a Gradle build here wrote about 160 MB into the worktree before failing, and the daemon and test workers need more), then: run the three classes at base and confirm the pre-registered failures; implement (container `Surface` becomes a fill-less `Box` in landscape; `MapIconBar` and `ControlPill` gain a landscape row pitch and horizontal form; `mapIconBarRowAnchorOffset` gains a row-pitch parameter); revert checks from saved copies; the full suite from a cleared results directory.

## Decisions I would have to make (not made; for the planner)
1. **The pill's width.** The dispatch fixes its thickness (48) and outer-end flush, not its length. I pre-registered 96 (two 48 dp buttons, no padding or spacing, the same rule the bar's "no spacing" gives), which also makes the record button sit exactly under the bar and puts "half of the pill" under the bar as the owner said. The alternative is 108 (today's 4 dp padding and spacing turned horizontal), which offsets the record button 4 dp from the bar's edge. The tests pin 96.


# Resumed (dispatch 2026-09-28-162 continuation; the owner cleared space)

## 1. What governs, quoted
`prompts/preserved/2026-09-29-28.md` at `origin/journal-redesign` (`3016b39b`), verbatim:

> 1. **The pill** (your open question). The owner, verbatim: "the icons need to stack fully. Make sure that happens and have the pill extend outward like the L".
>    - The pill is **96 dp**: two 48 dp buttons with no padding or spacing.
>    - **Record's 48 dp box is exactly under the bar's 48 dp column.** Its left and right edges equal the bar's, within 0.5 dp in the tests, on both sides.
>    - Return extends outward, inboard of the screen.
>    - Your pre-registered 96 stands. Add an explicit alignment assertion if the shape test does not already pin both edges.
> 2. **The disk.** The owner answered "1 C": **the owner is clearing space. You delete nothing outside your worktree.**
>    - Before each Gradle run, also check `df -m /` shows at least **2048 MB** available, and wait if it does not.
>    - A run that fails with "No space left on device" is not evidence of anything. Re-run it from a cleared results directory once space is back.

The planner's message carrying it said the same; "Decisions I made" below therefore lists no open pill-length question. `LandscapeLClusterTest.assertShape` now pins both of record's edges to the bar's (added before the base run).

Gate applied before every Gradle run from here: no Java Gradle process (`pgrep -af '^\S*java .*([G]radleWrapperMain|[G]radleWorkerMain)'`), at least 2500 MB memory available, at least 2048 MB disk. Several runs waited on other sessions' builds. I did not run `./gradlew --stop`. A daemon left by the first crashed run held a lock on this worktree's `.gradle`, so runs used `--no-daemon`.

## 2. Tests first, at the unmodified base (`9c337f19` plus the tests only)
Run: `:app:testDebugUnitTest --tests '*LandscapeLClusterTest' --tests '*LandscapeLClusterPixelsTest' --tests '*LayoutFixesShortLandscapeTest' --offline --no-daemon`, results directory cleared first; no `e:` lines in the log; read from the JUnit XML: **30 tests, 16 failed, 14 passed.**

Failures, each for the pre-registered reason (message quoted from the XML):
- L1 (both rotations): "the bar is 240 tall ... expected:<240.0> but was:<256.0>" (the base bar has no end padding in `bar()` terms, 256 from row to row).
- L3 (both): "a real long-press in the empty corner inboard of the bar reached the map ... expected:<1> but was:<0>": the container fill takes it.
- L4 bottom clamp and notice floor (both): "the cluster is 296 tall ... but was:<264.0>".
- L5 (both): "the empty corner inboard of the bar shows the map: expected (0.996, 0.969, 1.000), read (0.957, 0.922, 0.890)": the container's 0.6 fill.
- TR1 (both): "the cluster ... is 296 tall ... but was:<264.0>"; TR2 and TR4 (both rotations, the four): "the pill ... is 8 dp below the bar ... expected:<8.0> but was:<-104.0>": beside, not beneath.

Passing at base: TR3, TR5, T5, T9 (unchanged tests, as expected), **and L2 (the real touches on the end rows), which I had flagged as not discriminating at base.** They confirm the touch routing before and after; the shape they sit in is asserted by L1. The L2 middle-row tests passed at base in their first form and were later narrowed (section 5).

## 3. What was built (all under `app/src/main/java/com/zynergylabs/forager/app/ui/`)
- `availability/AvailabilityCompactMapUi.kt`: in landscape (`landscapeCluster`, CMU:409) the container `Surface` is replaced by a plain `Box` that draws nothing and takes no pointer input, still carrying the padding, the measuring and `MAP_ICON_CLUSTER_TAG`; inside it `LandscapeLCluster` (replacing `ShortLandscapeClusterRow`): a `Column` aligned to the cluster's outer edge, the bar, an 8 dp gap, the horizontal pill. The bar and pill lambdas are hoisted above and take their fill and spacing as parameters; the portrait branch is the old `Surface`/`Column` with the same arguments (child fill, `Spacing.xs`). The AddActionTile's y offset uses `ADD_TILE_ANCHOR_OFFSET_LANDSCAPE` in landscape.
- `map/MapChrome.kt`: `MapIconBar(rowSpacing = Spacing.xs)` (default unchanged, so `CartographyEntryReportScreen`'s call is unchanged); `mapIconBarRowAnchorOffset(rowIndexFromTop, rowSpacing = Spacing.xs)`; `MAP_ICON_BAR_LANDSCAPE_ROW_SPACING = 0.dp`; `mapIconChromeFillColor()` (the standing 0.8 fill).
- `availability/AvailabilityMapControlsUi.kt`: `TrailheadControls`/`ControlPill` gain `horizontal`, `onLeftSide`, `fillColor`, `rowSpacing` (defaults reproduce the vertical pill exactly); the horizontal pill is a `Row` whose order mirrors by side so record is always the outer end; `ADD_TILE_ANCHOR_OFFSET_LANDSCAPE`.
- The wide (tablet) tree, `CartographyEntryReportScreen`, `MapChromeAlphaTest` and its constants: not edited.

## 4. Forward runs, and what each caught
- First forward run (12 of 30 failed). One was **a real bug in my first build, caught by L1 at ROTATION_270**: the horizontal pill's `Row` put record first, so on the right side record sat at the inboard end, beside the bar's column, with return under it (message: "the record button's left edge equals the bar's ... expected:<767.0> but was:<719.0>", and TR2/TR4 the same on that side). Fixed by mirroring the Row order.
- The rest were my tests' own errors, fixed in the tests, not the code: L4 notice floor assumed the top reaches the notice's bottom (section 6b); the L5 pixel sample landed on the minimise handle; L4's message text read the "Fullscreen" row while the screen was legitimately in fullscreen; L2's touch fractions on rows 2 and 4 (section 6c).
- Last forward run of the three classes: **30 tests, 2 failed (T9, section 6a), 28 passed.** New: `MapIconBarAnchorTest` (2, the default 104 dp unchanged, the L's 96 dp) and L6 (the add menu's panel 32 dp below the add row's centre; a characterisation read at the implementation, see the test's comment).

## 5. Revert checks (from copies saved to `/tmp/llrev` before each edit; the runner refuses results if the log has an `e:` line and restores from the saved copy; each log had none)
| # | Revert | Failure that this edit alone causes |
|---|---|---|
| R1 | landscape bar back to `Spacing.xs` row spacing | L1 "the bar is 240 tall ... but was:<256.0>", TR1 "the cluster ... is 296 tall ... but was:<320.0>", TR2/TR4 "8 dp below ... but was:<12.0>", TR5 drag 32 not 40 (the clamp) and L6 "expected:<32.0> but was:<24.0>" |
| R2 | horizontal pill order not mirrored | only L1, TR2, TR4 at the right-hand cluster: "the record button's left edge equals the bar's ... expected:<767.0> but was:<719.0>", "the return button ... extends inboard (left) of the record button" |
| R3 | container `Surface` with the 0.6 fill back under the L | L5 "the gap under the bar shows the map: ... read (0.973, 0.945, 0.976)"-family corner/gap pixel failures and L3 "a real long-press in the empty corner ... reached the map ... expected:<1> but was:<0>" |
| R4 | the pill's own fill back to the 0.5 child fill | only L5 "the pill reads as one layer at 0.8 over the map: expected (0.941, 0.906, 0.851), read (0.965, 0.929, 0.906)" |
| R5 | AddActionTile anchor back to the portrait offset | only L6 "the add menu's panel bottom is 32 dp below the add row's centre ... expected:<32.0> but was:<40.0>" |
**Correction to the R3 row, made after re-reading the result files:** the pixel message `the gap under the bar shows the map ... read (0.973, 0.945, 0.976)` belongs to R1 (the spacing revert moves the pill into the gap), not R3. R3's L5 failure is `the empty corner inboard of the bar shows the map: expected (0.996, 0.969, 1.000), read (0.957, 0.922, 0.890)`, the same reading as at base, as it should be with the container fill back.

Every run also carried T9's two known failures (not caused by these edits). After the last revert, `git status` was clean against `af00f9a0` and the anchor line is the forward one (checked by grep), so the forward change was present. `L3`'s reverted result (R3) confirms the positive control: with a fill the corner really is captured.

## 6. Three conflicts with existing behaviour. I have not decided any of them.
**a. T9 fails at both rotations (existing test, not edited).** "the search bar [0.0, 0.0][384.0, 45.0] and the cluster [8.0, 44.0][104.0, 340.0] do not intersect" (and the right-hand mirror). The centred 296 dp L has its top at 44 dp; the search bar's bottom is 45. Cause, read from the code: the top clamp's upward bound is `coerceIn(-fallbackDownwardOffsetPx, 0f)` at CMU:846, so the clamp can pull a cluster up but never push a centred one down, and 296 > 384 - 2 x 45 by 2 dp. At the old 264 it never met the bar. Options for the planner: let the top clamp push down (a change to the clamp's semantics); accept a 1 dp overlap and change T9; or another. Device insets differ, so the S22's real figure is unknown.

**b. The notice floor cannot hold in a 384 dp window (my test corrected, a code fact reported).** With a notice showing, the floor is `min(notice bottom, lowest edge)` (CMU:880-882), so the L's top sits at 88 dp with the notice ending at 137: the L overlaps the notice by 49 dp (at the old 264, by 17 dp). The L4 test pins the code's own contract (top = 88), not a clearance.

**c. The minimise handle steals the outer-lower corner of the compass row and the outer-upper corner of the Layers row: 11 existing tests fail.** `LayoutFixesChipRowLandscapeTest` T7 (all 11, both rotations): "five real touches across the reset button [8.0, 92.0][56.0, 140.0] all reached it expected:<5> but was:<3>" (4 of 5 at ROTATION_270). The handle is a 20 x 72 dp box centred on the bar's mid-height (unchanged, per the dispatch). With rows 48 dp apart it reaches 12 dp into rows 2 and 4; at the old 52 dp pitch it reached 8 dp, just short of the sample at 0.8 of the row's height. My own L2 middle-row test therefore samples the compass and locate rows only at the centre column and the inboard 0.2/0.8 columns, and says why in its comment: that is the finding excluded by name, not hidden. Options: shorten the handle's box to 48 dp; move or narrow it; accept the loss and rewrite T7's sampling. The dispatch says the handle is unchanged, so this is a conflict between two of its own instructions.

## 7. The corner-clipping stop (dispatch), triggered
Scratch probe (real touches, two rotations, not committed; the source is not in the repo), the bottom row (its result is read from the add menu opening, which is unambiguous). At the row's own 48 dp box, fractions across and down:
- reached: (0.03, 0.03), (0.97, 0.03), (0.1, 0.1), (0.9, 0.1), (0.15, 0.15), (0.85, 0.85);
- **lost to the map** (`mapTaps+1`): (0.03, 0.97), (0.97, 0.97), (0.1, 0.9), (0.9, 0.9).
Same result at both rotations. The end of the bar is a 24 dp semicircle (a 24 dp corner radius on a 48 dp wide bar), so the two outer corners of the end row's box lie outside the drawn shape and the shape's clip takes the hit test: (0.9, 0.9) is 27.2 dp from the arc's centre (lost), (0.85, 0.85) is 23.8 (reached). Lost area is the two corner slivers outside the semicircle, about 5% of the box, none of it inside the drawn shape. The top row's probe is not usable (toggling fullscreen moves the chrome mid-probe); by the same geometry it is the mirror image. The gate samples in L2 (0.2/0.8 and the centre) all reach it. At the old 4 dp end padding the same geometry lost points near the corner too, only fewer (arithmetic, not run: (0.1, 0.1) at the old padding is 24.5 dp from the centre, just outside); that comparison is inferred, not measured. Per the dispatch I did not shrink the rows and this is the report of the geometry.

## 8. Full suite
From a cleared results directory, `--continue`, run started at epoch 1790675323; **348 result files, none older than the start; 2821 tests, 13 failed, 0 errors, 24 skipped** (skipped is the existing `@Ignore` count; I did not measure the base's). The 13: T9 x 2 (6a) and `LayoutFixesChipRowLandscapeTest` T7 x 11 (6c). No other class fails, including the portrait and tablet suites, `MapChromeAlphaTest`, and the Cartography entry report's.

## 9. Not tested / device-only
- Not run: the base's full suite (no baseline for the 24 skipped or to say the two T9 and eleven T7 are new by suite count; they pass at base per the tests-first run for T9 and by the old 8 dp overlap arithmetic for T7).
- Robolectric reports zero insets, so the L against the S22's real insets, the cut-out and the rail; thumb reach; and the empty corner on a real screen are device items at 90 and 270. So is whether real fingers land in the lost corner slivers.
- L6's 32 dp is read from the implementation, not derived.

## 10. Decisions I made
- The corrected tests (section 4) and the L2 middle-row sampling (6c) are my test-design choices, disclosed above.
- `LandscapeLCluster` replaces `ShortLandscapeClusterRow` (the old composable is gone, not left dead).
- Pushed to `landscape-l-wip`, not `journal-redesign`, because the branch carries three unresolved conflicts and a red suite.

## 11. Flags outside scope
- `/tmp` is shared: another session overwrote `/tmp/d58check.sh`, so the D58 check is inline (0 hits, diff and commit messages).
- A crashed run's daemon held this worktree's `.gradle` lock; I did not stop it.
- The disk and memory gates delayed runs by hours in total; several waits were on another session's long Gradle process.


# Resumed (continuation 2026-09-28-172, the owner's four calls; -178, TR5 and the top limit; J6c merge)

**Superseding note.** The header status line of this file ("STOPPED AGAIN on three conflicts ...") and the "Three conflicts" section above were the state at the first resume. All three are decided or resolved below; the record above is left as written.

## 1. What governs, quoted
`prompts/preserved/2026-09-29-30.md` at `origin/journal-redesign` (`91c7cc7e`), verbatim:

> **The owner, verbatim:** "1 2 3  I'll take your recommendations". Item 1 was your four decisions (record -170), so the planner's recommendation on each is ruled. All four apply in **phone short landscape only**. Portrait and the tablet stay unchanged.
> - **(a) The search bar.** Change the top limit so it **pushes the L down** as well as pulling it up. The L's top is never above the search bar's bottom (the strip clearance).
>   - T9 should then pass as it is. Do not edit T9.
>   - If the L cannot fit between the top and bottom bounds in a window, stop and report the numbers.
> - **(b) The search notice.** In landscape, the notice's end on the L's side is inset by 8 + the L's measured width + 8, the way the legend makes room (CMU:1272-1276 at 53c79fdc). The notice and the L never overlap.
>   - In landscape the L no longer follows the notice's floor: it stays where it is.
>   - Portrait's notice-floor behaviour is unchanged.
>   - Test both sides at 90 and 270 with a notice showing: the bounds are disjoint, the notice text is readable (it wraps as needed), and the L does not move.
> - **(c) The minimise handle.** Its touch box becomes **20 x 48 dp**, centred on the locate row (row 3). It must not reach the compass or Layers rows. The mark is unchanged unless it no longer fits the box; then stop.
>   - T7 should pass as it is. Do not edit T7.
> - **(d) The corners.** Each button of the L, all five bar rows and both pill buttons, takes touches across its **full 48 x 48 dp square**, corners included, even where the drawn shape curves away. Nothing outside those squares takes touches.
>   - Add real-touch tests at the probe points that fell through before: (0.03, 0.97), (0.97, 0.97), (0.1, 0.9) and (0.9, 0.9) of each end row's box, plus the pill's outer corners. Each must reach its button.
>   - A point 2 dp outside a square must reach the map.
> **Then:** tests first for (a)-(d), pushed failing; the build and revert checks; **the full suite at 0 failures;** push to journal-redesign; a "Resumed" section in your report.

The planner's message recorded as -178, verbatim, is the authority for the one edit to an existing test:

> 1. TR5: take option (1). Mark TR5 @GraphicsMode(NATIVE), as T9 and the chip tests already are.
>    - This is not weakening. The assertion and the 40 dp drag stay exactly as written. Only the text metrics change, from Robolectric's legacy ones (the search bar at 85 dp, which no device shows) to real ones.
>    - Conditions: (a) change nothing else in TR5; (b) show that it passes under NATIVE; (c) show that it still bites, with a revert check from a saved copy that breaks the vertical drag, failing with TR5's own message; (d) quote this message in your report as the authority for the edit.
> 2. Top limit: "never above the search bar's bottom" is the ruling's wording, so topInset alone is right IF nothing else is drawn between the search bar's bottom and topInset + compassStripClearance in short landscape. Check that in code before you land: Is the compass strip, or anything else, composed in that band in short landscape? Cite file:line. If nothing is: keep topInset alone. If the strip IS there: the L must clear it too (topInset + clearance). Under native metrics that is 61 + 296 = 357 <= 384, so it fits on real text metrics. Any test that only fails in legacy metrics then goes NATIVE under the same conditions as TR5. Report which it was.

## 2. Tests first for (a)-(d), at the tree that had the L but none of the four calls
Committed and pushed before any code (`e75fdb98`, message not saying "not run yet"; `65f915d5` is the empty follow-up commit that says so), then run once the disk floor allowed: `LandscapeLRulingsTest` (new), my `LandscapeLClusterTest` L2 and L4 (updated: the notice-floor tests were removed as superseded by (b); the compass-row touches now take the full spread, since (c) removes the handle from that row), plus `LayoutFixesShortLandscapeTest` and `LayoutFixesChipRowLandscapeTest`. Log has no `e:` line; read from the JUnit XML, 4 files, none stale: **71 tests, 31 failed.** Every new failure is for its ruling: A1/B1 ("the L did not move when the notice appeared ... expected:<44.0> but was:<88.0>", the old notice floor), C1 ("the handle's touch box is 48 dp tall ... expected:<48.0> but was:<72.0>"), D1/D2/D3 (corner touches on the top row, bottom row and pill lost, "every corner touch on the record button's square reached it expected:<8> but was:<4>"), T7 (11) and T9 (2) as before. Two were my tests' faults and are recorded: A1 first ran under legacy text metrics where the search bar is 85 dp (T9 is NATIVE), so A1 and B1 are marked NATIVE; and D4's first case ("2 dp outside the top row's outer side") returned no map tap before the change, for a reason **I did not isolate** (it passes after the change; which element took a touch at 6 dp from the screen's left edge before is unverified).

## 3. What was built for the four calls
- **(a)** the top-limit clamp's upper bound is `Float.POSITIVE_INFINITY` in the L (was 0f), so the limit pushes the L down as well as pulling it up; the L's limit is `topInset` (the search bar's bottom).
- **(b)** `SearchNoticeInset` and `LocalSearchNoticeInset` (in `AvailabilitySearchUi.kt`): the notice takes an absolute left or right inset; `CompactMapTab` provides 8 + the L's measured width + 8 on the L's side, and only when the L is on the notice's own side (the punch-hole side); the notice-floor step of the clamp is skipped in the L.
- **(c)** `MapIconBarMinimizeHandle(tapHeight)`: 72 dp default (`HANDLE_DEFAULT_TAP_HEIGHT`), 48 dp in the L. The mark is 48 x 10 either way (it was the 72 dp box less 12 dp above and below) and fills the shorter box exactly; it did not have to change.
- **(d)** `MapIconBar(fullSquareHits)` and `ControlPill`'s horizontal form draw their shape on a content-less `Surface` sized to the buttons and put the buttons above it unclipped, so the rounded ends no longer decide which corner touches a button gets. Nothing outside the squares takes touches: the shape lies inside their union.

## 4. Forward runs, and what they caught
- First forward run of the classes: **75 tests, 22 failed.** One cause for most: I had used the existing top limit, `topInset + compassStripClearance` (61 dp under NATIVE, about 121 dp under legacy metrics), which put the 296 dp L at 121 to 417 in a 384 dp map: the pill half off-screen, so TR1, TR3, TR5, L4 and D4 failed together. Changed to `topInset` alone (section 6); also fixed C1's unmerged-node lookup (`useUnmergedTree`).
- Second run: 75 tests, **4 failed**: TR5 at both rotations (section 5) and my L2 top-row test, which read the row's bounds once while the L now moves when fullscreen hides the search bar; it re-reads bounds after each settled touch now.
- Then, with TR5 marked NATIVE and a new A2 (below): 32 tests in the two classes, 0 failed.

## 5. TR5, on the planner's authority (-178, quoted above)
- **Before:** under legacy text metrics the search bar is 85 dp tall (topInset 85), the map area is 384, the L is 296: at rest 85 to 381, 3 dp of travel. TR5: "a 40 dp drag down moved the cluster 40 dp ([8.0, 85.0][104.0, 381.0] to [8.0, 88.0][104.0, 384.0]) expected:<40.0> but was:<3.0>".
- **Edit:** the `@GraphicsMode(GraphicsMode.Mode.NATIVE)` annotation on both TR5 tests and a comment quoting the reason; nothing else in TR5 changed (condition (a)).
- **(b) Passes under NATIVE:** the run `LandscapeLRulingsTest` + `LayoutFixesShortLandscapeTest`, 32 tests, 0 failed; and TR5 is green in the merged-tree full suite (section 9).
- **(c) Still bites:** revert Q1 (section 8), from a copy saved before the edit, breaks the vertical drag (`dragAmount.y` replaced by `0f`): TR5 fails with its own message "a 40 dp drag down moved the cluster 40 dp ([719.0, 45.0][815.0, 341.0] to ...)", with no compile error.
- **(d)** quoted above.

## 6. The top limit: what is in the band between the search bar's bottom and topInset + compassStripClearance in short landscape (the -178 condition)
Read from code, at `AvailabilityCompactMapUi.kt` after the merge:
- **The compass strip: no.** In short landscape it is in the top corner on the rail side, the side away from the search bar: `CompassElevationStrip`'s landscape modifier, CMU:704-710 (aligned top-start or top-end by `railPortEdge`, `padding(controlsPadding)` only, no `topInset`). Test A2 (`LandscapeLRulingsTest`, NATIVE, both rotations) drags the L to the top of the rail side and asserts its bounds do not intersect the strip's (`compass-elevation-strip`): passes.
- **Anything else: three things, none of which needs the clearance.**
  - The **search notice** sits under the search bar (the search slot's Column), so it is in the band by design; ruling (b) makes room for it horizontally.
  - The **taxon and journal chip row** is at `topInset + Spacing.sm` on the punch-hole side (CMU:764), so it is also in the band; its width is capped to leave the L's column clear (CMU:765-767), and T7, which passes unedited, pins that.
  - The **SearchDropdown** starts at `searchBarHeight + compassStripClearance` (`AvailabilityCompactScaffold.kt:1228`), so it starts below the band, and only while the search field is focused; it is drawn over the map content, the L included, and I did not test the overlap of an open dropdown with the L (a device item).
- **So it was: nothing persistent in the band that the L must clear. `topInset` alone is kept** (CMU passes it as `topLimitPx` in the L; portrait keeps `topInset + compassStripClearance`). Under NATIVE the L rests at 45 to 341 (bar 45; limit 45; 296 tall; map 384); under legacy metrics at 85 to 381.

## 7. The J6c merge (planner: "expect conflicts ... resolve by carrying the L into MapIconCluster / MapIconClusterState.landscape ... report file by file")
Merged with `git pull --no-rebase origin journal-redesign` (no rebase, no reset), twice: the first at `92556887` (J6c's `44c4ff2a` extraction and its dependents), the second after `journal-redesign` moved on 11 more commits, none of them source (RECORD.md, one audit record, the plan, four prompt files).
- `AvailabilityCompactMapUi.kt`: **the only textual conflict.** Took `journal-redesign`'s version whole (the cluster had left it: 1555 to about 1030 lines) and re-applied only what still belongs to the phone's tab: `topLimitPx` (`topInset` alone in the L); the `SearchNoticeInset` provided around the search slot; the AddActionTile anchor `ADD_TILE_ANCHOR_OFFSET_LANDSCAPE`; the bar slot as a local `phoneBar(modifier, fill, rowSpacing, fullSquareHits)` used by both `bar` and the new `landscapeBar`; the new `landscapePill`.
- `AvailabilityMapIconCluster.kt` (new upstream, no conflict): the L lives here now. Carried: the landscape `Box` in place of the container `Surface` (`clusterMeasure` shared by both), `LandscapeLCluster` in place of `ShortLandscapeClusterRow`, the top-limit clamp's upper bound and the notice-floor skip keyed on `state.landscape`, the handle's `tapHeight`, and two new optional parameters on `MapIconCluster`: `landscapeBar` and `landscapePill` (default null; used only when `state.landscape`, else `bar` and `pill`).
- `AvailabilityWideLayoutUi.kt` (the tablet's call): **not touched.** It supplies only `bar` and `pill`, never sets `landscape`, and so takes exactly the code it took at J6c. This is why the L's bar and pill are separate optional parameters and not a change to the shared slots' signatures.
- `MapChrome.kt`, `AvailabilityMapControlsUi.kt`, `AvailabilitySearchUi.kt`: auto-merged with J6c's edits without a textual conflict (they were not touched by J6c's extraction beyond what merged cleanly); the compile and the full suite are the check that they are consistent.
- Tests J6c added or changed (`WideMapControls*Test` x3, `MapChromeOverMapTest`, `AvailabilityScreenMapLayersTest`, `JournalEntriesOnMapScreenTest`, `WideMapTabsTest`) were not edited by me.

## 8. Revert checks on the merged tree (from copies saved to `/tmp/llrev` before each edit; the runner refuses results if the log has an `e:` line, restores from the saved copy, and the tree was clean against the merge commit afterwards)
All 13 logs had no `e:` line; each ran the 5 classes (75 tests). Each failed only for its own edit:
- Q1 vertical drag broken: **TR5** "a 40 dp drag down moved the cluster 40 dp ([719.0, 45.0][815.0, 341.0] to ...", and L4's fullscreen bottom clamp.
- Q2 top limit's upper bound back to 0f: A1 "at rest the L's top 44.0 is not above the search bar's bottom 45.0", D4.
- Q3 notice floor back on in the L: B1 "the L did not move when the notice appeared".
- Q4 handle box back to 72: L2, C1, T7.
- Q5 notice inset removed: B1 "the notice [...] and the L [...] do not overlap".
- Q6 top limit back to `topInset + clearance`: L4, TR1 "the cluster [8.0, 121.0][104.0, 417.0] lies inside the map area" (the legacy-metrics window, the numbers from section 4).
- Q7 bar's full-square touches off: D1, D2.
- Q8 pill's full-square touches off: D3.
- Q9 add-tile anchor back to the portrait pitch: L6 "expected:<32.0> but was:<40.0>".
- Q10 the container `Surface` with the 0.6 fill back: L5 "the empty corner ... shows the map", L3 "a real long-press in the empty corner ... reached the map".
- Q11 the pill's own 0.8 fill removed: L5 "the pill reads as one layer at 0.8".
- Q12 the bar's row spacing back to 4 dp: L1 "the bar is 240 tall", L5 "the gap under the bar shows the map".
- Q13 the pill's Row order not mirrored: L1, TR2, TR4 at the right-hand cluster.
T9's two failures appear in every one of these runs only where the edit lets the L rise above the search bar (Q2, Q6); T9 passes unedited in the forward runs.

## 9. Full suite on the merged tree
From a cleared results directory, `--continue`, run started at the epoch in `/tmp/llrev/full3.log.start`: **BUILD SUCCESSFUL; 355 result files, none older than the start; 2915 tests, 0 failed, 0 errors, 24 skipped** (the existing `@Ignore` count; not measured at base). That includes every L class, `LayoutFixesShortLandscapeTest` (T9 and TR5 unedited except the TR5 annotation), `LayoutFixesChipRowLandscapeTest` (T7, unedited), `MapChromeAlphaTest`, and J6c's three `WideMapControls*` classes (Portrait, Landscape, 1280). The second merge after that run added no source (docs, prompts, RECORD.md only, checked by `git diff --stat`), so the result stands for the pushed tree.

## 10. Decisions I made
- **The top limit is `topInset` alone in the L** (section 6), the planner's -178 conditional, verified as required (the compass strip is not in the band).
- **The notice inset applies only when the L is on the notice's own side** (the punch-hole side); the ruling's "the notice's end on the L's side" is read that way, so a notice does not shrink when the L is across the screen from it. The alternative (always inset the L-facing end) is one condition.
- **Optional `landscapeBar` and `landscapePill` on `MapIconCluster`** (section 7), so the tablet's call is unchanged.
- **`SearchNoticeInset` reaches the notice through a composition local**, so the search slot's signature (used by the scaffold) is not widened.
- **L2 and L4 (my tests) were updated**, and A1/B1/A2 marked NATIVE, for reasons recorded above.
- The handle's mark is unchanged at 48 x 10 dp; it fits the 48 dp box exactly, so the "stop if it no longer fits" condition did not arise.

## 11. Not tested / device-only, and flags
- Real insets (a real `topInset`, the cut-out, the rail), the real search bar and strip heights, thumb reach, and a real finger on the corner touches: device items at 90 and 270 on the S22.
- The notice's real wrapping (Robolectric's fonts): the tests assert its bounds are disjoint and at least 200 dp wide, not that it reads well.
- An open SearchDropdown over the L (section 6): not tested.
- D4 touches 2 dp outside the squares, including at 6 dp from the physical left edge, where a real device has a system back-gesture zone: device-only.
- The skipped count (24) is not compared against the base.
- **D4's first pre-change failure was not root-caused** (section 2).
- **Flags outside scope:** `/tmp` is shared between sessions (an earlier session overwrote a script of mine; the D58 check is inline, 0 hits on the diff and commit messages); several Gradle runs waited for other sessions' builds and the disk floor.
