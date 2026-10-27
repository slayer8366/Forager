# Handoff: dispatch 2026-10-27-13 (pattern no-fade, then the fix on #429)

Written 2026-10-27 by the coder (Opus) on Rowan's word (context past 200k). Lives on scratch branch
`scratch-pattern-no-fade`, never merged.

## Probe (step 1): "no fade" does NOT work
Build 1.0.4102+g0736420a (scratch-pattern-no-fade 0736420a8, CI 40578315652, artefact 12768901291, zip sha256 b7d5ca41...100d,
matches GitHub's digest). fill-pattern-transition 0/0 set at the top of the setStyle callback on every fill layer with a
pattern (log: "on 2 of 2 pattern layers [landuse_forest_glyphs, landuse_wetland_tufts]" on every load), no clear/restore.
Spot: the forest house pair (previous session's adbd recipe). Glyph share of x100-1150, y450-2300 (colour +-6):

| Case | Run | Before | 1 s | 30 s |
|---|---|---|---|---|
| Night on at cap | 1 | day 2.66 | night 3.18 | 3.18 |
| Night on at cap | 2 | day 2.83 | night 3.48 | 3.48 |
| Night on at cap | 3 | day 2.85 | night 3.16 | 3.16 |
| Night off at cap | 1 | night 3.14 | day 0.00 | 0.00 |
| Night off at cap | 2 | night 3.01 | day 0.00 | 0.00 |
| Night off at cap | 3 | night 3.21 | day 0.00 | 0.00 |

Tile edge, out-and-in and tufts were not run on the probe (it had already failed the gate).

## Step 2 on PR #429 (pattern-relayout-fix): BROKEN HEAD, fix first
- b446a3ed2: timer form (clear at style transition, 0 read as unset = 300, +100 ms; restore at first full frame >= 100 ms
  after; generation check, 2 s timeout, one log line per load); PatternRelayoutTest rewritten.
- c61867ffa: upstream draft corrected.
- **PatternRelayoutTest does not compile**: two test names contain ":" (lines 124 and 150, "Name contains illegal
  characters"). Rename them (drop the colons). PR run 40581709966 on c61867ffa will fail for this; b446a3ed2's run was
  cancelled by that push.
- Revert runs F0/R1/R2/R3 (branches prf3-*, runs 40581553104, -561194, -570695, -579806) all FAILED TO COMPILE for that
  reason: they are not evidence. Re-run them after the rename (re-create from the fixed head; edits in
  scratchpad revert.py: R1 inline immediate Deferral, R2 gate ignores the gap, R3 drop the deferral's generation check).
- Then full CI, device check on the PR build (5 cases x3, 1 s and 30 s), PR body with S22 steps and figures.

## Finding to weigh
disableSymbolFade (PlacementTransitions.kt) reads the style transition and writes it back with placement off; Android
reads an unset transition as 0 (native_map_view.cpp:920-930 at android-v13.5.0), so the app sets an explicit 0/0 in the
callback. The 300 ms default may then not be in force after the callback; the +261/+300 threshold's match with it is
unexplained. The timer uses 400 ms regardless.

## Phone
S22 on 1.0.4102+g0736420a (the scratch probe), schema 22 (checked before install; DB copy in
/home/zynergy-labs/s22-glyph-fix/db/pre-nofade/). Night Maps OFF. Evidence: /home/zynergy-labs/s22-glyph-fix/shots3/,
session-nofade.log. Note: `adb install -r` refused at first (versionCode 4102 below 4103); `-r -d` used, same schema.
