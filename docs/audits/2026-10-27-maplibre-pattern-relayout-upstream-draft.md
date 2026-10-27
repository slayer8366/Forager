# Draft: upstream bug report for maplibre-native (not filed)

Dispatch 2026-10-27-11, RECORD -1742; corrected under dispatch 2026-10-27-13 (RECORD -1746, -1748), where the first
draft's evidence and workaround described a form that failed on the S22, and again under its Amendment 1 (RECORD -1755),
where Sorrel's review of PR #429 (`docs/reviews/2026-10-27-sorrel-pr429.md`, branch `sorrel-pr429`) found the
transition bullet wrong. Written 2026-10-27 by a coder (Opus) for the
owner to file; nothing has been posted anywhere. The evidence is the S22 logging run in `docs/audits/2026-10-27-glyph-cause.md` (branch
`glyph-logging`, `4d9a2eb64`); the workaround is `PatternRelayout.kt` (PR #429). Sorrel searched the tracker on
2026-10-27 (`fill-pattern`, `pattern setStyle`, `pattern style reload`, `fill-pattern setStyle`) and found no report of a
pattern lost across a reload at a held zoom; two possibly related issues are listed at the end.

---

**Title:** Android: `fill-pattern` not drawn on tiles retained across `setStyle` at the same zoom

**Version:** MapLibre Native Android `org.maplibre.gl:android-sdk:13.5.0` (the default
`android-sdk` artefact). Device: Samsung Galaxy S22 Ultra (SM-S908U), Android 16. Not tried on other versions or devices.

**What happens.** After `MapLibreMap.setStyle(...)` is called again while the camera stays where it is, fill layers with
a `fill-pattern` draw no pattern on the tiles that were already on screen. The fill colour under the pattern draws;
the pattern does not. The same happens whether the new style's pattern images come from the sprite or are added at
runtime with `Style.addImages` in the style-loaded callback. Tiles that load after the reload (panning onto new ground,
or zooming to a level whose tiles are laid out fresh) draw the pattern, so one polygon can show the pattern on one side
of a tile edge and not on the other.

**Minimal reproduction.**

1. A vector style with one `fill` layer whose `paint` has `"fill-pattern": "<an image in the sprite>"`, over polygons
   large enough to fill the screen.
2. Load it, zoom in to a level where the pattern shows (we saw it at zoom 18, overscaled from z15 tiles, and at 15.84).
3. Without moving the camera, call `setStyle` again with the same style (or a copy differing only in colours).
4. Wait for the style and its sprite to load. The pattern is gone on every tile that was on screen.
5. Pan several screen widths onto tiles that were not loaded before: the pattern draws there.

**Expected.** The pattern draws on every tile after the reload, as before it.

**Evidence** (from logging `OnSpriteLoadedListener`, `OnTileActionListener`, `OnStyleImageMissingListener`,
`OnCanRemoveUnusedStyleImageListener` and the layer's own properties, with screenshots and pixel colour counts):

- After the reload, the layer's `fill-pattern` reads back the right name, the image is in the style at its size and
  pixel ratio, `Style.isFullyLoaded()` is true, and `queryRenderedFeatures` finds the layer's features at the centre,
  yet no pattern pixel is drawn (pattern colour 0% of the map area, against about 1.6% before the reload).
- The kept tile is parsed again after the sprite loads (`StartParse`/`EndParse` 4 ms after `onSpriteLoaded`); that
  layout draws no pattern.
- No `canRemoveUnusedStyleImage`, sprite error or render error was reported in the whole session.
- Setting the layer's `fill-pattern` to `""` and back to its value afterwards makes the kept tile draw the pattern again
  (by day and by night, at the same zoom), **but only when the clear comes late enough.** With T0 the moment the
  `setStyle` callback has run and `onSpriteLoaded` has fired: a clear at T0 +0 to +250 ms leaves the
  pattern at 0; a clear at +300, +500 or +2000 ms brings it back, by day and by night. The threshold lies between +261 ms
  and +300 ms. The gap between the clear and the restore matters too: a restore at the first full frame 2-7 ms after the
  clear can fail, while a clear at +400 ms with the restore at the first full frame at least 100 ms later worked 5 of 5.
- `fill-pattern` is a cross-faded paint property that transitions (`fill_layer_properties.hpp:40`,
  `properties.hpp:31-64`), and a style that sets no `transition` gets 300 ms (`parser.hpp:40`, `constants.hpp:59`),
  which matches the threshold. The parser holds that 300 ms as a set value, so `Style.getTransition()` on Android reads
  300 (`native_map_view.cpp:921-926`), and our app, which reads the transition back in the style-loaded callback and
  writes it again with `Style.setTransition(TransitionOptions(duration, delay, false))` to turn the placement fade off,
  keeps it: the style's transition is 300 ms duration, 0 delay, throughout (our log reads "style transition 300 ms" on
  every reload). That the 300 ms transition is the cause is not shown.
- Setting `fill-pattern-transition` to `{duration: 0, delay: 0}` on the pattern layers at the start of the style-loaded
  callback, with no clear and restore, did not fix it: patterns came back 3 of 3 after a reload whose callback also
  switched the pattern names (our night style), and stayed at 0 3 of 3 after one that did not (our day style).
- One clue, not explained: after a reload whose new style no longer names some images, `styleImageMissing` is reported
  for the **old** style's pattern names, after the new sprite has loaded and while the kept tile is being laid out
  again, as if a request made with the old style's names were still open.

**Possibly related code** (read at `android-v13.5.0`, not confirmed as the cause): `ImageManager::getImages`
(`image_manager.cpp:121-150`); the dynamic texture atlas skipping an upload when a bin is already held
(`dynamic_texture_atlas.cpp`, `isUploadNeeded`) while the old layout frees its bins on destruction
(`geometry_tile.cpp:169`); a fill drawable built while the tile has no atlas texture (`render_fill_layer.cpp:214-229`).

**Workaround.** After the reload, once the style callback has run and the sprite has loaded, wait the style's
transition (300 ms, the default when the style sets none; we never wait less) plus 100 ms; then set each pattern layer's
`fill-pattern` to `""`, and set it back at the first fully rendered frame at least 100 ms after that. Clearing straight
after the sprite loads, and restoring at the next full frame, does not work. We skip it on the map's first style, which
keeps no tiles, and restore at once if another style is requested before the restore.

**Possibly related** (open; neither describes a reload at a held zoom):
- maplibre/maplibre-native#4527, "Some objects with `fill-pattern` disappears on zoom 16 on Android" (Android 13.5.1):
  one polygon of a pair vanishes with its pattern; removing `fill-pattern` fixes it; no `setStyle` involved.
- maplibre/maplibre-native#2650, "Android Polygon Fill Layer display weird at tile boundaries on high zoom level"
  (Android 11.0.x): fill-pattern colour and pattern artefacts at tile boundaries at high zoom.
