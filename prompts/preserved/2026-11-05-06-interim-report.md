# Dispatch 2026-11-05-06, Phase 2: the edge shade (Hazel Cairnwright). INTERIM hand-back: C2 is not done.

Preserved by the planner from the hand-back message, verbatim. The draft completion report is on the branch at `docs/audits/2026-11-05-06-edge-shade-completion-report.md` (2544e080e), with C2 and the final CI marked PENDING.

Everything is built and green except C2, the bubble's entry-count menu in `MapFeatureBubble.kt`. That waits for PR #494 (the trip bubble), which is open and on the phone check. Waiting means polling for hours, which my profile forbids ("one background wait, then stop"), so I'm handing back here. Send me #494's merge commit and I'll finish:
- merge pre-main into edge-shade (a merge keeping both sides, never a rebase);
- set the menu at its new line, about :204 on that branch (the edit is ready: shadow 0 and the hairline via `MapChromeMenuEdge.kt`);
- run full CI and read the counts from the JUnit XML;
- fill the report's PENDING lines, mark PR #495 ready, and delete scratch.

**PR #495 is still a draft. Not merged.**

## Where things stand

- **Branch:** `edge-shade`, cut from pre-main ee0651fe0, pushed, tree clean. Last commit 2544e080e.
- **pre-main has moved** to 3a3d85089 (#492, #493). Nothing overlaps this branch; the PR runs test the merge.
- **Last full CI:** run 42137248165 on 2544e080e. Every job succeeded, no compile errors: 6680 tests, 0 failures, 0 errors, 24 skipped.
- **The last code change,** b02687d2e (run 42134399792), gave the same counts.

## What changed

Over the map, no window has an elevation shadow any more, and each carries the map-chrome hairline where it lacked one. They are:
- every map bubble;
- the add tile, map type panel, legend chip, icon bar, control pill and cluster container;
- the search pill (its tuck shadow is untouched);
- the compass strip and both navigation cards;
- the centre-pin row: line on top and both sides, none at the bottom, the rail's line on its rail side;
- the two dialogs and five Material menus;
- the notice along the bottom.

Where the same piece also appears with no map behind it, it keeps its old look there: the solid search pill on other tabs, the notice off the Maps tab, and the menus that know whether they're over a map.

**The bubble** (your -2156 and -2160):
- The card and the tail are one shape, filled once and outlined once.
- The tail is a clean triangle whose base lies along the card's edge, so the line runs straight along both sides to the tip, with none across the join.
- Filling once removed a double fill where the tail sat under the card. At night that sliver read 242424 against the correct 3E3E3D (0.96 instead of 0.8); it now reads the correct value.

**The notice** (-2155, -2156) is the app's own (`MapChromeSnackbar.kt`). It copies Material's one-row layout rule for rule, and a parity test checks it against Material's side by side in one, two and four lines.

**Measured, as you asked, not changed:** the notice's action text is drawn in #D0BCFF (`inversePrimary`), not `primary` (#2E5339). The host's comment at `ScaffoldSnackbarHost.kt:49-51` is wrong: the colour comes from Material's `actionColor`, which the host never passes.

## Evidence

**Failing first:**
- Run 42119202488 (cd2a745e0): 21 failures, every one naming a band or a missing hairline. One prediction missed: the bubble check in MapChromeColourPixelsDarkTest passed before the change. Over the dark screen background the band is within 2/255, so that claim bites by day only.
- My first test version had two design faults, which run 42116693802 exposed and cd2a745e0 fixed:
  - the tile's and panel's side next to the icon bar lies over the bar, not the map;
  - the rail's side was read wrongly for the wide centre-pin row.
- Run 42126975492 (f96201a67): the clean-triangle check failed at the predicted step, on the tail's lower side.

**Revert checks,** one edit each on its own branch, built from a saved copy in a separate worktree, compile log read first. Each failed only its own cases:

| Check | Edit | Failures |
|---|---|---|
| r1 | bubble shadow put back | 6, all bubble |
| r2 | bubble outline removed | 4, hairline samples |
| r3 | card outlined alone | 2, the tail's join |
| r4 | notice two-line minimum at 64 dp instead of 68 | 1, parity |
| r5 | shared corner arcs not drawn | night only, which exposed the blind spot below |
| r6 | r5's edit, under the new rule | all four cases, 38 of 75 by day and 40 at night (I predicted 40 and 43) |

**The outline test's strip-end walk** (the test changes the owner allowed in -2160 and -2164):
- "Runs without a line", out of 75, by day: 0 with the shadow, 2 once it was gone, 0 after O-1, 0 after K-2a. Night stayed at 0 throughout.
- The bare-map reference reads 0.502. The line over the map reads 0.376 by day and 0.701 at night.
- The four squares that the 15° to 69° range drops, compared with 12° to 72°: all were broken in r5 under 12° to 72°, and none under the O-1 rule they keep.
- Two other places share the same day blind spot. Both are reasoned, not measured, and I left both unchanged:
  - the fullscreen far-end walk at :534;
  - the `assertLineNear` corner checks at :263, :264, :279, :313, :314, :343, :348, :367 and :368.

## Not tested, said plainly

- **The phone's band.** Robolectric draws shadows differently, so that's the device check.
- **Menus and dialogs** are not read from pixels, because they open in their own windows.
- **Not compared:** the notice's tooltip, and font scales other than 1.0.
- **Not read at their edges by the new classes:** the legend chip, icon bar, control pill and cluster.
- **Not exercised:** the centre-pin row in fullscreen, and tails near a card's corner.

## Device check (B3)

It's in the report, ordered cheapest first, by day then at night. Steps: the pill and strip, a bubble and its tail, the tile, the panel, a menu, the notice (including its action colour), the centre-pin row, and the navigation card. Each is pixel-sampled from the bare map through the edge to the middle, with a named pass condition per step.

## Calls beyond the dispatch

Accepted: the single fill, the outer tail samples at 2.5 px, and K-2a's angles.

Still for you to accept or reject:
- a grey stand-in map under every read;
- each panel's side by the icon bar read over the bar;
- the notice keeping Material's 6 dp shadow off the map.

## Premises that were wrong

- "The action on a new line": only for `actionOnNewLine`, which the host never passes.
- The host's comment that the action is `primary`.
- -2160's "a missing line would still fail": false by day at the strip's end.
- K-2's "the forward run stays green": false as worded, so it became K-2a.

## Stops

Seven, all resolved by rulings -2155 to -2164; each is listed in the report.

## Files

- Report (draft, C2 and final CI marked PENDING): `/home/zynergy-labs/.cache/forager-wt/edge-shade/docs/audits/2026-11-05-06-edge-shade-completion-report.md`
- New: `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapBubbleOutline.kt`
- New: `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapChromeMenuEdge.kt`
- New: `app/src/main/java/com/zynergylabs/forager/app/ui/availability/MapChromeSnackbar.kt`
- New tests: `SeeThroughEdgePixelsTest.kt`, `SeeThroughEdgePixelsLandscapeTest.kt`, `SeeThroughEdgeSupport.kt`, `MapChromeSnackbarParityTest.kt`
- Changed test: `MapChromeOutlinePixelsTest.kt`

## Left in place on purpose

- `/mnt/work/scratch/edge-shade/` (runner scripts, the C2 edit, run artifacts) stays for the C2 step and is deleted at finish.
- The worktree `~/.cache/forager-wt/edge-shade` stays.
- Revert branches r1 to r6 and the backup branch are deleted; only `edge-shade` is on the remote.
- RECORD.md, the audit index, the planner's worktree and the phone are untouched.
