# Handoff: dispatch 2026-10-27-11, pattern relayout after a style reload

Written 2026-10-27T19:47Z by the coder (Opus), on Rowan's word that context was near full. The S22 session is
unfinished. This file lives on the scratch branch `scratch-prf-gap-probe`, which is never merged.

## State

- **PR #429** (`pattern-relayout-fix` into `pre-main`, not merged). Worktree at `/mnt/work/forager-wt/pattern-relayout-fix`
  (sparse), symlinked from `~/.cache/forager-wt/`. Commits: `6379dddfe` (the fix and test), `703ce7d94` (upstream draft
  `docs/audits/2026-10-27-maplibre-pattern-relayout-upstream-draft.md`). Its PR CI run 40567060530 succeeded: 5834 tests,
  0 failures, 24 skipped; `PatternRelayoutTest` 6/6. APK artefact 12764707313 (zip sha256 `6b6f4042…eb4`, matches
  GitHub's digest), build 4102+g529c8cb0.
- **The fix as on the PR:** `PatternRelayout.kt` (pure) and `PatternRelayoutApply.kt` (adapter), called from
  `MapStyleReload.kt` (request, callback). After the callback and the sprite-loaded listener have both fired, it clears
  each fill-pattern layer to `""` and restores at the first fully rendered frame (2 s timeout fallback).
- **The JVM test is fine. Fail-first and two revert checks passed**, all via workflow_dispatch filtered to
  `*PatternRelayoutTest`. In each, the build step passed and the logs held no compile errors:
  - F0, no relayout: run 40566830146, 6/6 failed.
  - R1, no restore: run 40566935347, 3 failed (lines 88, 120, 156, all restore-dependent).
  - R2, no generation check: run 40566940170, 1 failed (line 137, "the first style was cleared and never restored").
  - Restores were from a saved copy, and the forward file was confirmed intact by cmp.

## The S22: THE FIX DOES NOT WORK YET

The PR's form does not bring the glyphs back. Spot: the cold-start camera (45.3263 N, 122.6339 W, z12). Then double-tap
three times at (1000,2000) and five times at (600,1680), each double-tap as
`adb shell "input tap X Y & sleep 0.12; input tap X Y"` with a 2.5 s wait. That lands on the cap over forest: day glyph
2.81%, forest 95.19%. Night Maps toggle: Tools (1304,2760) > Settings (280,2815) > checkbox (148,1528), then Back twice.
Counts use the report's colours over x 100–1150, y 450–2300.

| Build / policy | Case | Glyph colour |
|---|---|---|
| PR 4102, first full frame (38 ms) | night on at cap | 0 (night forest 100%) |
| PR 4102, first full frame (31 ms) | night off at cap | 0 (day forest 100%) |
| scratch, delay 600 ms | night on at cap | 0, also 0 a few s later |
| scratch, delay 2000 ms | night off at cap | 0 |

So the restore gap is not the variable. The clear runs about 36 ms after the night images, right after the sprite
loads. The logging build's probe, which worked, ran many seconds after the reload. **Two fix attempts have failed on
this symptom, so the next step is data, not a third guess** (CLAUDE.md).

Scratch v2 is CI run 40572130840 (head `027ce7267`) succeeded; APK artefact 12765429476 (zip digest sha256 `1d939757…790`), not downloaded or installed yet. It adds:

- `files/pattern-clear-after`: ms to defer the clear itself.
- `files/pattern-relayout-now`: a relayout on demand, polled every 500 ms, like the probe.
- A tile parse log (`SCRATCH tile …`).

Set the restore with `files/pattern-gap` (`delay:<ms>`, `fullframes:<n>` or `fullafter:<ms>`). Write the files with
`adb shell "run-as com.zynergylabs.forager.app sh -c 'echo … > files/<name>'"`. The log tag is `ForagerPatternRelayout`.

Suggested order:

1. Confirm the manual trigger restores glyphs at the cap while they are missing. This reproduces the probe on this
   build.
2. Defer the clear: 2000 ms, then bisect down.
3. Read the tile parse lines to see whether a parse follows the clear and the restore.

Then change the PR to the shortest policy that works. Say what was measured. Rerun the revert checks if the class
changes.

## Left to do

- Get a working policy. Then the device steps 1–5 on the PR build: night on, night off, tile edge, out and in,
  and tufts if a wetland is found.
- Then Rowan's buildings addition (RECORD -1743, PR #426's body). Clear the app's cache first (Settings > Apps >
  Forager > Storage > Clear cache, not Clear storage), or confirm v3 some other way, and say which. Location
  10T ER 28696 19256. Check z13 see-through; z14 solid, no edge; z15–18 grey edge on every building, no tree glyph
  inside; the same by night. Mark each PASS, FAIL or OBSERVED.
- Leave the PR build on the phone at the end, and say so. The scratch build is on it now (4102+g975d61b3).
- Phone: schema `user_version` 22 before, and the PR is 22 (`ForagerDatabase.kt:206`). The DB copy is in
  `/home/zynergy-labs/s22-glyph-fix/db/` (19 tables, 2 rows). Screen timeout 1,800,000, unchanged. Night Maps is **off**
  now.
- Logs and screenshots are in `/home/zynergy-labs/s22-glyph-fix/` (`session.log`, `session-gap.log`, `shots/`). The
  helpers `shot.sh`, `count.py` and `toggle.sh` are in this session's scratchpad `tools/` and are not carried over;
  rewrite them.
- Scratch branches to delete when done (owner's call): `scratch-prf-f0-no-relayout`, `scratch-prf2-r1-no-restore`,
  `scratch-prf2-r2-no-generation-check`, `scratch-prf-gap-probe`.
- Side finding, not touched: `MapListeners.kt`'s tile error logger binds `onTileAction` as `(operation, z, x, y, …)`.
  The report says MapLibre's order is `(op, x, y, z, …)`, so its error lines mislabel tile numbers. #427 touches that
  file.
