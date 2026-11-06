# Review 2026-10-30: PR #448, Street v4 (the fill-in), `street-v4` → `pre-main`

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-30-sorrel-street-v4.md`
in Forager-cartography. Started about 11:15 PDT, finished about 12:00 PDT on 2026-10-30. Times are Pacific.
Every claim is marked **read** (from a file, a log or a run page), **computed** (by my own code, here on the Pi)
or **inferred**.

The renders show place names, so they stay in `/tmp/sorrel/render/shots/` on the Pi and are described in words
below (the dispatch: "keep them off GitHub").

## Mechanism prediction (written before computing)

1. v4's layers will equal `mkdraft.py` applied to v3 plus the ruled ramp match, and nothing else, because the
   override was written from the draft and the coder diffed it.
2. v1 to v3 will be byte-identical, because `generate-street.mjs` writes v3 from the same layer chain and the coder
   showed the hashes.
3. The selector on the last path part will be sound for every URI the app can produce today. Its weak spots will be
   paths where the URI is not consulted at all (`StreetWithTopoOver` carries its own `streetUri`), and a query string
   or fragment, which fails to match rather than matching wrongly.
4. Willow's `usesStreetSprite` observation will be right: a v4 URI outside `STREET_STYLE_URLS` gets the plain
   inversion, tables unused, so the PR changes nothing visible until the switch.
5. By day the trails stay the strongest line. At night the margin against the rail and the roads will be thin, since
   the OSM-like night takes the plain inversion.

Scored at the end.

## Baseline (all confirmed; read)

| Item | Dispatch | Found |
|---|---|---|
| `street-v4` head and PR head | `f228f9281` | `f228f9281238538e204551e6cf98aa46e05334af`, PR OPEN, base `pre-main` |
| `pre-main` head | `b493cffeb` | `b493cffebe6aa1e5183ad23c98c6feb0f7c54f26` |
| Full CI on the head | 41097273828 | `f228f9281`, success |
| Test-first run | 41095849138 | `439418442`, failure |
| Revert: a fill dropped | 41097417352 | `street-v4-revert-fill` `f8aa85f40`, failure |
| Revert: v4 on v3's tables | 41097421525 | `street-v4-revert-tables` `a3121c6e7`, failure |
| S22 debug APK | 41099952632 | `street-v4-s22-debug` `e4e25e87b`, success; `app-debug.apk` sha256 `6079649c…27f734b` (computed on the downloaded artifact, exact match) |
| v3 and the Worker's copy | `e2a25982…` | both `e2a259820bd1dda6bccfd7e05d5132adea6254002540cfbfdcfc04747de16b7f` on `pre-main` and on `street-v4` |
| v3 preview | `beb40e3a…` | `beb40e3a0e5624da44f52e98f0e85c52e3065cc87af5987396fbf983a8d71c97` on both |

No `build-usca` process was running (read: `pgrep`). The S22 debug branch is `f228f9281` plus one commit that
changes only `STREET_STYLE_URL` to the branch's raw `forager-street-v4.json` (read: diff of one file, one line).

## 1. Does v4 equal the ruled design, and nothing else?

**Yes, exactly (computed).** I rebuilt v4 with my own script: `mkdraft.py`'s edits applied to `pre-main`'s
`forager-street-v3.json`, with the one ruled change, the `line-color` match on `roads_major` and
`roads_bridges_major` extended to `primary_link`, `secondary_link` and `tertiary_link`. The 99 layers of the committed
`forager-street-v4.json` are equal to mine, element for element, in the same order.

- **Against v3 (computed):** 2 layers added (`landuse_settled`, `landuse_parks_fill`, after `landcover`); 8 changed
  (`roads_major`, `roads_bridges_major`, the three major casings, `roads_minor`, `roads_minor_casing`, `roads_rail`);
  nothing else. `sources`, `sprite`, `glyphs`, `forager:sprite-icons`, night bands, icon rings and night patterns
  are unchanged. `name` is "Forager Street v4".
- **Night metadata against the rulings table (computed):** `forager:night-layers` is v3's minus `roads_major` and
  `roads_bridges_major`, with `roads_rail` `#b4bec0` → `#a3acae`; `roads_link`, `roads_bridges_link` keep `#e0ad4f`
  and the motorways `#f0c060`. `forager:night-fills` is v3's three plus residential `#2d2a25`, commercial `#362824`,
  railway `#2b2829`, farmland `#2e2a10`, parks `#2c461e`. The table's inversion rows hold: my `nightColorOf` port
  gives trunk `#845104`, primary `#674400`, secondary `#463100`, tertiary `#000000`, tertiary casing `#70685a`, other
  casings `#a7853f`, all as ruled. The `#f6d68f` fallback inverts to `#634300` (not in the table; only a
  `kind_detail` outside the four classes and their links reaches it).
- **Regeneration (computed):** `node generate-street.mjs` in my own worktree leaves `git status` empty: v3 (all
  three), v4 and both previews are reproduced byte for byte. v4's preview has the same layers and metadata as v4 with
  the local asset base.
- **Checkers (computed, my worktree):** `npm run check:street:v4` exits 0 (style valid, v1 frozen, contrasts and
  night limits held, each ramp draws its road's colour, 6,643 trail cases against 17 footpath layers);
  `npm run check:street` exits 0 (v2's bundled copy identical, `build-sprite-v5 --check` passes).

One difference from the draft **as drafted**, which is the ruled one: the ramp match. Nothing else differs.

## 2. Is v1 to v3 unchanged by day and by night?

**By file: yes (computed).** The three v3 hashes above are equal on `pre-main` and `street-v4`; v1 and v2 are
untouched by the diff (the PR's diff stat lists only `map-style/` v4 files, the override, the generator, the two
checkers, `package.json`, four app files and one test). Nothing under `server/pmtiles-worker` changes. The Worker
today serves `/style/street-v3.json` JSON-equal to the repo's v3 (its bytes hash `0dd89cfe…`, a formatting
difference only; parsed, the documents are equal) and answers 404 for `/style/street-v4.json` (read: two GETs).

**By the app (read, `MapStyleReload.kt:146`, `StreetNightTables.kt`, `BasemapStyles.kt:278`):**

| Source at night | `usesStreetSprite` | `streetNightTablesFor(uri)` | Tables that act |
|---|---|---|---|
| `Uri(v3)`, `Uri(v2)`, `Uri(v1)` (online, or an offline region's style) | true | last part `street-v3.json` etc. → `STREET_NIGHT_TABLES` | v1 to v3's, unchanged |
| `Uri(OFFLINE_STYLE_URL)` (`offline.json`) | false | v1 to v3's | none: pure inversion, as before |
| `StreetWithTopoOver(v3, topo)` | true | `null` → v1 to v3's | v1 to v3's, unchanged |
| `Json(...)` | false | not called (`source !is Json` guard) | none, as before |
| `Uri(.../street-v4.json)` or `.../forager-street-v4.json` | **false today** (not in `STREET_STYLE_URLS`) | v4's | **none**: pure inversion; see question 5 |

Every pre-v4 path resolves to `STREET_NIGHT_TABLES`, which is the same two maps as before
(`STREET_NIGHT_LAYER_COLOURS`, `STREET_NIGHT_FILL_COLOURS`, both untouched by the diff), passed through a new default
parameter. `streetNightRecolourOf` with the default is the old function line for line (read: the diff replaces the two
global reads with `tables.layerColours` and `tables.fillColours`). The v3 suites (`NightExceptionsTest`,
`NightWaterTest`, `BasemapNightStyleTest`, `OfflineNightRecolourTest`, `StreetStyleTest`) are unchanged and passed on
every run (read: the JUnit XML in the three runs' artifacts shows failures only in `NightExceptionsV4Test`).

**Could v3 get v4's tables?** Only a URI whose last path part is `street-v4.json` or `forager-street-v4.json`.
No URL in `STREET_STYLE_URLS` or `OFFLINE_STYLE_URL` ends that way, and a region's `definition.styleURL` is always one
of those (it is written from `offlineDownloadStyleUrl()`), so no (inferred from the code read).

**Could v4 get v3's tables?** Two paths, neither reachable in this PR, both relevant to the switch PR:
- `StreetWithTopoOver` passes `null` (the `as? Uri` cast), so once `STREET_STYLE_URL` is v4, Trail Map under
  Topographical at night would load v4's layers and recolour them with v3's tables: the five new fills would take
  the plain inversion (residential `#332f29` instead of `#2d2a25`, and so on) and the major roads v3's amber. This is
  a **should-fix for the switch PR**, below.
- A v4 URI with a query string or fragment (`street-v4.json?x`) fails to match and falls to v3's tables. Nothing in the
  app adds one; noted only.

## 3. The selector

`styleUri?.substringAfterLast('/') in setOf("street-v4.json", "forager-street-v4.json")` (read).

- **Sound for the app's URIs (inferred):** every style the app loads by URI is a constant in `StreetStyle.kt` or
  `OFFLINE_STYLE_URL`, or a region's stored copy of one of them. The match is exact and case-sensitive on the file
  name, so `street-v3.json`, `offline.json`, `street-v4.json.bak` and `street-v4.jsonx` do not match. A host or
  path prefix is not checked, which is what lets the debug build's raw-GitHub URL and the Worker's future route share
  the tables; that is Willow's accepted call 1.
- **Accidental matches:** none among the URIs the app can produce. A query string makes a v4 URI *miss* (fails
  toward v3's tables), never a v3 URI hit. A downloaded region's URI is one of the constants.
- **The `null` case:** `StreetWithTopoOver` and any non-`Uri` source. Today correct (the street under topo is v3).
  After the switch it is the wrong answer; see question 2.

## 4. The tests

**`NightExceptionsV4Test` tests the design's values, not the code (read):**
- "v4's night is the ruled table" hand-writes the five fill pairs, rail `#a3acae`, the absence of `roads_major` and
  `roads_bridges_major`, and the motorway and link layers equal to v3's. These are the rulings' numbers typed into the
  test, so an override and an app table drifting together would still fail here.
- "the major roads and their ramps take the plain inversion" hand-writes the four road night colours as `rgba` and
  checks each `_link` equals its road, in both layers.
- "the settled and park fills take their ruled night colours" checks the recoloured expression in the design's order,
  with the earth default inverted (`#24211c`).
- "v4's layer exceptions / fill night colours are the design's" tie the app's table to the style's metadata.
- "every v4 exception reads on the night forest, grass and ground" re-applies the 2.5:1 floor to v4's table.
- "v4 is chosen by its style's name, and every other style keeps v1 to v3's tables" covers the Worker route, the raw
  branch URL, every `STREET_STYLE_URLS` entry, `OFFLINE_STYLE_URL` and `null`, with `assertSame` on the v1 to v3
  object.

Not pinned anywhere: the casing night colours (`#70685a`, `#a7853f`). They follow from inversion with no table
entry, and the v3 test "road edges not chosen keep the inversion" covers `roads_major_casing_late`, so this is a
note, not a gap.

**The two revert runs (read, JUnit XML downloaded from each run):**
- 41097417352 (parks' night colour dropped from `NIGHT_FILL_COLOURS_V4`; the diff is that one line plus the two
  regenerated JSONs): `NightExceptionsV4Test` fails 1 of 8, "v4's fill night colours are the design's" only. The
  style checker's failure (`#163f4b on #2b431d 1.04:1`) was observed by the coder on the Pi; CI does not run it.
- 41097421525 (`streetNightTablesFor` returns v3's tables for v4; one line): fails 6 of 8, the same six as the
  test-first run 41095849138 at `439418442`. That is expected (both states are "v4 with no own tables") but it means
  the second revert adds no evidence the test-first run had not already given.
- In all three runs every other suite passed.

**My own revert checks (computed):**
- *Style side.* I dropped **residential's** night colour from `NIGHT_FILL_COLOURS_V4`, regenerated, and ran
  `check-street-night.mjs v4`: it **exits 0**. The checker only caught the coder's dropped *parks* because the
  parks' inversion `#2b431d` sits at 1.04:1 against the night water, under the 1.05 floor; residential's inversion
  `#332f29` passes every gate the checker has. The app twin would still fail ("fill night colours are the design's":
  the metadata would lack `#d5d1cb` while `STREET_V4_NIGHT_TABLES` has it), so the design is guarded, but by the app
  test alone. Restored; v4's hash back to `e5e8e48d…`.
- *App side, in CI.* Never-merged branch `sorrel-revert-selector` at `0b8eeb8ea` (`f228f9281` plus one line: the
  selector's set also contains `street-v3.json`, so v3 regions would take v4's tables), dispatched as run
  41107895076: the full suite ran (815 suites, 5,961 tests; read from the JUnit XML) and exactly one test failed, "v4 is chosen by its style's name, and every other style keeps v1 to v3's tables", on the v3 Worker URL. Every other suite, `NightExceptionsTest` included, passed. The guard that keeps v3 regions on v3's night is live and specific.

## 5. Willow's observation on `usesStreetSprite`

**Right (read, `BasemapStyles.kt:278-282`, `MapStyleReload.kt:146-151`).** `applyOfflineNightRecolour` only
consults the tables inside its `streetWater` branch, and `streetWater = usesStreetSprite(source)`, which is
`source.uri in STREET_STYLE_URLS` for a URI. No v4 URI is in that list in this PR, so a v4 style loaded by URI today
gets `offlineNightRecolourOf` for every property: pure inversion, no water exception, no tables, and no night sprite
images either (`applyStreetNightImages` has the same guard). `STREET_V4_NIGHT_TABLES` is reachable only through
`streetNightTablesFor`, whose result is then ignored. So nothing the PR adds can change what any user sees, by day or
by night, until the switch PR adds the v4 URL to `STREET_STYLE_URLS`.

**Safe as plumbing (inferred):** the only behavioural edge is the new default parameters, which resolve to the old
tables for every existing source. The S22 debug build sees v4's tables only because its one-line change makes the raw
URL *be* `STREET_STYLE_URL`, so it is in the list; that is the path the switch will use too, with the Worker route.

## 6. The renders (read from the images; GL JS 6.12.0 in headless Firefox 142, 412×800 at 2×, tiles from the Worker, my own night emulation of `NightColour.kt` with each version's own metadata tables)

All 16 shots (four views × v3 day, v4 day, v3 night, v4 night) rendered with no style errors and every tile 200.

- **z9 Canby–Silverton, day.** v4 fills the Portland suburbs down through Oregon City, Canby and Woodburn with the
  warm grey of residential land, the valley floor with farmland's pale straw, and parks in light green; v3 had bare
  earth there. Roads now sort themselves: motorways and trunks amber, primaries and secondaries paler, and a fine white
  net of tertiaries with grey edges across the farmland. It reads as a settled valley rather than an empty one.
  **Night.** The fills nearly vanish (1.1:1 on the night ground, computed), which is the ruled quiet look; the road
  net becomes a dense web of dim brown lines across the whole valley, far busier than v3's night, where only the
  motorways and a thin amber net showed. Motorways stay bright amber. No trails draw at z9 (footpaths start at z12 to
  13).
- **z11 Oregon City, day.** Residential grey blocks, parks green, the river unchanged; tertiaries show as white
  streets on the grey, which is what gives the OSM-like density. **Night.** Residential is a dark warm grey against the
  near-black ground, parks a dark green; major roads are brown with amber edges; tertiaries show as their grey casing
  (`#70685a`) with a black centre, so streets read as thin grey lines. Motorway and ramps bright amber, as ruled.
- **z13.5 Silver Falls, day.** The forest with its tree glyphs, the campground icon, the creek, and the trails as the
  dashed red-brown line, unchanged. The one visible tertiary (lower left) is now white with a grey edge instead of v3's
  amber; the secondary (214) stays amber. **Night.** The trails keep their salmon dashes. The tertiary is now a solid
  **black** line about 2.3 px wide with a grey edge through the night forest: 3.28:1 on the night forest (computed),
  against the night footpath's 2.58:1 and 1.35 px. The secondary is a dim brown with an amber edge, subdued compared to
  v3's bright amber.
- **z12 inner Portland, day.** The densest change: the whole east side grey residential with the street grid in white,
  downtown's commercial pink-grey, parks green, the river as before. Trunks and primaries amber over the grid.
  **Night.** v3's night was a bright amber grid on black; v4's is a dark grey field with a fine grey lattice of
  tertiary edges, brown major roads and bright amber motorways. It is the look of the app's inverted OSM night.

**Do trails stay the strongest line?**
- **By day: yes (read and computed).** The footpath's `#a8452a` is 4.45:1 on the ground and about 3.9:1 on the forest;
  the white tertiary is 1.33:1 on the ground and about 1.7:1 on the forest; the trunk 1.27:1. The trails are also the
  only dashed lines.
- **By night: not in the forest at z13 and above (computed, and visible at Silver Falls).** Black tertiaries at
  3.28:1 on the night forest and 2.26 px beat the night footpath's 2.58:1 and 1.35 px. On the night ground and grass
  the roads are weak (tertiary 1.31:1, trunk 2.41:1) and the trails stay the strongest line. The rail at `#a3acae` is
  6.93:1 / 5.32:1 / 2.77:1 on night ground / grass / forest, slightly **above** the night footpath's 6.47 / 4.97 /
  2.58, though well below v3's `#b4bec0` (8.45 / 6.49 / 3.38). This is the consequence of the OSM-like ruling (tertiary
  `#000000`), not of a coding error: v4 draws exactly the ruled table.

## Verdict

### Blocking

None.

### Should-fix

1. **For the switch PR, not this one:** `streetNightTablesFor` is given `(source as? MapStyleSource.Uri)?.uri`, so
   `StreetWithTopoOver` always gets v1 to v3's tables. Once `STREET_STYLE_URL` is v4, Trail Map under Topographical at
   night would draw v4's layers with v3's night (inverted fills, amber majors). Pass `source.streetUri` for that case,
   and extend the selector test to `StreetWithTopoOver(v4Url, …)`. (inferred from the code read)
2. **The style night checker does not guard four of the five new fills.** Dropping residential, commercial, railway or
   farmland from `NIGHT_FILL_COLOURS_V4` passes `check-street-night.mjs v4` (computed, residential tried); only parks
   fails, and only through the water rule. Add a check that each day colour the fill-in draws has a night-fills
   entry, or that the v4 tables equal the ruled set. The app test holds the line today, so this is should-fix.
3. **A comment claims what the numbers deny.** `forager-fill-in.mjs` says the rail's `#a3acae` "keeps it below the
   night footpath on every ground"; by contrast ratio it is above on all three (computed above). The value is the
   owner's ruling and stands; the comment should say "dimmer than v3's rail, close to the night footpath".
4. **CI runs none of the map-style checkers.** `check:street`, `check:street:v4` and `build-sprite-v5 --check` only
   ran on the Pi (read: `ci.yml` has no map-style job). Nothing in CI would notice a hand edit to
   `forager-street-v4.json`'s layers, or a generator drift, unless the metadata changes. Pre-existing for v3; worth a
   Node job of its own.

### Notes

- The second coder revert (v4 on v3's tables) fails the same six tests as the test-first run; it is a restatement of
  that run rather than new evidence. The first revert (parks dropped) is the useful one.
- The night tertiary is black (`#000000`), so at night a tertiary road is carried by its grey casing on the ground and
  reads as a strong black line in the forest. Ruled ("Keep it osm-like"); the owner should know that at z13 and up in
  forest the black roads outrank the trails at night.
- `fallsBack` (`OwnStreetFallback.kt:95`) still compares to `STREET_STYLE_URL` only; unchanged by this PR and right
  for it.
- `map-style/README.md` does not describe the Street generator or `check:street:v4`; it did not describe v3 either.
- My never-merged branch `sorrel-revert-selector` (`0b8eeb8ea`) is left on origin for the record, like the coder's
  three; delete it with them.
- The Worker's served v3 hashes `0dd89cfe…` on the wire, the repo's `e2a25982…`; the parsed documents are equal.

### Prediction, scored

1. Held exactly. 2. Held. 3. Held, including the `StreetWithTopoOver` gap, which became should-fix 1. 4. Held.
5. Half right: the trails are strongest by day everywhere and at night on ground and grass, but at night in the forest
the black tertiary and the rail both edge past the footpath. Not predicted: that the style checker guards only parks
among the five fills.

**Is PR #448 mergeable as plumbing ahead of the switch, and under what conditions?** Yes: v4 is the ruled design to
the byte, v1 to v3 are unchanged by file and by every app path, and nothing in the PR can change what a user sees until
the switch. Conditions: the owner's S22 check first, as the dispatch orders; should-fix 1 and 2 recorded as work for
the switch PR (1 is required before the switch, not before this merge); 3 and 4 at the coder's next pass. Merging is
the owner's go.
