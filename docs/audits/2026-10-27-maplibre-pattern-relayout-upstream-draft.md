# Draft: upstream bug report for maplibre-native (not filed)

Dispatch 2026-10-27-11, RECORD -1742. Written 2026-10-27 by a coder (Opus) for the owner to file; nothing has been
posted anywhere. The evidence is the S22 logging run in `docs/audits/2026-10-27-glyph-cause.md` (branch
`glyph-logging`, `4d9a2eb64`); the workaround is `PatternRelayout.kt` (PR #429). Before filing, the owner may want to
search the tracker for an existing report; that search was not made (this session has no network but GitHub, and was
told to post nothing).

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
  (by day and by night, at the same zoom). Our app now does this after every reload as a workaround.
- One clue, not explained: after a reload whose new style no longer names some images, `styleImageMissing` is reported
  for the **old** style's pattern names, after the new sprite has loaded and while the kept tile is being laid out
  again, as if a request made with the old style's names were still open.

**Possibly related code** (read at `android-v13.5.0`, not confirmed as the cause): `ImageManager::getImages`
(`image_manager.cpp:121-150`); the dynamic texture atlas skipping an upload when a bin is already held
(`dynamic_texture_atlas.cpp`, `isUploadNeeded`) while the old layout frees its bins on destruction
(`geometry_tile.cpp:169`); a fill drawable built while the tile has no atlas texture (`render_fill_layer.cpp:214-229`).

**Workaround.** After the reload, once the style callback has run and the sprite has loaded, set each pattern layer's
`fill-pattern` to `""`, let the map render a full frame, and set it back.
