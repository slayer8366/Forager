# Night icon set: verify-first report (dispatch 2026-10-22-06)

Coder: Hazel (Claude Opus 5.5). Planner: Rowan. Owner's decision: RECORD -1565, "Yes, swap icon sets". This
also records the diagnosis that led to it (dispatch 2026-10-22-05), which had no file of its own.
Clarifications folded in:
- Glyph patterns are in the set (owner: "Same as map glyphs like trees and such").
- The owner on inverting: "Only for the white icons I mentioned. Nothing else".

Nothing is built. This report is the only change on branch `night-icon-set-verify`.

## Base, verified

- `origin/pre-main` is `8b6f3572f` (PR #379 merged), as the dispatch states.
- The live `style/street-v2.json` equals `map-style/forager-street-v2.json` at that commit. I fetched and parsed
  both, and they compare equal.
- Its sprite is `sprites/v5/light`. The live `sprites/v5/light{,@2x}.json` have the 61 names.
- **Unverified:** the Worker version id `1c2d8195`. Neither the response headers nor anything I can read without
  Cloudflare credentials show it. What I verified is what it serves.

## The cause this replaces (2026-10-22-05, confirmed on the S22)

The repaint ran in the `setStyle` callback, before MapLibre had put the sprite's images into the style. Evidence:
the instrumented build on the S22 (branch `night-icons-diag`, `abdabf36d`, never merged), a cold launch with Trail
and Night Maps on:

```
18:33:49.106 NightDiag: load#1 +0ms [callback] fullyLoaded=true nonNull=0/61: trailhead_b=null, parking=null, ...
18:33:49.138 SightingsMap: Street night: 0 icons and patterns redrawn, 61 left in day colours: [...]
18:33:49.340 NightDiag: load#1 +234ms [delay 0ms] fullyLoaded=true nonNull=61/61: trailhead_b=38x38/d600/px0=ff4fa074, ...
18:33:59.108 NightDiag: load#1 +10001ms [delay 10000ms] ... nonNull=61/61 (same day pixels)
```

A second cold launch gave the same lines: 0/61 at +1 ms, 61/61 at +222 ms. The phone was restored to
1.0.3823+g5eeb1ade afterwards; the database stayed at schema 22 and unchanged.

The owner's "The trees changed colors" was the forest fill, not the glyph:
- Layer `landuse_park` takes `#a9d39b` to `#446835` through the fill exception (`NightColour.kt:146`).
- The day glyph `#3e7a3a` stayed on top of it, nearly the same colour as the new fill.

Two facts from MapLibre Native's source decide every option below. Both are read at tag `android-v13.5.0`, the
version the app pins.

1. **A sprite load replaces any image already in the style that has the same name.**
   `src/mbgl/style/style_impl.cpp:374-381`, in `Style::Impl::onSpriteLoaded`: if an image with the same id
   exists, `*found = std::move(*it)`. So night images added under the sprite's own names before the sprite
   arrives are overwritten by the day ones.
2. **The map takes its snapshot of the day images before the app's sprite-loaded listener runs.**
   `onSpriteLoaded` calls `observer->onUpdate()` first, then `observer->onSpriteLoaded(sprite)` (`:395-396`).
   `Map::Impl::onUpdate` builds that snapshot (`.images = style->impl->getImageImpls()`) at once
   (`src/mbgl/map/map_impl.cpp:107-128`), so the renderer receives the day images before
   `MapView.OnSpriteLoadedListener` (present in 13.5.0, checked with `javap`) can swap anything.

Offline downloads store every sprite the style names, at 1x and @2x:
`platform/default/src/mbgl/storage/offline_download.cpp:364-371`. They store nothing the style does not name.

## 1. How the night set reaches MapLibre

### Option D (recommended): the night set ships inside the app, under its own names

**What it is:**
- The 61 night images ship in the APK as a night sheet with its own index, at 1x and @2x.
- In the `setStyle` callback the app adds them with `Style.addImages` under names no sprite uses
  (`night/parking`, `night/tree_dark_irregular`, ...).
- At night the app then points the style's image references at those names.
- v2 has six such references, read from the live style:
  - `landuse_forest_glyphs` `fill-pattern`;
  - `landuse_wetland_tufts` `fill-pattern`;
  - `roads_oneway`, `roads_shields`, `pois` and `places_locality`, each `icon-image`.
- Each reference becomes `["coalesce", ["image", <night name>], ["image", <day name>]]`. An image with no night
  copy then draws in day colours rather than vanishing. The callback logs the names the night set lacks, so that
  case is never silent.

**Why it holds from the first frame:**
- No sprite image is called `night/...`, so the sprite load never replaces these (fact 1).
- They are in the style before the first frame that could draw them. They are added in the same callback that
  runs when the style finishes loading, before any layer is drawn with them.
- It does not depend on when the sprite arrives. Today's bug is exactly that dependency.
- **This is inference from the source. The device check below confirms it.**

**Offline areas:** unchanged. Nothing new is stored and no download changes.
- v2 regions draw it.
- v1 regions draw it too. sprite v4's 53 images are pixel for pixel v5's: I compared all 53 at 1x and @2x, and
  none differ. The 8 names only v5 has are simply not referenced by v1's style.
- Topographical night over Trail Map (`StreetWithTopoOver`) takes the same path.

**The -1294 notice:** not affected. No style URL changes; `regionHasOlderMapStyle` (`StreetStyle.kt:75-78`)
reads the same values.

**No-signal Trail Map and the -1513 fallback:** unchanged.
- The offline style choice (`offlineRenderStyleUrl`, `StreetStyle.kt:66-67`) is untouched.
- The fallback (`fallsBack`, `OwnStreetFallback.kt`) still keys on `STREET_STYLE_URL`.
- The OpenStreetMap raster it falls back to has no sprite, so the night set never applies there.

**Older apps:** nothing served changes, so they see exactly what they see today.

**What it needs:** no R2 upload and no Worker change.

**What it costs:**
- APK size grows by the night sheets. The day sheets are 37 KB and 67 KB, so the night pair should be similar.
  That is an estimate; I built nothing.
- A future sprite (v6) needs an app release to get its night set. Until then its new images draw in day
  colours, and the log line lists them.
- Changing which image a layer points to at run time is not repainting pixels, but it is a run-time style edit, of
  the same kind the colour recolour already makes. **Whether that fits "no repainting on the phone" is for the
  planner to judge.**

### Option C: the night set ships inside the app under the sprite's own names, swapped in when the sprite loads

- Adding the images in the callback does not work: the sprite load replaces them (fact 1).
- So they would have to be added in `OnSpriteLoadedListener`. By then the renderer already holds the day images
  (fact 2), so a few frames in day colours are possible. That is the same light flash the dispatch rules out.
- **Unverified on a device either way.**
- Offline, the notice, the fallbacks and older apps: as D.
- **Not recommended:** it races by construction.

### Option A: a night style document on the Worker

**What it is:** a new `street-v2-night.json`, with `sprite` pointing at `sprites/v5-night` on R2.

**Offline areas change. That goes to the owner.**
- A v2 region holds only `street-v2.json` and the v5 day sprite (fact on offline downloads above).
- At night with no signal, the app would load a style URL that no region holds. The style fails to load, and the
  -1513 fallback never acts offline (`fallsBack` is false when `useOfflineTiles`). The result is a blank map.
- For this to work, every download would have to store a second "night" region over the same area (MapLibre
  shares tiles by URL, so the extra is the style and sprite files).
- Every existing v2 region would need that second region too: either downloaded again, or added by the app
  without asking whenever it is online.
- v1 regions would need a `street-v1-night.json` as well, or draw day icons at night offline.
- The region list and `regionHasOlderMapStyle` would have to hide these night companions. Otherwise each one shows
  as a region of its own, carrying the -1294 notice, because its URL is not the download URL.

**Other changes:**
- The -1513 fallback: `fallsBack` would need the night URL added. Without it, a failed night style online gives a
  blank map, not OpenStreetMap.
- No-signal Trail Map: `offlineRenderStyleUrl` would need a night list beside `STREET_STYLE_URLS`.

**Older apps:** unaffected. These are new paths only.

**Worker and R2:**
- R2 `sprites/v5-night/light{,@2x}.{json,png}`.
- `SPRITE_PATH` in `server/pmtiles-worker/src/index.ts:393` widened to `v5-night`.
- A bundled `/style/street-v2-night.json` route beside the v2 one (`index.ts:463`), and the v1 night route if
  wanted.

### Option B: one style with two sprites (MapLibre's `sprite` array) and night references switched in the app

- Offline downloads would store both sprites (offline_download.cpp:364-371), so night works offline for regions
  downloaded against it.
- But the v2 document cannot change: "a URL that never changes meaning" (`StreetStyle.kt:20-21`), and the regions
  hold the old document. So it ships as `street-v3.json`.
- **Every v2 region then shows the -1294 notice until downloaded again. That goes to the owner.**
- It also needs the same reference switch as D.
- **Not recommended:** it costs every user a re-download for nothing D does not already give.

### Rejected without a section

The app fetching a served night sprite and putting it into MapLibre's cache itself
(`OfflineManager.putResourceWithUrl`, present in 13.5.0). That resource lands in the ambient cache, which
MapLibre may evict, so a downloaded area could lose its night icons without warning.

## 2. The night sprite build, and the test that holds it to the app's rules

**Where:**
- D: in the APK, under `app/src/main/assets/` (exact path for the build dispatch). Nothing on R2.
- A: R2 `sprites/v5-night/` with the Worker change above.

**How it is made:**
- One implementation of the rules: the app's own Kotlin functions (`nightColorOf`, `nightIconPixelsOf` with
  `STREET_NIGHT_ICON_RINGS`, `nightPatternPixelsOf`), run on the JVM.
- A writer mode (a Gradle task, or a test run with a property) reads the v5 day sheets, applies the rule per name,
  and writes the night sheets with the same index (same x, y, width, height, pixelRatio).
- "Reproducible" means pixels and index, not PNG bytes, as `build-sprite-v5.mjs` already treats it.
- A JavaScript port inside `map-style/` would be a second copy of the rules. Rejected.

**The day input:**
- The v5 day sheets are not in git (`map-style/.gitignore:3` ignores `assets`; `fetch-assets.sh` fetches them).
- The build would commit copies of `sprites/v5/light{,@2x}.{json,png}` (37 KB + 67 KB) as test resources, with
  their sha256.
- A check against the served R2 copies keeps those committed copies honest.

**The test** (`NightSpriteTest`, JVM, no Robolectric needed):
- It decodes the committed day and night sheets.
- For every name in the day index, it asserts that the night pixels equal the rule applied to the day pixels,
  pixel for pixel at both densities, and that the index entries are equal.
- It asserts the night index's names equal the day index's: 61, no more, no fewer.
- It asserts the day index's names equal the v2 style's `metadata["forager:sprite-icons"]` plus the two patterns.
- It asserts every image a v2 layer references by a literal name, and every pattern, is in the night set.

### The rule for each of the 61 images

The owner's rulings applied, with the planner's reading that road shields go with the icons:
- **Inversion** is the app's night transform per pixel, alpha kept: `nightColorOf`, `NightColour.kt:39-47`.
- **Inversion, ring kept light** is RECORD -1345: `STREET_NIGHT_ICON_RINGS`, `NightColour.kt:187-191`, which the
  app applies today.
- **Painted** means every visible pixel takes the colour and keeps its alpha: `nightPatternPixelsOf`.
- "Used by" is read from the live v2 style.

| # | Image | Used by | Rule |
|---|---|---|---|
| 1-5 | `NL:S-road-1char` to `-5char` | `roads_shields` | Inversion |
| 6-10 | `US:I-1char` to `-5char` | `roads_shields` | Inversion |
| 11-15 | `generic_shield-1char` to `-5char` | `roads_shields` | Inversion |
| 16 | `arrow` | `roads_oneway` | Inversion (it has no fully opaque pixel; only its translucent pixels change) |
| 17 | `capital` | `places_locality` | Inversion |
| 18 | `townspot` | `places_locality`, `pois` (saddle, shelter, huts) | Inversion, ring kept light (#d6d6d6) |
| 19 | `toilets` | `pois` | Inversion, ring kept light (#c6bfea) |
| 20 | `drinking_water` | `pois` | Inversion, ring kept light (#c6bfea) |
| 21-59 | `aerodrome`, `animal`, `artwork`, `attraction`, `bar`, `beach`, `beauty`, `bench`, `books`, `building`, `bus_stop`, `cafe`, `camp_site`, `clothes`, `convenience`, `electronics`, `fast_food`, `ferry_terminal`, `forest`, `garden`, `library`, `marina`, `museum`, `park`, `parking`, `peak`, `picnic_site`, `post_office`, `ranger_station`, `restaurant`, `school`, `stadium`, `supermarket`, `theatre`, `trailhead_b`, `train_station`, `university`, `viewpoint_c`, `zoo` (39) | `pois` | Inversion |
| 60 | `tree_dark_irregular` | `landuse_forest_glyphs` (`fill-pattern`) | Painted #5d804f, not inverted |
| 61 | `tufts` | `landuse_wetland_tufts` (`fill-pattern`) | Painted #2485ad, not inverted |

Two points the table does not settle by itself:

- **`forest`, `park` and `garden` are point icons, not patterns.** They are 38x38 images drawn by `pois`. The only
  fill patterns in v2 are rows 60 and 61, and `NightExceptionsTest` already checks that list.
- **Painting `tufts` differs from today's inversion only at 1x.**
  - At @2x all 156 visible pixels are #3fa0c8, and its inversion is #2485ad, so painting gives the same result.
  - At 1x the sheet's tufts has varied edge colours. On 144 of 167 visible pixels (alpha up to 235) the inversion
    is not #2485ad, so painting changes them.
  - MapLibre uses @2x on any phone with pixel ratio above 1, which includes the S22. Only a 1x screen would see the
    difference.
  - These counts come from a Python port of `nightColorOf`, not the Kotlin itself.

## 3. What to retire

**Run-time repaint (removed):**
- `applyStreetSpriteNight` (`StreetStyleApply.kt:65-109`) and its call (`MapStyleReload.kt:129`).
- Its log line, "Street night: N icons and patterns redrawn, M left in day colours".
- In its place, one line from the new path: how many night images were added and how many references switched,
  with any name lacking a night image.

**`NightPattern.kt`:**
- `STREET_SPRITE_NIGHT_NAMES` and `streetSpriteNightPixelsOf` go.
- `STREET_NIGHT_PATTERN_COLOURS` and `nightPatternPixelsOf` stay only as the generator's rules. `tufts` joins the
  map at #2485ad under the owner's ruling, and leaves the inversion list.

**Kept as the generator's rules, out of the run-time path:**
- `nightIconPixelsOf`, `nightPixelsOf` and `STREET_NIGHT_ICON_RINGS` (`NightColour.kt`).
- `STREET_SPRITE_ICON_NAMES` (`StreetStyle.kt:93`) stays as the list the night set must cover, still held to the
  style's metadata by `StreetStyleTest:70-83`. Its doc comment ("the app inverts one by one") is rewritten.

**Tests reworked:**
- `NightExceptionsTest:212-231` moves to the generated set: the per-name pixel rule, and the two patterns painted.

**Comments only, nothing served:**
- `build-sprite-v5.mjs:9-11` ("Night is drawn by the app") and `generate-street.mjs:38` describe the run-time
  repaint, and are rewritten.
- The served v2 document is not touched. Its metadata keys stay as they are.

**Kept:** the colour-property recolour (`applyOfflineNightRecolour`). It is out of scope and passes on the S22.

## 4. A test that would have caught today's bug

**The JVM test:**
- A test for the new night step, entered the way `MapStyleReload` enters it: the callback's style, through a small
  interface over the three things it does (add images, read a layer's image reference, set it).
- Its fake style holds **no sprite images** at the call, which is what the S22 showed at the callback (`nonNull=0/61`).
- It asserts that all 61 night images were added and all six references switched.

**Its revert check:**
- Run today's `applyStreetSpriteNight` against the same fake: it returns "0 redrawn, 61 left", so the test fails
  with a message naming the missing night images.
- Today's Robolectric fixtures hand the images over at once, which is how the bug passed review.

**The device check** (MapLibre's own ordering cannot run on the JVM; Robolectric has no native map). On the S22,
Trail with Night Maps on:
- cold launch, then turn Night Maps off and on;
- pass: the new log line says 61 added, 6 switched, none missing;
- pass: a screen capture taken within the first second of the load shows parking, peak, viewpoint and trailhead dark
  and the tree marks #5d804f, with no light frame first.

## 5. Deploy order

**D:** the app only. No R2 and no Worker step. Older apps are untouched at every step, because nothing served
changes.

**A:**
1. R2 `sprites/v5-night/` (the owner's go, via Aspen). Nothing references it yet, so no app sees a change.
2. The Worker: the night style route and the `v5-night` sprite path (the planner deploys on the owner's word).
   These are new paths only, so older apps never request them.
3. The app, together with the offline-region changes above.

## Owner-facing decisions

1. **D or A.**
   - D changes no offline area, shows no notice, and needs no R2 or Worker change.
   - A changes offline areas: every download gains a night companion, and every existing v2 region needs one before
     it works at night offline.
   - B brings back the -1294 notice on every v2 region.
2. **Which images invert.** "Only for the white icons I mentioned. Nothing else."
   - The road shields are settled. The owner, via Aspen, with S22 screenshots: "See how these icons stay white
     during the switch? Including the highway icon". So rows 1-15 invert.
   - Aspen's night samples match the day sprite's colours exactly: `picnic_site` and `viewpoint_c` #d6f2e3 and
     #4fa074, `toilets` #dcebfa, the US 30 shield #ffffff (the day colours of those images in the live sprite v5 at @2x). That
     is what the 2026-10-22-05 cause predicts.
   - Coloured POI icons (restaurant orange, museum pink and so on) are not "white icons" either. If the owner meant
     only the green-and-white outdoor set to invert, the table changes.
   - The three -1345 light rings (toilets, drinking water, the place dot) are kept, as the app does today. The
     planner's word was "plain inversion", which would drop them. That needs the owner's word either way.
3. **`tufts` painted:** it is identical to today on the S22 (@2x), and differs only on 1x screens.

## Not verified

**On a device:**
- D's first-frame claim, and C's flash: inferred from MapLibre's source, not seen on a phone.
- That `["coalesce", ["image", ...], ...]` works in `fill-pattern` and `icon-image` on MapLibre Native 13.5.0.
  It is supported in MapLibre's expression language as I understand it. **Unverified.** The fallback is a plain
  `concat` name with the missing-name log line.

**Not measured or not visible:**
- The night sheets' size (not built).
- The Worker version id `1c2d8195` (not visible without credentials).
- The 1x/@2x pixel counts above came from a Python port of `nightColorOf`. They are not run through the Kotlin.
- Option A's per-download cost for night companions: not measured.

**Not read:** `NoSignalTrailMap.kt` in full. Its offline style comes from `offlineRenderStyleUrl`, per the doc
comment at `StreetStyle.kt:56`; I read that function, not the composable.
