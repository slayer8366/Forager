# Night tree glyphs vanish at full zoom: diagnosis from code

Dispatch 2026-10-27-03 (RECORD -1730), report only. Coder (Opus), 2026-10-27, written against pre-main
`6064fef86` (verified equal to `origin/pre-main` when this started). No fix built, no Gradle run, no device.

The symptom (from the dispatch, relayed by Aspen from the owner's S22 screenshots): by day the forest glyphs show at
full zoom. At night they are gone at full zoom and come back when the map is zoomed out slightly (10T ER 28691 19251).

## Verdict

**The code supports none of the three hypotheses.** From the code, nothing changes for this layer between zoom
17.9 and zoom 18. That holds for the style, for the app's night step, and for MapLibre Native 13.5.0's evaluation of
the pattern:

- **Hypothesis 1 (crossfade between z and z+1 fails at the cap): not supported.** After the night switch the
  layer's `fill-pattern` is a constant, `"night/tree_dark_irregular"`. MapLibre 13.5.0 evaluates a constant
  cross-faded pattern as the same name at both ends of the fade. Nothing is looked up at z+1. The details are in
  Part 2.
- **Hypothesis 2 (pixel ratio or size of the added bitmap): not supported.** The night copy has the day image's
  size and pixel ratio. A mismatch would change the glyph's scale at every zoom. It could not hide the glyph at one
  zoom only.
- **Hypothesis 3 (something re-applies the switch on a zoom change): not supported.** No app code that runs on a
  camera move or idle touches a style layer's image property or the night images. The night step runs only in the
  `setStyle` callback.

**What the code does point at, as an inference:** the only zoom-tied difference is which tile objects are drawn.
The Trail Map vector source stops at z15, so at the cap the map draws tiles laid out at overscaled zoom 18. Just
below the cap it draws different tile objects, laid out at overscaled zoom 17, each at its own time. MapLibre looks a
fill pattern up per tile, in that tile's own image atlas. A tile whose atlas lacks the pattern draws nothing where the
glyphs should be. So the symptom fits "the z18 tiles' layout has no night pattern", which is a state of particular
tiles, not a rule about the zoom.

MapLibre has a path that leaves a tile in exactly that state: a tile laid out while the image was absent is given the
images that do exist and keeps that layout. Adding the image later does not re-lay-out existing tiles. **I found no
path in this app that makes the night image absent when a tile is laid out.** That is why the next step is the S22
session in Part 5, not a fix.

**Cheapest fix: none is justified yet.** Part 4 gives the fix for each outcome the S22 session can produce, and
what would prove each one.

## 1. The night pattern path, traced

| Step | Where | What it does |
|---|---|---|
| Style | `map-style/forager-street-v3.json:342-358` | `landuse_forest_glyphs`, a fill on `protomaps`/`landuse`, minzoom 12, no maxzoom, `fill-pattern: "tree_dark_irregular"` (a literal). It is the only tree-pattern layer; the other pattern layer is `landuse_wetland_tufts` (`"tufts"`, minzoom 10). No fill layer in v3 has a zoom-dependent paint value or a maxzoom (checked by script over all 96 layers). |
| Style source | `forager-street-v3.json:151` | `protomaps` is the Worker's `us.json`. The app records its `maxzoom` as 15 (`OfflineMapRepository.kt:160-176`); the live value was not fetched here (no network beyond GitHub). |
| Which style at night | `BasemapStyles.kt:265-270`, `:304-316` | Trail Map at night is `MapStyleSource.Uri(STREET_STYLE_URL)`, so it is v3 by URL (`StreetStyle.kt:23`). It is not the topo-over composition, which is Topographical's night. |
| Camera cap | `MapStyleReload.kt:117`; `BasemapStyles.kt:289-290`; `StreetStyle.kt:52` | `setMaxZoomPreference(18)` for Trail Map. Navigation raises it to `max(18, NAVIGATION_VIEW_ZOOM = 18)` (`NavigationView.kt:152`, `:161-171`), so the cap is 18 in every mode. At 18 the z15 tiles are overzoomed 8×. |
| When the night step runs | `MapStyleReload.kt:73`, `:127`, `:141` | Inside the `setStyle` callback only. The effect is keyed on map, basemap, offline tiles, night mode, its loaded flag and the own-Street fallback. Zoom is not a key. |
| Images made | `NightImageApply.kt:24`, `:35-60` | From the bundled sheet `night-sprite/v5/light@2x` (on any density above 1). It is cut into one bitmap per index entry, each with `density = 160 × pixelRatio`. |
| Images added | `NightImageSwitch.kt:216` → `NightImageApply.kt:67` | `Style.addImages(HashMap)` under `night/<name>`, before any reference is switched. In 13.5.0 that call converts and adds synchronously (`Style.java:530-539`). |
| Reference switched | `NightImageSwitch.kt:96-103` → `NightImageApply.kt:76-77`, `:82` | A literal with a night copy becomes `ImageReference.Name("night/tree_dark_irregular")` and is set with `PropertyFactory.fillPattern(String)`: **a constant, not an expression.** It is then read back (`:85-92`) and counted only if it holds the night name. |
| Zoom-driven code | `MapListeners.kt:154-215`; `MapForecastCells.kt:31-67`; every `setProperties`, `addImage` or `removeImage` in `app/src/main` (12 sites) | The camera move and idle listeners touch bubbles, logging, camera memory and fan reopening. The forecast effect pushes GeoJSON. None of them touches a basemap layer's image property or a `night/` image. The only `removeImage` call is the fan circle's own image (`FanOutLayers.kt:171`). |

## 2. MapLibre Native 13.5.0 at an integer maximum zoom (hypothesis 1)

Read on GitHub at tag `android-v13.5.0` (commit `efbde815`). The pinned version is `maplibre = "13.5.0"`
(`gradle/libs.versions.toml:43`).

- `fill-pattern` is a `CrossFadedDataDrivenPaintProperty<expression::Image, …>`
  (`src/mbgl/style/layers/fill_layer_properties.hpp:40`). It is evaluated by
  `DataDrivenPropertyEvaluator<Faded<T>>`.
- **For a constant**, that evaluator returns `calculate(constant, constant, constant)`
  (`src/mbgl/renderer/data_driven_property_evaluator.hpp:57`). `calculate` picks `{min, mid}` or `{max, mid}` by
  comparing the zoom with `lastIntegerZoom` (`:73-76`), so from and to are the same name at every zoom, the cap
  included.
- For a feature-constant expression, it evaluates once at `floor(z)` and again uses one value for both ends
  (`:62-69`). Only a feature-dependent expression is resolved per feature at `zoom - 1`, `zoom` and `zoom + 1`. That
  happens in `PatternLayout` (`src/mbgl/layout/pattern_layout.hpp:141-166`), and the results go in as image
  dependencies.
- **For a constant**, `PatternLayout` adds the constant's `to` and `from` names as the tile's image dependencies
  (`pattern_layout.hpp:112-121`), which are the same name here.
- **At draw time**, the fill tweaker looks up `from` and `to` in the tile's own atlas
  (`src/mbgl/renderer/layers/fill_layer_tweaker.cpp:80-84`). If a position is missing, it passes `{0,0,0,0}` as the
  pattern rectangle (`:169-171`). That draws nothing visible where the glyphs should be: it is the "glyphs vanish"
  shape, and it is per tile.
- **What happens when one of the two images is missing** (the dispatch's question): the tile draws with a zero
  rectangle for the missing end. There is no fallback to the other end. For this layer the two ends are always the
  same name, so either both resolve in a tile or neither does.

**Answer:** for this layer, MapLibre does not resolve the pattern separately for zoom and zoom+1. The crossfade
cannot fail at the cap, because both ends are one image.

## 3. The other two hypotheses

**Hypothesis 2.** `Style.toImage` takes `pixelRatio = bitmap.getDensity() / DENSITY_DEFAULT`
(`Style.java:1359`). The app sets density to `160 × pixelRatio` from the index (`NightImageApply.kt:55`), which
gives 2.0 for the @2x sheet. The night index entry is 128×128 at pixelRatio 2, or 64×64 at 1x
(`app/src/main/assets/night-sprite/v5/light@2x.json`, `light.json`, entry `tree_dark_irregular`). `NightSpriteTest`
asserts that the night sheets carry the day index (its second test). MapLibre picks @2x for the day sprite on the
same rule, a pixel ratio above 1. So the day and night images have the same size and ratio. The pattern shader's
inputs are the same by day and by night except for the image's place in the atlas.

**Hypothesis 3.** See the last row of the table in Part 1. Changing a constant `fill-pattern` is a layout change in
MapLibre (`src/mbgl/style/layers/fill_layer_impl.cpp:10`). It clears the tile cache and re-lays out every retained
tile (`src/mbgl/renderer/tile_pyramid.cpp:65-67`, `:166-167`). So the one switch in the callback cannot leave stale
tiles behind, and nothing switches again on zoom.

## 4. Where the remaining explanation must be, and the fix for each outcome

These are the MapLibre paths that can leave **some tiles** (here, the z18 ones) without a pattern while others
have it. Each is a candidate. None is confirmed.

- **(R) The image was removed.** An image that was once requested while missing, and later provided, goes into
  `requestedImages`. When no tile uses it any more, `reduceMemoryUse` offers it for removal
  (`src/mbgl/renderer/image_manager.cpp:176-190`; called at `render_orchestrator.cpp:540` and `:778`). Android removes
  it unless an `OnCanRemoveUnusedStyleImageListener` refuses (`MapChangeReceiver.java:269-272`, true with no
  listener). The app registers none. Tiles created after a removal would find no night pattern. **Gap:** I found no
  way for `night/tree_dark_irregular` to be missing when first requested, because the app adds it before switching
  to it. So I cannot show this path is reachable.
  - *Fix if confirmed:* register a listener that refuses removal for names starting `night/`, and log each refusal.
  - *Test:* a JVM test can hold the predicate (night names refused, others allowed). The evidence that it fixes the
    symptom is device-only: Part 5's log showing the image absent at the cap before the fix and present after.
- **(S) The z18 tiles were laid out in a window without the image, and never again.** A tile laid out while the
  image is absent is notified with what exists (`image_manager.cpp:232`, `:162-174`). Adding an image later does not
  re-lay-out existing tiles: only removed or resized images do (`render_orchestrator.cpp:224-247`, `:386`). A tile
  laid out in such a window keeps no pattern position. When the map zooms out and back in, it reuses that tile from
  its pyramid or cache. **Gap:** as for R, I found no window in the app's order of calls.
  - *Fix if confirmed:* force one relayout after the night images are known to be in the render-side image set.
    Every way I can see to do that (for example removing and re-adding an image) is a workaround with a cost, so it
    goes to the owner as a choice, not as a fix decided here.
- **(D) It is deterministic for runtime-added patterns at overscaled zoom 18 on this GPU.** Nothing in the code read
  makes that so. The pattern position math in the tweaker (`fill_layer_tweaker.cpp:92-98`) is the same by day and by
  night. Only the atlas placement differs. If the S22 shows glyphs missing on freshly laid-out ground at the cap,
  with the image present and the reference holding the night name, the cause is below this app.
  - *Options for the owner:* report it to MapLibre with a minimal repro, or serve the night patterns from the style's
    own sprite instead of adding them at runtime. That second option is a design change on the Worker side.

One MapLibre detail was read and not pursued. The shared atlas keys images by
`int32(sqrt(hash(name)) / 2 + area)` (`src/mbgl/gfx/dynamic_texture_atlas.cpp`, `uploadIconsAndPatterns`), so two
names with the same key share one slot. A clash would draw the wrong pixels at every zoom rather than nothing at one
zoom, and the chance for this set of names is very small. I did not compute the keys.

## 5. The S22 reproduction for the next session

**Part A runs on the current build, with no instrumentation.** Each step is written as do > shows > back. Record a
screenshot for each "shows", and the zoom level if the debug readout is on.

1. **Day control.** Trail Map, Night Maps off. Go to 10T ER 28691 19251 and pinch in until the map stops zooming.
   > Shows: tree glyphs on the forest. > Back: none needed. Stay there.
2. **Night entered at the cap.** Still at full zoom, turn Night Maps on, the way it was done this morning.
   > Shows: glyphs, yes or no? > Back: none.
3. **Fresh ground at the cap.** At full zoom, at night, drag the map several screen widths onto forest that has not
   been shown at this zoom tonight.
   > Shows: glyphs on the new ground, yes or no? > Back: drag back to the first spot.
4. **Out and in.** At the first spot, pinch out slightly (glyphs come back, per this morning), then pinch back in to
   full.
   > Shows: glyphs at full zoom now, yes or no? > Back: none.
5. **Night entered below the cap.** Turn Night Maps off, pinch out to about street level (about z14), turn Night
   Maps on, wait two seconds, then pinch in to full.
   > Shows: glyphs, yes or no? > Back: none.
6. **Wetland tufts.** At night at full zoom, find a wetland: blue tufts on the map. A different runtime-added night
   pattern.
   > Shows: tufts at full zoom, yes or no? > Back: none.

How to read Part A:

| Result | Points at |
|---|---|
| Step 3 shows glyphs while step 2 does not | S: stale z18 tiles from an early layout |
| Step 5 shows glyphs while step 2 does not | S: the order of night load against tile layout |
| No glyphs in any of steps 2-5 | R or D |
| Tufts also missing in step 6 | Any runtime-added pattern, not one name |

**Part B needs a logging build** (a separate go, and code goes to a coder). One log line at every camera idle on
Trail Map:

- the camera zoom, to two decimals;
- `landuse_forest_glyphs`'s `fill-pattern` as read back;
- whether `style.getImage("night/tree_dark_irregular")` is non-null, with its width and height, both at the cap and
  below it;
- the same two items for `landuse_wetland_tufts` and `night/tufts`.

Two MapLibre listeners that only log:

- `MapView.addOnStyleImageMissingListener`, with the name;
- `addOnCanRemoveUnusedStyleImageListener` returning `true`, with the name, so behaviour is unchanged.

Then repeat Part A.

| What Part B shows | Points at |
|---|---|
| `getImage` turns null at the cap | R |
| A missing-image line for `night/…` before the cap is reached | S |
| Image present, reference night, no glyphs on fresh ground | D |

## 6. What a JVM test can show, and why none was run

A JVM test could show only what Part 1 already reads from the code: the plan for v3's `landuse_forest_glyphs` is the
constant `ImageReference.Name("night/tree_dark_irregular")`. `NightImageSwitchTest`'s fake style would draw it the
same at 17.9 and at 18, because the value carries no zoom. That test would pass with the bug present and with any fix
of it, so it is not evidence about the symptom (CLAUDE.md, "a check that passes identically before and after a code
change is suspect"). The existing switch tests cover v1 and v2. v3's six image references are the same six layers by
id, listed by script here and not run through the tests.

Device-only: everything in Part 5, and whether any tile at the cap holds the night pattern. A real MapView cannot be
built under Robolectric in this repo.

## Disclosures

### Confirmed vs inferred

- **Confirmed by reading the code at `6064fef86`:** every row of the Part 1 table, each with its file and line.
- **Confirmed by reading MapLibre at `android-v13.5.0` on GitHub:** each MapLibre claim in Parts 2-4, with its
  file and line.
- **Inferred, not observed:** that the symptom is a per-tile missing pattern, and the three candidates R, S and D.
  No log or device read supports any of them yet.
- **Inferred from the dispatch, not seen by me:** the symptom itself. The screenshots stay off GitHub and were
  relayed by Aspen.

### Could not determine

- Which of R, S or D, if any, is the cause.
- How the owner entered night mode this morning: at the cap, below it, or by a cold start with the camera restored.
  That is the one fact that would favour S.
- The live `street-v3.json` the Worker serves and the live `us.json` maxzoom. Both were read from the repo only. If
  the deployed v3 differs from `map-style/forager-street-v3.json` (for example, if the forest pattern is an
  expression there), Part 2's constant case would need re-checking against the live file.
- Whether the night forest is sometimes drawn but invisible against the night forest fill, rather than missing.
  This is unlikely, because it shows at 17.9 under the same colours.

### Premises that were wrong

- *"The only night rule naming its image is `forager:night-patterns`."* At runtime the app does not read that
  metadata. The night tree pattern comes from the bundled, pre-painted sheet (`NightImageApply.kt:24`). The metadata
  is read only by tests (`NightExceptionsTest.kt:173`). The conclusion drawn from the premise still holds: nothing in
  the style stops the glyphs at any zoom.
- *Hypothesis 1's premise, that MapLibre cross-fades `fill-pattern` between images resolved at z and z+1.* In
  13.5.0 that is true only for feature-dependent expressions, at layout. A constant, or a zoom-only expression, gives
  one image for both ends (Part 2).

### Decided beyond scope

- **Sparse worktree.** `/mnt/work` had 65 MB free (`df`, `/dev/nvme0n1p6` 100%), and a full checkout failed partway.
  I removed the partial directory, pruned the worktree record, and made a sparse worktree at the same path. It holds
  only `CLAUDE.md`, `docs/audits/README.md` and `gradle/libs.versions.toml`. All code was read with `git show` from
  `6064fef86`. The disk being full is the owner's or planner's to look at.
- **No row in `docs/audits/README.md`.** Recent reports (2026-10-24, 2026-10-26) have none, and the index is a
  serialization point while another coder works. Rowan can add one.
- **No test written or run, no CI run** (Part 6). No map-style or v3 file touched. The RECORD is untouched.
