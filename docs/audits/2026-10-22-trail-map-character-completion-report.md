# Trail Map character with its night colours: completion report

Dispatch 2026-10-22-03, Amendment 1 (RECORD -1561; read from `origin/records-after-173` at `3b4ae54b1`). Coder: Hazel
(Claude Opus 5.5). Planner: Rowan. Written 2026-10-22, about 23:35 UTC (16:35 PDT).
The verify-first report this builds on: `docs/audits/2026-10-22-trail-map-character-verify-first.md` on branch
`trail-map-character-verify` (`c58f8d23`).

**Nothing is deployed or uploaded.** The Worker, R2 and the S22 were not changed. The S22 launch check was not run:
the phone dropped off USB before the APK was ready (below).

## What landed

Branch `trail-map-character`, cut from `origin/pre-main` at `563e9b756` (checked against the remote at the start). PR
**#379** into pre-main.

| Commit | What it does |
|---|---|
| `051314d3d` | The v2 style and the sprite v5 build: rulings 1-7 in `map-style/overrides/forager-character.mjs`; the generator writes `street-v2.json` only (v1 frozen by sha256 in the night checker); `fetch-assets.sh` fetches the eight cut-outs; `tools/build-sprite-v5.mjs` builds v5 with pinned `pngjs` 7.0.0; the night checker takes the ruled limits. |
| `46d76bbf8` | The app and the Worker. The app loads v2, takes v1 offline, and draws the ruled night colours. The Worker serves `/style/street-v2.json` and `/sprites/v5/` beside v1 and v4, which are unchanged. |
| this report's commit | This report and its index row. |

The tree was clean after each push. The eight revert branches `trail-map-character-revert-1` to `-8` are on the
remote. They are evidence only and must not be merged.

## Verification before building

- **Base:** `origin/pre-main` was `563e9b756`, the commit the dispatch and the verify-first both assumed.
- **Cut-outs, pinned:** Forager-cartography `236957d3` (main on 2026-10-22). It is the newest commit that holds the
  eight ruled day cut-outs unchanged. Since they were filed at `ef4cb60`, the only change under
  `assets/sprite-drafts/2026-10-22/` is one line of its README (`git diff --stat ef4cb60 origin/main`). The archive's
  sha256 is `7ed1e0f1…e48e`. It was read twice, identical. `fetch-assets.sh` pins it (trust on first use, as for v4)
  and checks each of the 16 files against `map-style/sprite-v5-items.sha256`. Those lines are copied from cartography's
  own `SHA256SUMS`: 16/16 OK.
- **The tiles carry what the layers filter on.** This was unverified in the verify-first; it is now read from six live
  tiles fetched from the Worker (read-only GETs, `x-forager-origin: r2`) and decoded:
  - `wetland` and `bare_rock` are `landuse` kinds (z13/1302/2919: 21 wetland; z12/666/1432: 11 bare_rock).
  - Peaks carry `elevation` (Avalanche Mountain 1634).
  - Parks and reserves are `pois` points with `min_zoom` (Mount Rainier National Park, `nature_reserve`, 8; Gifford
    Pinchot National Forest, `forest`, 9).
  - Saddles, shelters and huts were not in the six tiles sampled (below).
- **The tufts are no exception:** night tufts are the plain inversion `#2485ad`. Amendment 1 says so, and so does the
  cartography record's "Night tufts, final" entry ("Keep #2485ad"). My `#163847` proposal is withdrawn.

## What was built

**The style (v2).** `overrides/forager-character.mjs` (207 lines, new) applies rulings 1-7 after the Street design,
with values from the character record:

- the tree glyph pattern `landuse_forest_glyphs` from z12 on forest and wood;
- `water_stream` from z13, at 0.6, 1, 1.5 and 2.5 px for z13, z14, z15 and z18, in `#3fa0c8`. It fails the build if
  the stream label's zoom is not the line's.
- `pois_area_names`, as the record gives it;
- peak heights in feet, in a 0.85-scale line (`number-format` with locale `en-US`);
- wetland and bare rock in the park fill, plus `landuse_wetland_tufts` from z10.

`forager-street.mjs` now:

- adds saddle, shelter, alpine_hut and wilderness_hut to the outdoor list, drawn as the dot (no icon was ruled for
  them);
- gives the six kinds their sprite icons (`trailhead_b`, `viewpoint_c`, `parking`, `camp_site`, `picnic_site`,
  `ranger_station`);
- sets night water to `#163f4b`.

v2 has 93 layers, against v1's 90. v1's three files are byte for byte what they were.

**The sprite (v5).** v4's 53 icons, unchanged, plus a row of the eight cut-outs, at 1x and 2x (61 images). Built by
`npm run build:sprite`. `--check` rebuilds it in memory and compares decoded pixels and the index, not bytes. Every
build re-reads its output and confirms each v4 icon and each cut-out pixel for pixel. The cut-outs carry no licence
file or credit (the owner: "As the project's own art"). v4's MIT credit stays: `sprites/LICENSE-MIT.md` on R2, which
covers both folders.

**The app.**

- `STREET_STYLE_URL` is v2. `STREET_STYLE_V1_URL` and `STREET_STYLE_URLS` are new.
- `offlineRenderStyleUrl` tries v2, then v1, then `offline.json` (the trap the verify-first found).
- `usesStreetSprite` takes both v1 and v2.
- `STREET_NIGHT_WATER` is `#163f4b`.
- `STREET_NIGHT_FILL_COLOURS` maps day fill colours to night ones:
  - forest `#a9d39b` to `#446835`;
  - wetland `#c5e3d6` to `#2a4f47`;
  - bare rock `#c9c4bb` to `#6e7580`.

  The map applies inside any `fill-color` expression, matching on parsed RGB at full opacity; every other colour in the
  same expression is inverted.
- New `NightPattern.kt` (49 lines):
  - paints the tree glyph `#5d804f`, keeping each pixel's alpha;
  - `applyStreetSpriteNight` walks the icons, then the patterns, through one per-name function.
- The six icons and the tufts are on the inversion list (60 names, equal to v2's `forager:sprite-icons`).
- v1 regions show the existing -1294 notice (the owner: "The existing notice").

**The Worker.** `/style/street-v2.json` is bundled beside v1. The sprite path accepts `v4` or `v5` only. v1, v4,
`offline.json` and the tile routes are unchanged.

**Not touched:**

- trail names (ruling 8a: no style change, a phone check below);
- hillshade, contours, scrub dots;
- the offline style;
- Topographical;
- `.github` (the diff is empty);
- `wrangler.toml`;
- anything on R2.

**Sizes:**

- New files: `forager-character.mjs` 207 lines, `build-sprite-v5.mjs` 114, `NightPattern.kt` 49.
- Largest touched: `server/pmtiles-worker/src/index.ts` 605, `forager-street.mjs` 407, `NightColour.kt` 352.
- No hand-written file passes 800. The generated v2 JSON is 15,351 lines per copy, outside the rule (Amendment 1).

## Evidence

**Unit tests**, CI run [39869410122](https://github.com/slayer8366/Forager/actions/runs/39869410122) on the PR's merge
commit, against pre-main's run 39846205013 at `563e9b756`, compared by test ID:

| | Before | After |
|---|---|---|
| App unit tests | 5263, 0 failures, 24 skipped | 5272, 0 failures, 24 skipped (the same 24 IDs) |
| Worker route tests | 21, 0 failures | 23, 0 failures |

- **Removed:** `NightWaterTest#night water reads at 3 to 1 or better …`. This is the 3:1 rule the owner's ruling
  replaces.
- **Ten new tests:**
  - three in `NightWaterTest` (the ceiling, 1.05:1 on every night land fill, the existing "every water fill" test
    reworked);
  - five in `NightExceptionsTest` (fill and pattern colours equal the design's; the park fill's night by branch; the
    tree glyph at 1.35:1; the alpha-kept paint; every fill pattern redrawn once);
  - two in `StreetStyleTest` (v1-only regions load v1; v2 on sprite v5 with v1 unchanged);
  - one in `OfflineRegionOlderStyleNoticeTest` (a v1 region shows the notice, through the real row composable).
- **Existing tests extended to cover v1 and v2:** `OwnStreetStyleSourceTest`, `NoSignalTrailMapTest`,
  `BasemapStyleTest` and `OfflineNightRecolourTest` (101 and 103 colour properties).
- **The new 1.05:1 test reads its sample from the style through the app's own `streetNightRecolourOf`.** It asserts
  that the sample holds the fills that could fail it (`#446835`, `#2a4f47`, `#6e7580`, and the closest, night grass
  `#2d3911`), plus at least 20 colours.

Other jobs in that run, all green: Android lint, the release compile, the Worker build and tests, and the route cutter.

**Revert checks.** One line each, on its own branch from `46d76bbf8`, run on CI with a test filter. For every run, the
compile step was read before the results ("Build the debug APK" succeeded, or for R8 the Worker's "Build the bundle"
did). Each failure is one only that edit can cause. The forward branch was then confirmed still to hold all eight
lines (grep, HEAD `46d76bbf8` on the remote).

| # | Edit | Run | Failed with |
|---|---|---|---|
| R1 | `STREET_NIGHT_WATER` back to `#8ec5f0` | 39871327014 | `NightWaterTest`: design `#163f4b` vs `#8ec5f0`; and "night water #8ec5f0 has luminance 0.5197… and blue 240" |
| R2 | the fill-exception line removed from `streetNightRecolourOf` | 39871335984 | `NightWaterTest`: differing fills `[]`, and "no night land fill #446835 read from the style"; `NightExceptionsTest`: park fill `[67, 109, 53]` where `[68, 104, 53]` |
| R3 | `offlineRenderStyleUrl` back to v2-or-offline | 39871344731 | `StreetStyleTest`: only v1 regions expected `street-v1.json`, was `offline.json` |
| R4 | `usesStreetSprite` back to v2 only | 39871351424 | `OwnStreetStyleSourceTest#the Street sprite is inverted at night only for the own Street style` |
| R5 | pattern paint drops alpha | 39871357942 | `NightExceptionsTest`: alpha-kept pixels came back opaque |
| R6 | patterns inverted instead of painted | 39871364190 | `NightExceptionsTest`: tree pixel `0xFF9CD898` (the inversion) where `0xFF5D804F` |
| R7 | patterns left off the night list | 39871370540 | `NightExceptionsTest`: "tree_dark_irregular is not redrawn at night" |
| R8 | the Worker's v2 route renamed | 39871376658 | Worker test `/style/street-v2.json …`: 404 !== 200 (the app jobs green) |

**`npm run check:street`** (local Node 24.21.0, below) exits 0 on `46d76bbf8`:

- the sprite matches a fresh build;
- style-spec valid, and the bundled copy is identical;
- v1 is unchanged (three sha256 sums);
- water `#163f4b` passes at luminance 0.0423 and blue 75;
- water is at least 1.08:1 on 22 night land fills;
- the tree glyph is 1.42:1 on `#446835`;
- every existing floor holds;
- the trail spec holds over 6643 cases.

**The old water shown failing:** with `nightWater` set back to `#8ec5f0`, the checker exits 1 with "night water
#8ec5f0: luminance 0.5197 (at most 0.05), blue 240 (at most 96)". A second revert, with the night forest back to the
inversion `#436d35`, exits 1 with "night tree glyph on night forest: #5d804f on #436d35 1.34:1 (minimum 1.35)". Both
were restored from saved copies; the forward checker then exits 0.

**The dex report** (`scripts/verify-dex-register-budget.sh` on the CI APK, report only) gives the same five methods,
with the same register counts, as pre-main's APK from run 39846205013. This change adds nothing there.

**The `.github` diff is empty.** The full diff against `origin/pre-main` is 31 files. Most lines are the three
generated v2 JSON copies.

**Node, a premise to correct.** The dispatch's rules say there is no Node here. There is: the Node 24.21.0 that earlier
map-style work used, at `~/from-flash/map-style/node`, and CI has no map-style job. Without it, the generated v2 JSON
and `check:street`, both required by the dispatch, could not exist. So it was used for map-style only: generation,
checks, the sprite build and the renders. Gradle ran only on CI.

## Scratch renders (for the planner, off GitHub)

`~/.cache/forager-wt/renders-trail-map-character/` on the laptop. Twelve PNGs, 412x800 at 2x, in MapLibre GL JS 6.12.0
(the web engine) with tiles from the live Worker:

- `forest-park-z12`, `-z13`, `-z14` and `-z15`, each `-day` and `-night`;
- `paradise-z13` and `-z14`, each `-day` and `-night`.

Night is drawn the app's way, by a scratch script that mirrors `NightColour.kt` and `NightPattern.kt` on the v2 preview
style and sprite. There were no console errors.

Observed:

- the irregular glyph on Forest Park, day and night;
- streams in `#3fa0c8` by day and `#163f4b` by night;
- "Alta Vista / 5,961 ft";
- the picnic icon;
- the wetland tufts at Paradise;
- the italic area names. "Forest Park" and the locality "FOREST PARK" both show at z12, as Sage-03 reported.

No bare rock falls in these two views. The phone renders with Native 13.5.0, which can differ, labels especially.

## Deploy order, with exact steps

Old apps load v1, which never changes, so none of these steps changes anything on a phone that has not updated.

1. **R2: upload sprite v5** (the owner's go; Aspen or the owner uploads).
   - The files are staged at `~/.cache/forager-wt/r2-upload-sprites-v5/sprites/v5/`, four files, with `SHA256SUMS`
     beside them:

     | File | sha256 |
     |---|---|
     | `light.json` | `5d4f7869…73e4` |
     | `light.png` | `6a1ddbf3…8622` |
     | `light@2x.json` | `073f4064…db9b` |
     | `light@2x.png` | `215987d8…c838` |

   - Rebuild and compare if you like: `./fetch-assets.sh && npm run build:sprite` in `map-style`. Matching pixels are
     what counts; the PNG bytes can differ with zlib.
   - Upload to the `forager-maps` bucket under `sprites/v5/`, keeping the names. Do not touch `sprites/v4/`,
     `us.pmtiles` or `overflow/`.
   - Check: the four objects are listed under `sprites/v5/`.
   - **Risk:** none for phones. Nothing references v5 until step 2. A wrong upload is fixed by uploading again, before
     step 2.
2. **The Worker: hand paste** of the CI `pmtiles-worker-dist` artifact (`dist/index.js`) from the merged pre-main run.
   This is the owner's hand paste.
   - Check in a browser:
     - `…/style/street-v2.json` names `sprites/v5/light`;
     - `…/sprites/v5/light.json` lists `trailhead_b`;
     - `…/style/street-v1.json` still names `sprites/v4/light`.
   - **Risk:** if it goes before step 1, phones are unaffected (they load v1). A test phone running this PR's APK
     would draw v2 with no sprite. Whether MapLibre then reports the style as failed, which would trip the -1513
     fallback, is not verified. A bad paste is rolled back by pasting the previous bundle; v1 is in both.
3. **The app**, after both, through the usual pre-main to main promotion.
   - **Risk:** if it goes before step 2, `street-v2.json` answers 404. The -1513 fallback draws OpenStreetMap's map
     until restart, and Trail Map downloads fail. Not bright, not blank, but the wrong map.
   - Rolling the app back is safe: v1 never changed.

## Not verified

- **The S22 launch check.** The phone was attached earlier in the session (the installed build was `1.0.3807+ga317ca1a`,
  schema 22, the same as this branch's). It was gone from `adb devices` when the APK was ready, and still gone at
  23:30. The APK is run 39869410122's `app-debug-apk`, `1.0.3823+g5eeb1ade`, sha256 `267cc100…7410`. To run it:
  `scripts/s22-launch-check.sh <apk>`. It installs with `-r` only; there is no schema change.
- **On a phone, at night, with the Worker and R2 deployed:**
  - whether MapLibre Native picks up a pattern image replaced at run time;
  - whether expression colours come back in a form the fill exceptions match;
  - whether `number-format` with a locale, and `in` on a string, draw on Native.
- **Saddle, shelter and hut kinds** were not in the six tiles sampled, so whether the tiles carry them is unknown. Their
  dot draws only if they do.
- **Renders are the web engine,** not the phone's.

## Device checklist for the owner (S22, after steps 1 and 2)

Each step: where, what passes, what to keep.

1. **Launch.** Run `scripts/s22-launch-check.sh` with the APK. Passes: "PASS". Keep: its output.
2. **Forest, day:** Forest Park, z13. Passes: scattered dark-green tree marks on the forest. Keep: a screenshot
   (off GitHub).
3. **Forest, night:** the same view at night. Passes: dim green marks (`#5d804f`) on a green forest (`#446835`), not
   bright `#9cd898`, and none missing.
   - Bright marks: the image was inverted, not painted.
   - No marks: the pattern image replacement did not take.

   Either way, keep logcat's "Street night: … redrawn" line.
4. **Water, night:** the Willamette. Passes: dark teal river and creeks, nothing lighting the screen.
5. **Wetland:** Ridgefield refuge, z13, day and night. Passes: a pale green-blue fill with blue tufts by day; a dark
   teal-green fill with mid-blue tufts at night.
6. **Peak:** Paradise, z14. Passes: "Alta Vista" over "5,961 ft". If the feet line is missing or reads "5961", Native
   drew `number-format` differently: keep a screenshot.
7. **Trailhead and viewpoint:** any trailhead and viewpoint at z12 or above, day and night. Passes: the walker and the
   binoculars in green rounded squares by day, and dark squares with green rings at night.
8. **Bare rock above treeline:** Snow Lake or Paradise high ground, z12 to z13, day and night. Passes: a warm grey by
   day, a cool slate (`#6e7580`) at night.
9. **Area names:** Mount Rainier at z8 to z10. Passes: "Mount Rainier National Park" in italics.
10. **Trail names, ruling 8a:** Forest Park, z14. Note whether the winding trails (Wildwood, Maple) carry their names,
    or only the straight fire lanes. This is an observation, not a gate: the owner rules on it after.
11. **An area downloaded under v1** (if one exists): the row shows "Older map style. Download again to update.", and
    offline with no signal that area still draws.
