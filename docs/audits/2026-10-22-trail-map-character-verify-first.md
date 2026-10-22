# Trail Map character with its night colours: verify-first report

Dispatch 2026-10-22-03 (`prompts/preserved/2026-10-22-03.md` on `origin/records-after-173`). Coder: Hazel.
Planner: Rowan. Written 2026-10-22, about 22:15 UTC (15:15 PDT).

**This round is the verify-first only.** Nothing is built. No app, style, Worker, sprite or test file is changed;
this report is the only file on the branch. No build ran (there is no Gradle or Node here, and none was needed).
Nothing was deployed, and R2, Cloudflare and the S22 were not touched.

**Stop.** Item 3 finds that this design needs `street-v2.json`. The dispatch says that is the owner's call, so the
work stops here until the planner and the owner answer. Items 4 and 5 are laid out both ways so the answer can be
given in one pass.

## Base and sources, as checked

| What | Claimed by the dispatch | Found |
|---|---|---|
| Base | `origin/pre-main` at `563e9b756` | `origin/pre-main` is `563e9b7561d65f30339b8acb610aafa1d3951f26` after a fetch at about 22:00 UTC. Matches. Branch `trail-map-character-verify` is cut from it. |
| Character record | `docs/design/2026-10-21-character-rulings.md`, `ef4cb60` | Forager-cartography `main` is `ef4cb60`; the file's last commit is `ef4cb60`. Matches. |
| Night record | `docs/design/2026-10-22-night-colours-rulings.md`, latest `2ff9881` | Last commit on the file is `2ff9881`. Matches. |
| Sprite drafts | `assets/sprite-drafts/2026-10-22/`, `ef4cb60` | Matches. `sha256sum -c SHA256SUMS`: all 72 entries OK. |
| `DOT_KINDS` | `forager-street.mjs:119` | `map-style/overrides/forager-street.mjs:119`. Matches. |
| `STREET_NIGHT_WATER = "#8ec5f0"` | `NightColour.kt:96` | `app/src/main/java/com/zynergylabs/forager/app/ui/map/NightColour.kt:96`. Matches. |
| Icons inverted as images by name | `NightColour.kt:72-73`, `STREET_SPRITE_ICON_NAMES` in `StreetStyle.kt` | `NightColour.kt:72-73` (the comment), `StreetStyle.kt:77-86` (the list), and the loop that does it, `StreetStyleApply.kt:73-106`. Matches. |
| Style written by | `generate-street.mjs:63` | `map-style/generate-street.mjs:63`. Matches. |
| Sprite from R2 `sprites/v4` | | `server/pmtiles-worker/src/index.ts:387,401`: the Worker serves only `/sprites/v4/light{,@2x}.{json,png}` from R2 key `sprites/v4/...`. Matches. |
| `NightWaterTest` holds night water to 3:1 | | `app/src/test/.../ui/map/NightWaterTest.kt:17`, and `map-style/tools/check-street-night.mjs:77-81`. Matches. |

### Where the records disagree with the dispatch, or with themselves

The records win, as the dispatch says. Each point is read from the record, not from a summary.

1. **The tufts' night colour in the draft sheet.** The character record (Status table, "Night colour of the wetland
   tufts") says "The night sheet keeps the day `#3fa0c8`". The night draft does not: every visible pixel of
   `night/tufts@2x.png` is `#2485ad`, which is the app's inversion of `#3fa0c8`, and `night/tufts.png` is that
   inversion pixel for pixel (167/167). The record's sentence is wrong about its own file. It does not change any
   ruling, since the tufts' night colour was left open either way.
2. **Trail names.** The dispatch says "ruling 8: the owner said leave them as they are". The character record says
   ruling 8 is **unresolved** ("Ask the owner before dispatching anything for trail labels"). No record I can read
   has the "leave them" ruling. It is out of scope here either way, and nothing is built for it; the planner may hold
   a later word that the record does not.
3. **"The owner had no preference" on tufts.** The dispatch says this; the records say only "Not separately ruled".
   Not a contradiction, but the dispatch's wording is not in a record I can read.
4. **"Forest `#446835`, the app's palette".** The record's source for `#446835` is the swatch board and
   `MapPaletteTest.kt:177`: a measured night cluster of the **OpenStreetMap raster** map through the night shader.
   Trail Map, the map this dispatch changes, draws its night forest today as the inversion `#436d35`
   (`NightColour.kt:91`, `forager-street.mjs:41`). So `#446835` is new for Trail Map and needs its own exception. That
   matches the dispatch's own line ("The formula gives `#436d35`"), so this is a note on what "the app's palette"
   refers to, not a disagreement.

## Item 1: how night reaches the new layers

### What the app does today

At night the app does two things to Trail Map after the style loads (`MapStyleReload.kt:128-129`):

- **Paint colours:** `applyOfflineNightRecolour(style, streetWater = true)` walks every background, fill, line and
  symbol colour property and gives each the inversion, except water (by source layer `water`) and the line colours
  named in `STREET_NIGHT_LAYER_COLOURS` (`NightColour.kt:137-146`).
- **Sprite images:** `applyStreetSpriteNight` reads each image named in `STREET_SPRITE_ICON_NAMES` back with
  `Style.getImage`, inverts it pixel by pixel, and puts it back under the same name (`StreetStyleApply.kt:73-106`).
  An image not on that list is never touched.

A `fill-pattern` layer draws the sprite image's own pixels; its `fill-color` is not used when a pattern is set
(MapLibre style spec; read from memory of the spec, not from this repository). So the two pattern layers get night
only through their images, never through the paint walk.

### Proposal, one mechanism per kind of thing

| New thing | Night colour | Proposed mechanism | Exact? |
|---|---|---|---|
| Six icons (`trailhead_b`, `viewpoint_c`, `parking`, `camp_site`, `picnic_site`, `ranger_station`) | not separately ruled; Sage's night drafts | **The app's inversion, as today**: add the six names to `STREET_SPRITE_ICON_NAMES` and to the style's `forager:sprite-icons`. | Yes. Sage's night PNGs **are** the app's inversion: for each of the six, at 1x and 2x, every visible pixel of `night/` equals `nightColorOf(day/)` (357/357 at 1x, 1376/1376 at 2x, alpha identical). Checked with a scratch script implementing `NightColour.kt:39-47` with Kotlin's half-up rounding. |
| Tree glyph pattern `tree_dark_irregular` | `#5d804f` | **A new flat recolour of the pattern image**: every pixel's colour set to the ruled colour, alpha kept, put back under the same name. A new map `STREET_NIGHT_PATTERN_COLOURS` (name to colour) in the app, with its design copy in the style's metadata (`forager:night-patterns`), held equal by a test like the existing ones. Patterns are kept off the inversion list. | Yes, by construction. It is also pixel-identical to Sage's ruled night PNG: `night/tree_dark_irregular{,@2x}.png` has exactly one colour, `#5d804f`, and alpha identical to the day PNG, which is what `tree-steps5.py` did. Plain inversion gives `#9cd898` (3.87:1 on the forest), as the dispatch expected. |
| Wetland tufts pattern `tufts` | open (see "Tufts", below) | The same flat recolour. | Yes, by construction. |
| Streams `water_stream`, and every water fill and line | `#163f4b` | **The existing water exception** with its constant changed: `STREET_NIGHT_WATER = "#163f4b"`. `water_stream` and `water_river` already draw from source layer `water`, so they take it with no new code. | Yes: it sets the literal. |
| Forest (`#a9d39b` by day) | `#446835` | **A new fill-colour exception by day colour**: inside the `fill-color` of `landcover` and `landuse_park`, the colour literal `#a9d39b` becomes `#446835`. A map `STREET_NIGHT_FILL_COLOURS` (day to night) in the app, its design copy in metadata, held equal by test. | Yes: it sets the literal. Matching must be on parsed RGB, not text, because MapLibre may hand expression colours back as `["rgba", ...]` arrays (`NightColour.kt:209-211` records that form as unverified until a device run). |
| Wetland fill (`#c5e3d6`) | `#2a4f47` | The same fill exception. Inversion would give `#1b392c`. | Yes. |
| Bare rock fill (`#c9c4bb`) | `#6e7580` | The same fill exception. Inversion would give `#433e35`. | Yes. |
| Area names, peak heights | not ruled | Inversion, as every label today (-1288). No new code. | n/a |

Why these and not the alternatives:

- **Sage's night PNGs as separate sprite images** (for example `tree_dark_irregular-night`, swapped in at night):
  exact too, but it puts colour decisions into image files that a test cannot read as colours, doubles the new
  sprite entries, and needs a second swap mechanism beside the inversion. The flat recolour keeps every night colour
  as one hex value that the checker and the tests read, the way every other night exception is held today.
- **The app's inversion for patterns:** cannot give a ruled colour (`#9cd898` for the tree, `#2485ad` for the tufts).

The forest exception is load-bearing for the tree rule: the tree glyph `#5d804f` is **1.424:1 on `#446835` but
1.342:1 on today's `#436d35`**, which fails the 1.35 floor. The forest exception and the tree glyph must ship
together.

### The checker numbers, confirmed against the app's real colours

Every land fill colour was read from the generated style (`map-style/forager-street-v1.json`) and put through the
app's transform (`NightColour.kt:39-47`). Relative luminance and WCAG contrast as `check-street-night.mjs:30-37`
computes them.

| Rule (the planner's numbers) | Result |
|---|---|
| Night water: L at most 0.050, blue at most 96 | `#163f4b`: L 0.0423, blue 75. **Passes.** `#8ec5f0`: L 0.5197, blue 240. **Fails**, as required. |
| Water against every night land fill: at least 1.05:1 | 22 fills checked (every fill and background colour the style draws outside `water`, plus the new wetland and rock, with forest at `#446835`). Lowest: **1.083** on night grass `#2d3911`, then 1.111 on the background `#333333`, 1.190 on `#1c3232` (zoo, military), 1.203 on farmland `#1c3316`, 1.250 on wetland. **All pass.** The record's 1.08 is confirmed. |
| Tree glyph against the night forest: at least 1.35:1 | `#5d804f` on `#446835`: **1.424. Passes.** (T4 `#57784a`: 1.279, as the record says.) On `#436d35`: 1.342, fails (above). |
| Every other layer keeps its current floor | Re-checked on the ruled forest `#446835`, since moving the forest moves every "on the night forest" figure. Every one rises: minor casing 3.21 to 3.40, service casing 2.65 to 2.82, major 2.95 to 3.13, highway 3.57 to 3.79, rail 3.18 to 3.38, boundaries 3.21 to 3.41, place-dot ring 4.16 to 4.41, toilets ring 3.46 to 3.67. Informal paths 2.01 to **2.13**, still inside their band (1.9 to 2.3) and still below the night footpath (2.43 to 2.58). **All pass.** |

No ruled colour fails its own rule. The checker's "night forest" ground (`check-street-night.mjs:88`, today
`night(COLOURS.forest)`) has to become the ruled `#446835` when the exception lands.

### Tufts: the planner's rule has no answer as written

The rule: "the darkest colour that keeps at least 1.35:1 against the night wetland `#2a4f47` while staying inside the
night-water ceiling".

- The night wetland itself is L 0.0654, already above the 0.050 ceiling.
- A tuft **lighter** than the wetland needs L of at least 0.106 for 1.35:1. That is above the ceiling, so no lighter
  colour meets both.
- A tuft **darker** than the wetland needs L of at most 0.0355. Every such colour is inside the ceiling, and
  "darkest" then has no bound: black has the most contrast.

So the rule needs a reading. My proposal, **for the planner to accept or change**: tufts darker than the wetland, at
the contrast floor (the dimmest that still reads), in the day tufts' own hue (the day tufts are the stream blue):

| Candidate | L | Blue | On wetland |
|---|---|---|---|
| **`#163847`** (day tuft hue `#3fa0c8` scaled down; proposed) | 0.0345 | 71 | 1.365:1 |
| `#1f3934` (the wetland's own hue scaled down) | 0.0347 | 52 | 1.363:1 |
| `#10394b` (the inverted tuft `#2485ad` scaled down) | 0.0354 | 75 | 1.350:1 |

For reference: the water `#163f4b` itself is 1.25:1 on the wetland, so it would not meet 1.35. Dark tufts on a
lighter wetland are the opposite of the day look (blue marks on a pale fill), so the owner should see it in the
render before it stands; the dispatch already provides for that.

### Not verified for item 1

- **Replacing a pattern image at run time.** The app already replaces icon images under the same name after the style loads
  (`StreetStyleApply.kt:98`); I have not checked that on a device myself. Whether MapLibre Native 13.5.0 picks up a replaced image used as a
  `fill-pattern` in tiles already laid out is not verified here. It needs the S22 at night at a forest. If it does
  not, the fallback is the separate-image option above, set by `fill-pattern` at night.
- **Edge pixels on the phone.** `Bitmap.getPixels` returns un-premultiplied colours, so faint anti-aliased edge pixels
  may round differently from Sage's PNGs. Full-alpha pixels are exact. Unverified on a device.
- **The tiles carry what the layers filter on.** `wetland` and `bare_rock` as `landuse` kinds, `elevation` on peaks,
  and `saddle`, `shelter`, `alpine_hut`, `wilderness_hut` as `pois` kinds were not checked: there is no archive
  here. They need a look at the owner's build (Aspen) or its profile before building. If one is missing, its layer
  draws nothing, silently.

## Item 2: the sprite

### Today

- The sprite is Protomaps' `basemaps-assets` at commit `028c18f7`, pinned and checksummed by
  `map-style/fetch-assets.sh` (extracted to `map-style/assets/`, not committed). Icons are MIT, "Copyright (c) 2017
  Mapzen" (`map-style/licences/sprites-tangrams-icons-MIT.md`).
- The generator records the sprite's icon names in the style (`generate-street.mjs:31`), and the app's copy is held
  equal by `StreetStyleTest`.
- The Worker serves `sprites/v4/...` from R2 with a one-week cache, under the rule "Fonts and icons never change under
  one name: a new set gets a new path" (`index.ts:383`). Its path pattern accepts **only** `v4`
  (`index.ts:387`), so a v5 needs a Worker change.

### Proposal for sprite v5

1. **Inputs, pinned:**
   - v4's four files, from the archive `fetch-assets.sh` already pins.
   - The eight ruled day cut-outs only (`trailhead_b`, `viewpoint_c`, `parking`, `camp_site`, `picnic_site`,
     `ranger_station`, `tree_dark_irregular`, `tufts`, at 1x and 2x: 16 PNGs). No night PNGs (the app draws night),
     and none of the unchosen drafts.
   - **Two ways to bring them in, for the planner:** (a) `fetch-assets.sh` also fetches Forager-cartography at
     `ef4cb60` by archive, checks the archive's sha256 (trust on first use, as for v4), then checks the 16 files
     against the cartography `SHA256SUMS`; or (b) the 16 files are copied into this repository with a `SHA256SUMS`
     identical to cartography's lines. (a) keeps the art and its licence in its own repository; (b) needs no network
     in CI. I lean to (a).
2. **Build:** a new script, `map-style/tools/build-sprite-v5.mjs` (under 400 lines), that copies v4's sheet and index
   unchanged and appends the eight images below it at fixed positions, writing `assets/sprites/v5/light{,@2x}.{json,png}`.
   It fails if a name already exists in v4. It needs a PNG library; `pngjs` (pure JavaScript) pinned to an exact
   version is the proposal. `package.json` has none today.
3. **Reproducible means pixels, not bytes.** PNG bytes depend on the zlib in the Node that ran it, so the check
   compares decoded RGBA and the index JSON: every v4 image unchanged, every new image equal to its cut-out. The
   built files' sha256 sums are recorded at build time for the upload.
4. **Licences:** a licence file for the new icons beside the MIT one, in `map-style/licences/` and in the R2 upload
   folder. **This needs the owner's word** (below): the cut-outs are GPLv3 in cartography, and Forager has no
   licence file at all.
5. **Served from:** R2 `sprites/v5/light{,@2x}.{json,png}`, with the licence files under `sprites/`, from a new
   `r2-upload/` folder built the way the Part 1 report's was. The Worker's sprite pattern becomes `v4|v5`; v4 stays
   for every phone still loading `street-v1.json`. Uploading is the owner's (or Aspen's), on its own go.

Overwriting `sprites/v4` in place instead is not proposed: phones and the edge cache it for a week, and the Worker's
own rule forbids it.

## Item 3: the style version: this needs `street-v2.json` (stop)

**Changing `street-v1.json` in place is not safe**, for two reasons, one of which no deploy order avoids:

1. **Older apps at night.** An app from before this release inverts only the images on its compiled list
   (`StreetStyle.kt:77-86`). Loading a new v1 at night, it would draw the six new icons in their day colours, a light
   mint square `#d6f2e3` with a green ring, and the two patterns in day colours (tufts in the bright `#3fa0c8`). Its
   water would stay `#8ec5f0`, as today. That is a bright night map on every phone that has not updated, for as long
   as the Play rollout takes. No order of the three deploys avoids it, because the old app is the problem.
2. **Downloaded areas.** Every region downloaded against v1 holds v1's sprite (v4) and glyphs. If v1 changes in place
   to reference `sprites/v5`, a phone that revalidates the stored style online (one-day cache, `index.ts:409`) would
   then, offline in that region, ask for a sprite the region never stored: icons and the patterns missing there.
   Whether MapLibre's offline store updates a region's stored style this way is **inferred, not verified**.

The code already says a later design ships as v2: `StreetStyle.kt:17-19` ("a later design ships as `street-v2.json`
beside it") and `index.ts:370-373`. But the dispatch makes v2 the owner's call, so it is stopped here.

**What v2 means for downloaded areas and the -1294 notice:**

- Every region downloaded against v1 would show "Older map style. Download again to update."
  (`regionHasOlderMapStyle`, `StreetStyle.kt:62-65`, which compares to the current download URL). Those regions keep
  drawing; the notice asks the user to act. Under "Don't make me think" the owner may want something else (for
  example, no notice for v1-to-v2, or an automatic re-download), and that is a decision, not something I would pick.
- **A trap to fix in the same change:** `offlineRenderStyleUrl` (`StreetStyle.kt:53-54`) picks the Trail Map URL if
  a region holds it, and otherwise `offline.json`. After a plain URL change, a phone with only v1 regions would get
  `offline.json`, which none of its regions stored, so its offline map would fail to load. It has to choose v2, then
  v1, then `offline.json`. The same goes for `usesStreetSprite` (`BasemapStyles.kt:273-275`): a v1 style loaded
  offline must still get its night icons and water.
- `fallsBack` (`OwnStreetFallback.kt:74-79`) only needs the online URL, which becomes v2.

## Item 4: the deploy order

### With v2 (the proposal)

Three pieces, in this order, each checked before the next:

1. **R2: upload `sprites/v5`** and its licence file. Nothing references it yet. Risk: none. Check: the four objects
   are on R2.
2. **Worker: deploy with `/style/street-v2.json` and the `v5` sprite path**, with v1 and `sprites/v4` served
   byte for byte as now. Every phone in use still loads v1, so nothing changes for them. Check: v2, its sprite and v1
   all answer, and v1's bytes are unchanged (the route tests can pin that, as they did for `offline.json`).
3. **The app**, which loads v2 and carries the new night exceptions. The S22 check runs against the live Worker
   before the Play release.

What each wrong order risks:

| Order | Risk |
|---|---|
| App before the Worker | v2 answers 404. The -1513 fallback draws OpenStreetMap's map until the app restarts. Not bright and not blank, but the wrong map, and Trail Map downloads fail. |
| Worker before R2 | Old phones: no change (they load v1). A new app (only on test phones before release) would load v2 with no sprite: icons and patterns missing. Whether MapLibre then reports the style as failed, which would trigger the fallback, is not verified. |
| Any rollback of the app | Safe: v1 never changed. |

No phone ever shows a bright or broken night map in the v2 order: old apps never see the new style, and new apps only
exist after both server pieces are live.

### In place (if the owner rules against v2)

Every order leaves old apps drawing the new icons and patterns in day colours at night (item 3, reason 1), and
deploying the Worker before R2 would make the sprite answer 404 for **every** phone, losing all icons, the existing v4
ones included. The least bad order would be R2, then the app, then the Worker, held until most phones have updated,
and it still shows bright icons on every phone that has not.

## Item 5: size

Line counts are today's (`wc -l` at `563e9b756`). Estimates assume v2, with my proposals above.

| File | Today | Change, estimated | After |
|---|---|---|---|
| `map-style/overrides/forager-street.mjs` | 394 | +40 (colours, night fill and pattern maps, `DOT_KINDS` emptied) | ~435 |
| `map-style/overrides/forager-character.mjs` (new: rulings 1-7 as layer edits, called from `applyStreetDesign`) | | new | ~200 |
| `map-style/generate-street.mjs` | 65 | +25 (v2 outputs, v5 sprite, metadata; v1 no longer written) | ~90 |
| `map-style/tools/check-street-night.mjs` | 127 | +60 (ceiling, water on land, tree on forest, fills, patterns, v1 frozen) | ~190 |
| `map-style/tools/check-street-trails.mjs`, `check-style.mjs`, `preview/index.html`, `package.json` | 154, 126, 51, 19 | a few lines each (v2 name, script, `pngjs`) | |
| `map-style/tools/build-sprite-v5.mjs` (new) | | new | ~150 |
| `map-style/fetch-assets.sh` | 30 | +15 (cartography fetch, if option (a)) | ~45 |
| `map-style/licences/` | | one new file | |
| `server/pmtiles-worker/src/index.ts` | 595 | +15 (v2 route, v5 path) | ~610 |
| `server/pmtiles-worker/test/worker.test.mjs` | 347 | +40 | ~390 |
| `server/pmtiles-worker/README.md` | 117 | +5 | |
| `app/.../ui/map/NightColour.kt` | 315 | +50 (water constant, fill exceptions by colour) | ~365 |
| `app/.../ui/map/NightPattern.kt` (new: flat recolour and pattern colours) | | new | ~60 |
| `app/.../ui/map/StreetStyleApply.kt` | 106 | +30 (patterns) | ~135 |
| `app/.../ui/map/OfflineNightRecolourApply.kt` | 108 | +5 | |
| `app/.../map/StreetStyle.kt` | 86 | +30 (v2 and v1 URLs, three-way offline choice, six names) | ~115 |
| `app/.../ui/map/BasemapStyles.kt` | 455 | +3 | |
| Tests: `NightWaterTest` 97, `NightExceptionsTest` 158, `StreetStyleTest` 71, `OfflineRegionOlderStyleNoticeTest` 90, `OfflineRegionReconciliationTest` 244, `NoSignalTrailMapTest` 128, `OwnStreetStyleSourceTest` 115, `OwnStreetFallbackTest` 127, `BasemapStyleTest` 228 | | +20 to +60 each | all under 320 |
| New test `NightPatternTest` | | new | ~100 |

About 25 files and 700 to 900 hand-written lines, plus the generated files.

- **No file passes 800**, so no split is needed first. Every new file stays under 400.
- **Generated files are the exception to the line rule:** `forager-street-v2.json`, its `.preview.json` and the Worker's
  `street-style-v2.json` will each be about 14,500 lines, as v1's are (14,101). The v1 copies stay as they are. The
  planner should confirm generated JSON is outside the 400-line rule, as v1 was.
- `MapLibreOfflineMapRepository.kt` (404) is not touched by this plan.

## Decisions needed

For the **owner**:

1. **`street-v2.json`** (item 3). My finding is that in place is unsafe for older apps at night whatever the order.
2. **What v1 regions show under v2:** the -1294 notice ("Download again to update"), no notice, or something
   automatic.
3. **The licence of the new icons in Forager.** They are GPLv3 in Forager-cartography; Forager has no licence file
   and serves the sprite from its Worker. Which licence file goes beside them in the sprite and in `map-style/licences/`?

For the **planner**:

4. **Tufts' night colour:** the rule has no answer as written. Proposed `#163847` (darker than the wetland, 1.365:1,
   day tuft hue), to be shown in the render.
5. **Night mechanism:** inversion for the six icons; a flat recolour of the image for the two patterns; colour
   exceptions by day colour for forest, wetland and rock (item 1).
6. **How the cut-outs come in:** fetched from cartography at `ef4cb60` (a) or copied in (b).
7. **`pngjs`** as a new pinned dependency for the sprite build.
8. **Generated v2 JSON** outside the 400-line rule.
9. **Trail names:** the dispatch's "ruling 8" is not in the records, which call it unresolved. Nothing is built for it
   either way.

## Not verified

- Whether MapLibre picks up a pattern image replaced at run time (item 1). Needs the S22.
- Edge-pixel rounding on the phone (item 1).
- Whether the owner's tiles carry `wetland`, `bare_rock`, peak `elevation`, and the four new outdoor kinds. Needs the
  archive or its build profile.
- Whether MapLibre's offline store revalidates a region's stored style online (item 3, reason 2). Inferred; not
  needed if v2 is chosen.
- Whether a missing sprite makes MapLibre report the style as failed (item 4). Avoided by the proposed order.
- That a `fill-pattern` layer ignores `fill-color`: from the MapLibre style spec as I know it, not read here.
- Nothing was rendered. The day and night renders come with the build.

## How this was checked

Scratch scripts only, run with `python3 -I` on Pillow 12.1.1, kept off the repository:

- A colour script with `nightColorOf` (`NightColour.kt:39-47`, half-up rounding), the WCAG luminance and contrast of
  `check-street-night.mjs:30-37`, every fill colour read from `map-style/forager-street-v1.json`, and the tufts search.
- A pixel script comparing each ruled `day/` and `night/` cut-out, at both densities: alpha, colour counts, and
  `night == nightColorOf(day)` per visible pixel. The off-hue colours in the 1x day files (`#00ffff` in tufts,
  `#336633` in the tree) sit only at alpha 1 and 5, so they cannot be seen.
