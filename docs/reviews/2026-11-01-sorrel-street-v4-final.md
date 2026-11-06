# Final delta review, 2026-11-01: PR #448, Street v4 at `ae647aaf4` → `pre-main` `40e9e4d3b`

Sorrel Tally, the independent reviewer (Fable), for Aspen's dispatch "sorrel-448-final". About 15:00 PST on
2026-11-01. The delta since my last full review of this PR (`97a26e8c7`, re-look 2) and since the #459 review this
afternoon. Each claim is marked **read** or **computed**. Read-only.

## Baseline (read)

PR #448 `street-v4` at `ae647aaf4` → `pre-main` `40e9e4d3b`, `CLEAN`. First-parent commits since `97a26e8c7`: the
creeks (`425eb6e64`, `c2e06dff6`), the orchard fill and edges (`11c31a9e0`), the hill shading (`00aaf7753`,
`a578ecd56`), the #459 merge (`75bd32e17`, reviewed), and the catch-up merge of `pre-main` (`ae647aaf4`). The
branch-only delta (`git diff origin/pre-main...ae647aaf4`) touches `map-style/`, the app's `ui/map` code and tests, and
the sprite test resources; nothing else. CI on the head, run 41357682799: all six checks green, **6,011 tests, 0
failures** (JUnit XML).

## 1. Each piece against its ruling (read from the committed v4 and the override; computed by regeneration)

- **Creeks under the water** (fill-in rulings, "Water fill to the creek colour", superseded by "Creeks under water
  (Recommended)": move `water_stream` and `water_river` to just before `water`, nothing else moves, the fill stays
  `#80deea`). v4: `water_stream` 22, `water_river` 23, `water` 24; the override moves exactly those two; the water fill
  is `#80deea`. v3 keeps 17/18/19.
- **Orchards, nurseries, vineyards** (sage-10 as ruled: fill after `landuse_settled`, kind farmland with the three
  `kind_detail`s, `#c8dd98`, opacity 0.6 at z6 to 1 at z9, no minzoom; pattern layer directly after, minzoom 13; the
  three in `landuse_edges` at `#b0c286`, night rim `#3a4b14`; "Standard night", the plain inversion). v4:
  `landuse_orchards` at 4 with exactly that filter, colour and opacity and no minzoom; `landuse_orchards_pattern` at 5
  (reviewed with #459); `landuse_edges` draws the three through a leading `case IS_ORCHARD → #b0c286` before the match
  by kind; `forager:night-line-colours.landuse_edges` has `#b0c286 → #3a4b14`; `#c8dd98` is not in `night-fills`, so
  its night is the inversion `#495e19` (the checker prints it, 2.21:1 on the night ground).
- **Hill shading** (global-coverage: the layer directly before `water_stream`; Sage's ALT: exaggeration 0.28 / 0.32 /
  0.24 / 0.16 / 0.10 at z9 / 11 / 13 / 15 / 17, illumination 335 anchored to the map, day shadow `#4a3f33`, highlight
  white, accent `rgba(74,63,51,0.5)`; night shadow 0.7, highlight `rgba(220,228,236,0.20)`, accent 0.35, as the app's
  own pair; hidden under Topographical; relief `z5-11`). v4: `hillshade` at 21, directly before `water_stream`, with
  exactly that paint; source `dem` raster-dem terrarium 256 px z5 to 11 at `/relief/{z}/{x}/{y}.png`;
  `forager:night-hillshade` is `{shadow rgba(0,0,0,0.7), highlight rgba(220,228,236,0.2), accent rgba(0,0,0,0.35)}`.
  The app: `StreetNightTables.hillshade` carries the same three values for v4 and `null` for v1 to v3;
  `applyStreetHillshadeNight` runs only when the chosen tables carry one; `hideHillshadeUnderTopo` sets every hillshade
  layer to `none` for `StreetWithTopoOver`; `NightExceptionsV4Test` holds the app's pair equal to the metadata and to
  the ruled values and asserts v1 to v3's is `null`. Credits: `a578ecd56` dropped the hillshade's own rows because
  `pre-main`'s Heights section already credits the data; the owner's "Leave as is" (read).
- **Sprite v6 patterns:** #459, reviewed this afternoon; unchanged by the catch-up merge.

## 2. Phones (read)

`StreetStyle.kt` is untouched by the PR: `STREET_STYLE_URL` is still `/style/street-v3.json` and `STREET_STYLE_URLS`
is v3, v2, v1. v4 is loaded by no code path, `usesStreetSprite` is false for any v4 URI, the v4 night tables (lines,
fills, line colours, hillshade) are reached only through `streetNightTablesFor` on a v4 file name, and the hillshade
night and Topo hiding act only on hillshade layers, which no v1 to v3 style has. Nothing changes on a phone until the
switch PR.

## 3. v3, v5, the checkers (computed)

`forager-street-v3.json` and the Worker's copy `e2a259820bd1dda6…`, the preview `beb40e3a0e5624da…`,
`sprite-v5-items.sha256` `b16aeac6…`; v1, v2, the v5 day sheets and `night-sprite/v5` are untouched by the branch-only
delta. In my worktree at `ae647aaf4` with the v6 sprite built, `generate-street.mjs` leaves the tree byte-identical;
`check:street:v4` and `check:street` exit 0 (104 layers, the orchards line printed, the v6 sprite with 64 images);
`build-sprite-v5.mjs v6 --check` passes.

## 4. The catch-up merge (computed)

`ae647aaf4` merges `pre-main` `40e9e4d3b` into `75bd32e17`. Their merge-base is `b493cffeb`; the set of files changed
on the `pre-main` side and the set changed on the `street-v4` side since it are **disjoint**, so git's automatic merge
resolved nothing by hand, and `git merge-tree` of the two parents gives exactly the merge commit's tree. What
`pre-main` brought (the way-home heights, the trail router, credits, heading, the Worker folder work) does not touch a
file the PR changes, and the head's CI ran the whole suite green on the result.

## Verdict

### Blocking

None.

### Should-fix

None new. Carried from the earlier reviews, for the switch PR: pass `source.streetUri` for `StreetWithTopoOver` into
the selector (today Street under Topographical would take v3's tables once `STREET_STYLE_URL` is v4; the hillshade is
hidden there anyway), and a CI job for the map-style checkers.

### Notes

- The first review's selector gap now has a second face: `tables.hillshade` is also chosen by the selector, so the
  same one-line fix in the switch PR covers it.

**Merge verdict for #448 at `ae647aaf4`: mergeable.** Each piece since `97a26e8c7` equals its ruling, the app still
loads v3 and no v4 table or layer can reach a phone before the switch, v3 and sprite v5 are byte-identical, the styles
regenerate to the same bytes with every checker green, and the catch-up merge combined disjoint files with the full
suite green on the result. The merge is the owner's go.
