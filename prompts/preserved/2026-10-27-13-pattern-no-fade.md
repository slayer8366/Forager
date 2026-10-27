# Dispatch 2026-10-27-13: map patterns after a style reload, try "no fade" first (a coder, Opus)

Written by Rowan Waymark, 2026-10-27. RECORD -1748. Continues dispatch 2026-10-27-11 and PR #429. The owner: "Try 'no fade' first (Recommended)". The deadline matters: the owner's early weekly run waits on this being verified on the S22, and must start by Sat 2026-10-31 10:00 PST.

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## What is known (the S22 session of 2026-10-27; its report is in the planner's record, summarised here)
- **On the S22 (scratch v2, 1.0.4103+g027ce726):** clearing and restoring the fill-pattern layers fails at +0 to +250 ms after the style callback and sprite are both done (T0), and works at +300, +500 and +2000 ms, by day and by night. The threshold is between +261 and +300 ms.
- **The restore gap matters too.** A restore at the first full frame 2-7 ms after the clear can fail. A restore at the first full frame at least 100 ms after a clear at +400 worked 5 of 5.
- **MapLibre Native 13.5.0 source** (android-v13.5.0): fill-pattern is a cross-faded paint property that transitions (fill_layer_properties.hpp:40, properties.hpp:31-64), and a style with no `transition` gets 300 ms (parser.hpp:40, constants.hpp:59). street-v3 sets none, and the app turns off only the placement fade (PlacementTransitions.kt:20-29).
- **The hypothesis (inferred):** changing a pattern, or laying a tile out, during that 300 ms transition loses the pattern on kept tiles.
- **Two more findings:** MapLibre's style cache lives in files/mbgl-offline.db, not cache/. And a refreshed style was used only after a cold start.

## Step 1: the probe (on the S22, a scratch build, never merged)
1. **The build.** Set `fill-pattern-transition` to `{duration: 0, delay: 0}` on every fill layer that has a fill-pattern, in the app after each style load (before or in the style callback), with no clear and restore. Read the pattern layers from the style, not from a list of names. Build it on CI from a scratch branch.
2. **Install it.** Check user_version first: the phone is schema 22 on scratch v2 4103, and a lower versionCode needs `-r -d` with the same schema. Run:
   - night on at the maximum zoom;
   - night off at the maximum zoom;
   - a tile edge;
   - zoom out and back in;
   - tufts, if a wetland can be found.

   Use the forest house pair south of 10T ER 28696 19256, where a fresh layout reads about 2.72% glyph colour. Run each case 3 times, and take readings at about 1 s and 30 s after the switch.
3. **If it works,** with every case above 1% at both readings, that's the fix. **If it doesn't,** say so with the figures, and use the timer fix instead: clear at the style's transition (300 ms when unset) plus 100 ms, then restore at the first full frame at least 100 ms after the clear, keeping the generation check and the timeout fallback.

## Step 2: the fix on PR #429
Update PR #429 (branch pattern-relayout-fix) to whichever form worked. If "no fade" works, replace the clear and restore with it, and remove the clear and restore classes. In either case:
- **The code comment** cites the MapLibre 13.5.0 fault and the S22 figures.
- **Logging:** one log line per reload.
- **JVM tests:** for no fade, the transition is set on every pattern layer and on no other; for the timer, no clear before the deferral, no restore before the gap, and a style change drops the clear. Each fails first, then gets a revert check, restored from a saved copy with the build log checked.
- **Full CI.**
- **The device check again** on the PR's own build: the five cases, 3 times each, at 1 s and 30 s.
- **Correct the upstream draft** (docs/audits/2026-10-27-maplibre-pattern-relayout-upstream-draft.md on #429). Its lines 43-44 and its "Workaround" paragraph describe the form that failed. Add the +261/+300 ms evidence and the 300 ms default transition.
- **The PR body** carries the S22 steps and figures.
- Leave the PR build on the phone, and say so. Don't merge.

## Rules
- **Network:** GitHub only on the laptop.
- **Phone data:** logs and screenshots in /home/zynergy-labs/s22-glyph-fix/, off GitHub.
- **The Restricted Object:** never read, name or quote it.
- **Waiting:** one background wait.
- **Long conversation:** past about 200k tokens, push and hand off.
- **Scratch branches:** never merge them.
- **Other coders:** one is on the spiral (MarkerFanOut, FanPlacement, MapTapHandler). Don't touch those files.

## Report
- the four disclosure sections;
- which form was built and why;
- the figures;
- the runs;
- the phone's state.
