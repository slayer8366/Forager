# Dispatch 2026-11-04-03: the v4 clone layers take their originals' night colours (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2043. The owner: "Start the night colour fix for the clone layers". This is required before any promotion (RECORD -2018). The new v4 (#474, #477) adds 78 clone layers for the first zooms, listed in the style's `forager:layer-clones` metadata. The app's night tables (TrailNightTables.kt, NightColour.kt) are keyed by layer id, so 13 clones lose their named night exception and draw the plain inversion. Aspen's table is in PR #474's body, under "Night in test builds" (`gh pr view 474 --json body`): clone, original, property, intended night colour with its source line, and today's value. Every fact here is a premise to re-check.

## A. What changes
- **A1.** At night, every clone layer in `forager:layer-clones` gets exactly the night colour its original gets: the same tables, the same exception, for every property the tables cover (line-color, fill-color, and any others). The 13 named in #474's body are the known cases. Find any others by reading every clone against the tables.
- **A2.** Done by mapping each clone to its original through the style's own `forager:layer-clones` metadata when the night tables are looked up. Nothing is hard-coded per layer, so a future clone works too. If the metadata is missing or malformed, the app falls back to today's behaviour and logs it once.
- **A3.** Both trail-v4 and street-v4 are covered (they are one style, D1), along with any offline or downloaded v4 render path, and the night images if they are keyed by layer.

## B. Tests and rules
- **B1.** A test, which fails first: for every clone in `forager:layer-clones` of map-style/forager-trail-v4.json, `trailNightRecolourOf` (or its current name) for the clone's property equals the original's. The 13 named cases are asserted with the colours from #474's table.
- **B2.** A revert check on a throwaway CI branch, the build log checked for compile errors. Full CI green on the last real commit. A night render or screenshot check, if one exists in the tests.
- **B3.** Branch `clone-layers-night` from origin/pre-main, 0fa3a936a or later. Report before the PR.
- **B4.** CI only, no Gradle on the laptop. Don't touch the phone. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object. Our own map is the "Trail Map". Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name. Keep OSM-dependent code behind its boundary (-2001). Before deleting anything in a shared scratch folder, list it and delete only your own files. Use a worktree at /mnt/work/forager-wt/ if the root disk is short.
