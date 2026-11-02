# Dispatch 2026-11-01-03: an S22 build with #449's card beside the bar, and its phone checks (Hazel, Opus)

Written by Rowan Waymark, 2026-11-01. RECORD -1935. The owner: "Fix these, then S22 build (Recommended)"; "Judge it on the S22 (Recommended)"; "Keep every word (Recommended)".

Read dispatches 2026-11-01-02 and 2026-10-30-21 Part 2, with their rules. They stand unless this says otherwise.

## Part 1: build and install
Worktree ~/.cache/forager-wt/tools-s22-combined, branch tools/s22-combined. Merge, at their heads, stating each: route-plot-ui (fb878627d as written) and origin/pre-main (08c1c3363 as written; it may have moved). Conflict beyond trivial: STOP. workflow_dispatch CI; compile errors first, then JUnit: only the three allowed Street v4 failures. Anything else red: STOP, install nothing. Then install as before: schema compared first, table hashes, in-app backup saved off the phone, digest, versionCode up, signing equal, `install -r`, hashes unchanged, logger on. Nothing cleared or reset.

## Part 2: what to prove
The phone may be unplugged at any time: do them in this order, and send your planner one line after each group with SendMessage to "main". TEST data only, deleted after; no Saves except a TEST route, deleted after; restore rotation by reading it back.
1. **The bar never moves.** Before plotting, record the bar's and the pill's bounds. Portrait, bar on the right: at peek, expanded and during a held drag, at 1, 3, 5 and 6 stops, the bounds are equal to the pixel. This is the check the last audit could not make: it measured a gap, and a moved bar passes a gap. Same for the bar moved to the left side, a dragged bar, and a minimised bar. Repeat in landscape.
2. **Nothing is covered.** Real touches on record, return, Layers, "+", locate and Save, spread across each, card up, both orientations. Include the 39 px strip below the old card's place.
3. **The card.** Portrait: width and side, sliding in from the side away from the bar, and shifting over when the bar crosses. Landscape: the strip's width at the bottom edge, away from the L, 1, 3 and 6 stops, the rows scrolling, below the search bar.
4. **Navigating, landscape.** Start navigation to a TEST waypoint and plot: the card rests on the navigation card, with Save above it and working. With a long-named TEST stop, say whether Save stays reachable. End navigation. No fix is expected indoors; say what you could do.
5. **The width motion, for the owner to judge.** Screenshots in sequence as legs are added: 312 dp to about 223 dp at the first leg, and a wider leg. Say whether Save or Undo is touchable during the ease, by a coordinate tap inside the 750 ms if you can time it. No video.
6. **Long names.** A 44-character TEST stop: whole, on as many lines as it takes, with the rows scrolling.
7. Font scale 2.0 in portrait, if adb can set and restore it: "N Waypoints routed" whole.

Screenshots of each go in /home/zynergy-labs/s22-2026-11-01/card-beside-bar/, never git. No video, no clock changes. No region downloads.

## Output
Add docs/audits/2026-11-01-s22-card-beside-bar.md on the tools branch, pushed. Final message: merges, the CI run, version, data checks, a verdict for each, what was not run, the phone's end state. A check that measured a gap is not a check that the bar stayed.

## Rules
Touch only the S22. No Gradle on the laptop. Evidence never in git. The Restricted Object: never read, name or quote it. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
