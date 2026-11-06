# Re-look 2026-10-30: PR #448, Street v4 at `e9be0bcdd`

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-30-sorrel-street-v4-relook.md`
in Forager-cartography. It follows `2026-10-30-sorrel-street-v4.md` (the review of `f228f9281`, on this branch at
`11959e944`). Started about 20:20 PDT, finished about 20:45 PDT on 2026-10-30. Times are Pacific. Each claim is
marked **read**, **computed** or **inferred**.

The Pi lost power at 15:17 to 15:23; my first review's scratch was gone, so everything here was rebuilt from the
repositories. The renders show place names and stay in `/tmp/sorrel2/render/shots/` on the Pi.

## Prediction

Not written down before computing this time; recorded here as formed on reading the dispatch, and scored at the end:
(1) v4 rebuilds exactly from the rulings; (2) the by-day-colour table cannot reach v1 to v3 or `StreetWithTopoOver`,
because their tables carry an empty map by default; (3) with `#333333` the trails are the strongest line at night in
the forest too; (4) the inverted land edges will be more visible at night than by day, because a line a shade darker
than a pale fill becomes a line a shade lighter than a dark one, next to a much darker ground.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| `street-v4` and PR head | `e9be0bcdd` | `e9be0bcddbfe028cc43edb559f89bc56f1dc586f`, PR OPEN, base `pre-main`, `MERGEABLE` / `CLEAN` |
| `pre-main` | `b493cffeb` | **`1ac67f12b`**: four commits since, PR #451 (the Heights credits), touching two files under `app/.../credits/` only. No overlap with this PR's files; the merge-base of `street-v4` and `pre-main` is still `b493cffeb`; v3's hashes on the new `pre-main` are unchanged. A stale line in the dispatch, not a wrong target: recorded, and the review went on. |
| Full CI | 41162267089 | `e9be0bcdd`, success (5,970 tests, 0 failures in the JUnit XML) |
| Test-first | 41133078333, 41135796965 | `f7bd77d07` failure, `871a49101` failure |
| Reverts | 41162544051, 41162546044 | `street-v4-revert-rail` `2d6353d0a` failure; `street-v4-revert-outline` `56cd4f1db` failure |
| S22 APK | 41162594972 | `street-v4-s22-debug` `975cdea0b`, success; `975cdea0b` is `e9be0bcdd` merged into the debug branch, whose only change is `STREET_STYLE_URL` → the branch's raw v4 file. `app-debug.apk` sha256 `28fef74ee6e52e96ca75648e88d543b0877462f7378a22a1aaeaead895b8d667` (computed; the dispatch gave no value to match). |
| v3 | `e2a25982…`, `beb40e3a…` | `e2a259820bd1dda6…` (both copies) and `beb40e3a0e5624da…` on `street-v4` and on both `pre-main` heads |

No `build-usca` process. The clock is NTP-synchronised (the dispatch's "about 20:35" was ahead of the Pi's 20:20).

## 1. Does v4 equal the rulings, every value?

**Yes, exactly (computed).** My rebuild script starts from `pre-main`'s v3 and applies: the draft (`mkdraft.py`) and
the ramp match, as before; the motorway centres `#e892a2` on the four centre layers and `#dc2a67` on the five casings,
tunnels untouched; `landuse_edges` and `water_edges` after `landuse_pier` with the rulings' thirteen edge colours,
width 0.5/0.75/1 at z13/15/17, minzoom 13, residential, commercial and railway land from z14; industrial `#ebdbe8`;
buildings `#d9d0c9` with `#ccc0b7` at z14 and the v3 opacity step kept; the outline `#b9a99c`. All 101 layers equal
the committed `forager-street-v4.json`, in order. Against v3: 4 layers added, 20 changed, nothing else; the preview
has the same layers and metadata.

- **Night metadata (computed):** `forager:night-layers` is v3's minus the seven ruled to invert (`roads_major`,
  `roads_bridges_major`, `roads_highway`, `roads_bridges_highway`, `roads_link`, `roads_bridges_link`, `roads_rail`);
  `forager:night-fills` is v3's three plus the five ruled; `forager:night-line-colours` is
  `{roads_major: {#ffffff: #333333}, roads_bridges_major: {#ffffff: #333333}}`. v3's metadata has no
  `night-line-colours` key. The inversions the rulings name hold: motorway `#7f2939`, casing `#e83673`, rail `#636c6f`,
  tan `#372e27`, z14 `#493d34`, outline `#645447`, industrial `#21111e`.
- **The edge groups (read):** each group of kinds is exactly a v3 fill's kind list (forest/wood and the grass kinds
  from `landuse_park`, urban green, school, hospital, industrial, the new settled and park fills), so every edge
  follows the fill it borders. The `#000000` fallback in the edges' match is unreachable: the filter admits only the
  listed kinds (inferred from the expression).
- **OSM Carto (read, the files at `9295745d` fetched):** `@motorway-fill: #e892a2` (road-colors-generated.mss:11),
  `@motorway-casing: #dc2a67` (line 7), `@industrial: #ebdbe8` and `@industrial-line: #c6b3c3` (landcover.mss:22-23),
  `@building-fill: #d9d0c9` with `darken(…, 15%)` and `darken(…, 4%)` (buildings.mss:1-3). My own HSL darken gives
  `#b9a99c` and `#d1c6bd`, as Willow computed (computed). The z14 step `#ccc0b7` is the owner's one-step-darker
  choice: its night is 1.083:1 on the night water, Carto's `#d1c6bd` 1.001:1 (computed).
- **Regeneration and checkers (computed, my worktree at the head):** `generate-street.mjs` reproduces every file byte
  for byte; `check:street:v4` and `check:street` exit 0. The checker prints the night tertiary at 1.97:1 on the night
  forest (band 1.7 to 2.3), 1.03 on grass and 1.27 on ground, each under the night footpath.

## 2. The night-line-colours mechanism

**Can it reach v1 to v3 or `StreetWithTopoOver`? No (read).** `StreetNightTables.lineColours` defaults to
`emptyMap()`; `STREET_NIGHT_TABLES`, the object every pre-v4 source and `StreetWithTopoOver` resolve to, is built
without it. In `streetNightRecolourOf` the new branch is `tables.lineColours[property.layerId]`, `null` for every layer
under those tables, so the code falls through to the fill branch and the pure inversion exactly as before. The
selector and `MapStyleReload.kt` are unchanged since `f228f9281`; the full run's v3 suites pass.

**Does every non-named colour in the match still invert? Yes (read, `colourNightOf` and `nightOfColourLiterals`).**
The expression walker visits every colour literal (hex, `rgb()`/`rgba()` strings, and `["rgba", …]` arrays); for each,
`colourNightOf` returns the table's colour only when the day RGB is a key and alpha is 1, else `day.night()`. So in the
major-road match, trunk, primary, secondary and the `#f6d68f` fallback invert, and only `#ffffff` becomes `#333333`.
Two edges worth knowing: the table is keyed by colour, not by class, so any other `#ffffff` literal in those two
layers' `line-color` would also go dark grey (there is none; the checker's "none is dead" test covers the other
direction); and a layer in both `layerColours` and `lineColours` would take `layerColours` first (none is).

**Tested against the design, not the code? Yes (read).** `NightExceptionsV4Test`: "v4's night is the ruled table"
hand-writes `0xFFFFFF to 0x333333` for both layers; "the major roads and their ramps invert, but the tertiary is dark
grey" hand-writes `rgba(51, 51, 51, 1)` for tertiary and its link and checks the fallback is the inversion of
`#f6d68f`; "the night tertiary sits under the trails" recomputes the band and the ceiling from the palette; "line
colours by day colour are the design's" ties the app's table to the metadata. The style checker's section 7 holds the
same by hand and checks each named day colour is one its layer draws.

## 3. Do the trails stay the strongest line?

**Yes, by day and by night, at every view (computed, and read from the renders).** Contrast of each line against the
ground / grass / forest it lies on, day then night:

| Line | Day | Night |
|---|---|---|
| Footpath (the reference) | 4.45 / 4.65 / 3.52 | 6.47 / 4.97 / 2.58 |
| Motorway centre | 1.74 / 1.82 / 1.38 | 1.74 / 1.34 / 1.44 |
| Motorway casing | 3.46 / 3.61 / 2.73 | 3.98 / 3.05 / 1.59 |
| Trunk | 1.27 / 1.32 / 1.00 | 2.41 / 1.85 / 1.04 |
| Tertiary | 1.33 / 1.27 / 1.68 | 1.27 / 1.03 / 1.97 |
| Rail | 2.24 / 2.34 / 1.77 | 2.98 / 2.29 / 1.19 |
| Forest edge | 1.65 / 1.73 / 1.31 | 3.50 / 2.69 / 1.40 |

Every road, the rail and every edge sit under the footpath on all three grounds, day and night. The black tertiary
of my first review (3.28:1 on the night forest) is now 1.97:1, inside Aspen's band; the rail's inversion `#636c6f` is
under the footpath everywhere, where `#a3acae` had been just over it. The motorway casing is the strongest road line
in both modes and still under the trails. Trails are also the only dashed lines; motorways are wider, as they always were.

**The renders (GL JS 6.12.0 in headless Firefox 142, 412×800 at 2×, tiles from the Worker, my own night emulation of
`NightColour.kt` at this head, each version with its own metadata tables; 16 shots, no style errors, every tile 200):**

- **z9 Canby–Silverton.** Day: as before, now with Interstate 5 and the 205 in Carto's pink with the darker pink edge,
  which lifts the motorways clear of the amber trunks they had matched. Night: the fills quiet, the road web brown, the
  motorways a dark red core with a bright pink edge that reads as a pink line. No trails at z9.
- **z11 Oregon City.** Day: pink 205 through grey residential and green parks; white tertiaries. Night: pink motorway,
  brown majors, tertiaries as grey casings with a dark-grey centre. Edges start at z13, so none here.
- **z12 inner Portland.** Day: the three freeways pink over the amber grid, buildings a faint warm tan at half
  opacity, the lilac industrial strips along the river. Night: the dense grey lattice of before, the freeways pink,
  buildings dark brown.
- **z13.5 Silver Falls.** Day unchanged from the first review. Night: the tertiary is now a dark grey centre inside its
  grey casing, clearly weaker than the salmon trail dashes beside it; in the first review it was a solid black line.
  The forest and park edges are visible here as thin lines a shade lighter than the night forest.
- **z15 Oregon City.** Day: tan buildings with their outline, residential grey with a faint edge, parks and the
  forest with a slightly darker green edge, white tertiaries, trail dashes at the park. The edges are faint, as ruled.
  Night: buildings as dark-brown blocks with a lighter outline, streets as grey casings, the park and forest edges as
  light-green outlines, the trail dashes the strongest line. The night edges read more strongly than the day ones
  (prediction 4): the forest edge is 1.65:1 against the day ground and 3.50:1 against the night ground. Still thin
  (0.75 px at z15), but no longer "faint" in the day sense.

## 4. The earlier should-fixes

- **2, the checker guards every fill: closed (computed).** Dropping residential's night colour now fails
  `check-street-night.mjs v4` with "night fill for residential #d5d1cb is undefined, ruled #2d2a25" (my first
  review's revert, re-run at this head). Section 7 also pins the inverted layers and the night-line table by hand.
- **3, the rail comment: closed (read).** The claim is gone with the rail's exception; the only "under the night
  footpath" sentence left is about the tertiary, and it is true (1.97 against 2.58).
- **1, `StreetWithTopoOver` passes `null`: still open, still correctly left for the switch PR (read).**
  `MapStyleReload.kt` is unchanged; the gap is inert until `STREET_STYLE_URL` is v4.
- **4, no map-style checker in CI: still open, still a separate Node job (read).** `.github/` is unchanged. The new
  sections 6 to 8 of the night checker are the strongest design guard in the PR and CI never runs them; the app tests
  carry the design values a second time, which is what keeps this at should-fix.

## 5. Revert checks

**The coder's two (read, JUnit XML):** the rail revert (the app table given `roads_rail` back `#a3acae`) fails 3
tests, all about the rail or the table; the outline revert (`#a6a6a6`) fails 2, both in `BuildingsNightV4Test` about
the outline. Both ran a filtered 18-test set, so "only that" holds within the filter. The test-first runs fail 3
(`f7bd77d07`: tertiary, motorways and rail) and 4 (`871a49101`: three building tests and the missing `landuse_edges`),
each on the feature the commit names.

**My own, in CI:** never-merged branch `sorrel-revert-tertiary` at `ed167909b` (`e9be0bcdd` plus one line: the app's
night tertiary back to `0x000000`), run 41164487137 with the full suite: 5,969 tests, exactly 3 failures, all in `NightExceptionsV4Test` and all about the tertiary ("v4's night is the ruled table", "line colours by day colour are the design's", "the major roads and their ramps invert, but the tertiary is dark grey"; read from the JUnit XML). One test I expected to fail did not: "the night tertiary sits under the trails" passed with the app at black, because it hard-codes `val tertiary = 0x333333` instead of reading the app's table, so it checks the ruling's number, not what the app draws. Should-fix 3 below.

**My own, style side (computed):** residential's night fill dropped → the checker fails (should-fix 2 above).

## Verdict

### Blocking

None.

### Should-fix

1. **(Carried) For the switch PR:** `streetNightTablesFor((source as? MapStyleSource.Uri)?.uri)` gives
   `StreetWithTopoOver` v3's tables; pass `source.streetUri` and test it before the switch.
2. **(Carried) A CI job for the map-style checkers.** The night checker now holds the rulings by hand in sections 6 to
   8 and nobody but the Pi runs it.
3. **The band test should read the app's value.** `NightExceptionsV4Test`'s "the night tertiary sits under the trails"
   should take the tertiary from `tables.lineColours.getValue("roads_major").getValue(0xFFFFFF)` rather than the literal
   `0x333333`; as written it passed with the app's table at black (my revert run). The three table tests still catch
   that drift, so this is a weak test, not a hole.

### Notes

- **Night edges, for the owner to see on the S22:** the ruling says "night is the plain inversion" and the coder built
  that; the option label "No edge at night" referred to the water edge, which the water rule paints into the water.
  The inverted land edges are more visible at night than by day (forest edge 3.50:1 on the night ground against
  1.65:1 by day). If the owner meant faint edges at night too, that is a design pick, not a code fix.
- The dispatch's `pre-main` head is stale (PR #451 landed); nothing in it touches this PR.
- The motorway casing is the strongest road line now, under the trails; the pink reads as the OSM look the owner asked
  for.
- The by-day-colour table matches `#ffffff` wherever those two layers draw it; today that is only the tertiary and its
  link.
- My branch `sorrel-revert-tertiary` stays on origin for the record; delete it with the others.

### Prediction, scored

(1) held; (2) held; (3) held, and by a clear margin; (4) held (3.50:1 against 1.65:1). Nothing unpredicted this time.

**Is PR #448 at `e9be0bcdd` mergeable as plumbing ahead of the switch?** Yes. v4 is the rulings to the byte, including
the four new sections; v1 to v3 are unchanged by file and by every app path, and the new table cannot reach them; the
trails are the strongest line by day and by night on every ground; should-fixes 2 and 3 are closed and 1 and 4 are
rightly left for the switch PR and a CI job. The owner's S22 check of this head, and the owner's go, remain theirs.
