# Dispatch 2026-11-05-10, Phase 2 interim: the stop on the measured widths (Hazel Cairnwright)

Preserved by the planner from the hand-back message, verbatim. The owner's rulings on its four questions are appended to RECORD -2181.

Hazel, dispatch 2026-11-05-10 Phase 2: I've stopped. The owner's rulings can't all hold on the measured widths, so four placements need the owner. The settled half is built, tested and pushed. There's no PR yet, and no reverts, full CI or completion report: they wait for the ruling. You can resume me with it; my context is intact.

## Base
- Branch `nav-card-kind-line` is cut from origin/pre-main 779599c3f. I re-checked it at the end and pre-main hadn't moved. Head is ae7ad700c, pushed, and the tree is clean. [observed]
- My worktree is `~/.cache/forager-wt/nav-card-kind`. I never touched yours, `edge-shade` or `trip-download-maps`. No Gradle ran on the laptop and no stash was used. [observed]

## The stop (also sent to you by message during the run)
**Finding** [observed widths from CI run 42171184260, through the real screen; the step outcomes are arithmetic on them]:
- The step is chosen for the longest distance, as the owner ruled. With that rule and the app's font (Noto Sans), the longest figure doesn't fit left of the middle at normal text size whenever an evening time shows:
  - in landscape and at 360 dp, for both kinds;
  - on the S22 in portrait, for straight.
- The longest figures are 85 dp by trail and 101 dp straight. The half is 96 dp on the S22, 84 dp at 360 dp, 64 dp at 320 dp and 80 dp in landscape.
- The owner's own landscape screenshot ("2.2 mi", "Dark since 5:23 PM") would get "by trail" on its own line.
- Read strictly as halves, the owner's steps 1 and 2 ("by" over "trail", then "trail") can never happen, at any size or font scale. The 24 sp figure is always wider than the 12 sp "by trail" (85 against 44 dp at font 1.0, 127 against 86 at 2.0). Every fallback is caused by the distance not fitting, never by the kind.

**Q1.** The owner's words: "Meet at the middle (Recommended)" and "Option 1 but stack by on top of trail first, then just shorten it to trail, and then drop it down". Neither option keeps both, so neither is marked Recommended.
- A. Exactly at the middle. Where the longest figure doesn't fit, the kind drops to its own line, and stacking never happens.
- B. At the middle where it fits. Otherwise the meeting point moves right just enough (4 to 20 dp at font 1.0) and stays fixed for the walk; then stack, "trail", own line.
  - At font 1.0 this is one line everywhere measured except 320 dp straight with an evening time.
  - At font 2.0 with an evening time, by trail stacks and straight takes its own line.

**Q2.** A word wider than its half: "Arrived" (89 dp) at 360 dp or in landscape with an evening time, or the figure on step 3 under A. The owner said "In the left half".
- a. Start at the left part's left edge and cross the middle only as far as it must.
- b. Centred, as today. The owner turned down "centred" for words that stand alone.

**Q3.** "Drop it down": where the kind sits on its own line.
- a. Starting at the middle, or as near it as fits.
- b. Right-aligned under the distance.
- c. Centred, as Phase 1 drew it.

**Q4.** Only under B, or at very large text: the stacked "by" and "trail".
- a. Both start at the middle, with "trail" on the distance's baseline.
- b. The stack centred on the distance's height.

## What landed, commit by commit [read]
- **a5b19578d (WIP):**
  - `NavigationCardTopLine.kt` (137 lines) draws the top line.
  - `NavigationCardTopLineFit.kt` (115 lines) is the pure step choice: `longestFigures`, `TopLineHalves`, `kindPlace`, and `leftPartWidthPx`, which `InfoParts` now shares.
  - `LeftPart` draws the top line and no kind line under the turn row.
  - `NavigationInfo` decides the place on the width the left part is drawn at.
  - The strict reading (A) is coded. My choices for Q2a, Q3a and Q4a are in as placeholders until the owner rules.
- **07bdeffd5:** a screen rig (`NavigationCardKindLineScreen`, using the made-up test point P) and portrait and landscape classes. They cover only the cases every option agrees on: no evening time, and the longest figure fitting its half by at least 8 dp.
- **f22f5fc5b:** `NavigationCardTopLineFitTest`, with the longest figures checked against the real formatters over a sweep, plus the pinned-test updates.
- **ae7ad700c:** fixes a test name that wouldn't compile.

**What a user sees** where every option agrees:
- The distance ends 2 dp left of the left part's middle, and the kind starts 2 dp right of it, on one baseline.
- "Arrived" and the dash end at the middle.
- Nothing sits under the turn row. The card is 16 dp shorter (77 to 61 dp at font 1.0).

## Evidence [observed]
- **Red on pre-main** (run 42173497021): pre-main plus only the new screen tests, on throwaway branch `nav-card-kind-red`.
  - The build log has no compile errors. 10 of 10 tests fail.
  - Every message is specific to this change: "does not end at the middle", "the kind's baseline … is not the distance's", "is not above the turn row", "the left part ends … below the turn row's bottom". No positive control fired.
- **The WIP with the old pinned tests** (run 42173523842): the 10 new tests are green. The old tests fail exactly where Phase 1 predicted. `NavigationCardFitAcrossWidthsTest` stays 12 of 12.
- **Run 42174305046 (f22f5fc5b):** my test name had a colon and didn't compile. Its results were refused, not cited. My fetch script now reads the build log for compile errors before it reads any XML.
- **The head** (run 42175030338, ae7ad700c): 56 of 56 across the 8 affected classes, no compile errors.

## The pinned tests, old against new [read]
- **NavigationInfoLayoutTest, the shared centre:**
  - Old: `abs(centreX(figure) - lineCentre) <= 1.5` and the same for the kind.
  - New: the figure ends at lineCentre − 2 dp and the kind starts at lineCentre + 2 dp (±1), on the same baseline (±0.5).
- **NavigationInfoLayoutTest, the two divider tests:**
  - Old: they used the kind's bottom.
  - New: they use the turn row's bottom. With no evening time it's now exact: `abs(divider.bottom − turn row bottom) <= 1`, where it was `>= kind.bottom − 1`.
- **NavigationHudKindInStatusTest:**
  - Old: `kind.top >= figure.bottom − 0.5` and `kind.top >= needle.bottom − 0.5`.
  - New: same baseline, `kind.left >= figure.right`, and `kind.bottom <= needle.top + 0.5`.
- **AvailabilityScreenNavigationWordsTest, the kind:** the same change as above.
- **AvailabilityScreenNavigationWordsTest, the heights:**
  - Old: `assertEquals(short, long, 0.5)`.
  - New: `long == short − the dash's status line` (±0.5), with positive controls. Measured: 61 = 77 − 16.
- **RoundedMapCardFitTest:** the doc is updated and one case is added. Its values are unchanged on purpose, because this fit no longer decides the kind's place (see "Decided beyond the dispatch").

## Not done, waiting on the ruling
- Evening-time and fallback screen tests, and the font 2.0 cases near a threshold.
- Revert checks (kind up, middle alignment, steps).
- Full CI compared by test name with pre-main.
- PR, completion report, index row, S22 check.

## Decided beyond the dispatch
- **"Longest distance"** is the widest of "8888 ft" / "≈ 8888 ft" (straight) and "88.8 mi" (metric: "8888 m" / "≈ 8888 m" and "88.8 km"), or the figure on screen if it's wider. The figures are tabular, and the formatters top out at four digits. [read]
- **The top line takes no width from anything else.** The turn-line fit runs as before, and the kind is placed in the width that fit leaves. So the turn words, "≤ 16 ft" and the evening wrapping are unchanged.
- **The gap** between the distance and the kind is Spacing.xs (4 dp), centred on the middle.
- **The new tests use the app's own theme**, so they measure in Noto Sans, the S22's font. The other screen tests use the default font, which is about 4% narrower, and some thresholds are within 4 dp. [observed]
- **Screen reader:** stacked, the kind still reads "by trail"; as "trail" alone it reads "trail".

## Premises that were wrong, and side findings
- **The strict halves reading** makes the stack and "trail" steps unreachable (see the stop).
- **Phase 1's "a case where the figure plus the kind is the widest line"** doesn't apply to this design, because the kind no longer competes for width in that fit.
- **At sunset or dusk the evening time can change its width.** That moves the middle and can change the step. The owner's "doesn't jump" rule names only the distance, so this isn't handled. [inferred]

## Left in place
- Throwaway branches `nav-card-kind-measure` and `nav-card-kind-red`, which hold the evidence runs.
- Worktree `~/.cache/forager-wt/nav-card-kind-measure`.
- Scratch at `/mnt/work/scratch/nav-card-kind/`.
- All are mine and will be deleted at the end; if a fresh session takes over, they can be deleted.
- The measurement test used a made-up point and copied nothing from any fixture. The new tests use P.

Stops: this one.
