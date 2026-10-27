# Dispatch 2026-10-27-02: buildings solid with an outline in street-v3 (a coder, Opus)

Written by Rowan Waymark, 2026-10-27, against pre-main 6064fef86. RECORD -1730.

**Authorisation.** The owner, on the S22 this morning, verbatim: "The buildings and houses are soft against the background, and the forest also overlaps them. Can we have a border around the buildings and houses to separate them from the rest of the map?" Aspen, the Pi planner, wrote the spec below (Forager-cartography 51d6358, docs/handoffs/2026-10-27-buildings-outline-and-night-glyphs.md; you can't reach that repo, so its essentials are here). The owner: "Build it (Recommended)".

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. Credit the OpenStreetMap Carto style (openstreetmap-carto, style/buildings.mss) in a code comment as the source of the structure, under its licence.

## Verify first (Aspen's reading, a premise to re-check at the current base)
- `map-style/forager-street-v3.json` layer `buildings` is a fill, `#cccccc`, fill-opacity 0.5, with no outline layer.
- `landuse_park` and `landuse_forest_glyphs` draw below it.
- No Forager override touches buildings; they come from the base style.
- At night the app's V1 rule (NightColour.kt nightColorOf) gives `#333333`.

## Change
| | Today | New | Night by V1 (check by computing) |
|---|---|---|---|
| `buildings`, z14 | `#cccccc`, opacity 0.5 | `#c2c2c2`, opacity 1 | `#3d3d3d` |
| `buildings`, z ≥ 15 | `#cccccc`, 0.5 | `#cccccc`, opacity 1 | `#333333` |
| new `buildings_outline`, line, z ≥ 15 | none | `#a6a6a6`, width 0.75 | `#595959` |

- **Layer order:** the outline sits directly above `buildings`, with the same source-layer and filter.
- **Night:** no night exception. If V1 gives something other than the night column above, report it.
- **Where it lives:** in the generator (an override used by map-style/generate-street.mjs), then regenerate v3 in all its copies. v1 and v2 stay frozen and byte-identical.
- **The style diff:** only the buildings layer's opacity and z14 step, plus the one new layer. Prove it with a structural diff.
- **Tools:** run tools/check-style.mjs and MapLibre's style validation. The step-8a or reference-water worktree's node_modules may be symlinked in; there is no npm registry. If check-street-night can't run on v3 offline, say so; it is still pinned to v2 (Sorrel's #423 S1, on the tidy list). Don't fix it here unless it's one line.
- **Tests:** update the app and Worker tests that pin v3's layers or hashes, saying which and why. Add a test that pins the new layer's paint and position. Fail first, then a revert check that follows CLAUDE.md.

## Report also
- **What a phone needs to see it.** The live Worker serves v3 from its bundled copy, so the app gets the change only after a Worker deploy. Deploys come from main, or by hand on the owner's word (RECORD -1609, -1703). Do not deploy.
- **Areas already downloaded as v3.** Do they keep the old style copy? Does the older-style notice say anything for them?
- **S22 steps** as tap > shows > Back: day and night, at 10T ER 28696 19256, zoom 15, 17 and 18. Pass: every building has a visible edge and no tree glyph shows inside one; the owner's eye decides.
- **The four disclosure sections.**

## Rules
- **Network:** none beyond GitHub.
- **Builds:** CI only.
- **Branch:** a new branch off current pre-main, a PR into pre-main, no merges.
- **The Restricted Object:** never read, name or quote it.
- **Long conversation:** past about 200k tokens, push and write a handoff.

## Amendment 1 (2026-10-27): the z14 grey is #c1c1c1 (RECORD -1733)
The coder stopped at buildings-outline 68ce84dd6. The spec's z14 `#c2c2c2` goes to night `#3d3d3d` under V1, which is 1.047:1 against night water `#163f4b`. That is below NightWaterTest's 1.05 floor (run 40550018434, the predicted failure). The owner: "A shade darker: #c1c1c1 (Recommended)". The z14 fill becomes `#c1c1c1` (night `#3e3e3e`, 1.063:1). Everything else stands as specified: `#cccccc` from z15, the outline `#a6a6a6` at 0.75 from z15, and no night exception. NightWaterTest is not changed.

Then finish the dispatch on the same branch:
- the one-value change and the expected colours in both tests;
- the revert check (drop the withBuildingOutline call and regenerate, read from CI XML, build log checked);
- the full CI suite;
- the PR into pre-main, with no merge and no deploy;
- the S22 steps.

Check the OpenStreetMap Carto licence from its repository on GitHub (LICENSE.txt) rather than from memory, and fix the credit if it differs.

## Amendment 2 (2026-10-27): the change starts at zoom 14; notes into the PR (RECORD -1737)
Sorrel's review is docs/reviews/2026-10-27-sorrel-pr426.md on branch sorrel-pr426 (a52cf2d99). It finds nothing blocking. S1: buildings draw from zoom 11, so `["step",["zoom"],"#c1c1c1",15,"#cccccc"]` with opacity 1 also changes zooms 11-13. The owner: "Change only from zoom 14 (Recommended)".

In street-v3 only, and paint only:
- `fill-color`: `["step",["zoom"],"#cccccc",14,"#c1c1c1",15,"#cccccc"]`
- `fill-opacity`: `["step",["zoom"],0.5,14,1]`

Below zoom 14, buildings are exactly as today. The outline stays as built, from zoom 15. NightWaterTest stays untouched; say whether it samples each step.

Also:
- **S2:** put the "what a phone needs" notes and the S22 steps into the PR body. Use Sorrel's review §6-7 (MapLibre refreshes a stored v3 style at the first load after its copy expires; the Worker caches for a day). Add a zoom 13 step showing today's look.
- **N1:** in BuildingsNightTest, give the type check a message before the cast.
- **Names and comments:** update the test names, comments and the credit comment to say the zoom range correctly.

Then add a revert check for the new opacity step, run full CI, and push to PR #426. No merge and no deploy.
