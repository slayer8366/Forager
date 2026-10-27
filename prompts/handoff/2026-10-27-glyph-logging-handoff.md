# Handoff: dispatch 2026-10-27-08, glyph logging build (scratch branch, never merged)

Written 2026-10-27 by the coder (Opus), on Rowan's word that context was near full. No phone session has run yet.

- Branch `glyph-logging`, cut from `origin/pre-main` `6064fef86`. Head `17131505a`.
- CI run 40561284741 (workflow_dispatch, success, tests filtered to NightImageSwitchTest). Artefact `app-debug-apk`:
  versionCode 4096, versionName 1.0.4096+g17131505, sha256 adbd51bb...392519b. Already downloaded to
  `/home/zynergy-labs/s22-glyph/apk/app-debug.apk` (laptop only).
- S22 checked before install: build 4094 installed, `forager.db` user_version 22 (pulled WAL copy in
  `/home/zynergy-labs/s22-glyph/db/`). The APK is schema 22 too (pre-main source), so `adb install -r` is safe, not a
  downgrade. Not yet installed.

## What the build adds (logging only, tag `ForagerGlyphLog`, each line starts `t=<uptime ms>`)
- `setStyle requested`, `setStyleCallback.start`, `.afterNightImages`, `.end`, and on every `cameraIdle`,
  `didFinishLoadingStyle`, `afterSpriteLoaded`, `didBecomeIdle`: zoom; `fullyLoaded`; both pattern layers'
  fill-pattern, visibility, min/max zoom; `img[...]` for `tree_dark_irregular`, `night/tree_dark_irregular`, `tufts`,
  `night/tufts` (WxH@density or null); `rendered[layer]@centre` counts for the two pattern layers and `landuse_park`.
- MapLibre listeners: `spriteRequested`, `spriteLoaded`, `spriteError`, `styleImageMissing`,
  `canRemoveUnusedStyleImage` (returns true, behaviour unchanged), `tile <op>` (all but the two Requested kinds, with
  z, x, y, oz = overscaled zoom), `didFinishRenderingMap`, `renderError`.
- An inert probe: `adb shell run-as com.zynergylabs.forager.app touch files/glyph-relayout`, then a small pan for a
  camera idle. It sets both layers' pattern to "" and back 600 ms later (a forced relayout of every tile), logged as
  `probe:`, `probe.cleared`, `probe.restored`. Run it only at a moment the glyphs are missing.

## S22 steps (report Part 5 plus Hazel's day case); `adb logcat -c` first, then
`adb logcat -v time ForagerGlyphLog:I SightingsMap:I ForagerMapsComeback:I Mbgl:V *:S > /home/zynergy-labs/s22-glyph/<step>.log`
Screenshots to `/home/zynergy-labs/s22-glyph/`, never to GitHub. Glyphs judged by colour count as Hazel did (report
s22-check-v3-8d, Part A). Night Maps is in the side drawer, Tools > Settings.
1. Day control: Trail Map, Night off, at 10T ER 28691 19251 (about 45.326 N, 122.634 W), pinch to the cap. Glyphs?
2. Night on at the cap. Glyphs? Then run the probe once. Glyphs after `probe.restored`?
3. Drag several screen widths at the cap (note the `tile ... oz=18` lines: are they new tiles?). Glyphs?
4. Pinch out slightly, then back to the cap. Glyphs each time?
5. Night off at the cap (Hazel's day case). Day glyphs? Then pinch out to about z15, night on, wait, pinch to cap.
6. Wetland tufts at the cap, night entered at the cap.

## What to read from the logs
- `img[...]=null` at the cap while missing → R (image removed); a `canRemoveUnusedStyleImage` line for a pattern
  name points the same way.
- `styleImageMissing` for a pattern name around the reload → S (layout before the image).
- Images present, `fullyLoaded=true`, `rendered[landuse_forest_glyphs]@centre` > 0, glyphs absent → the tile has the
  features and the image; the loss is in the tile's render state (pattern position or atlas). If the probe then
  restores glyphs, a fresh layout fixes it, and the cheap fix candidate is one forced relayout after the reloaded
  style and its sprite have both loaded. If the probe does not, it is below the app (report's D).
- Order of `setStyle requested` / `spriteRequested` / `spriteLoaded` / first `tile EndParse` for oz at the held zoom
  tells whether those tiles were laid out before the sprite arrived.

Reading of MapLibre 13.5.0 done so far (unverified on device): retained tiles are re-laid-out on a reload; images
added later do not re-lay-out tiles (`render_orchestrator.cpp:221-248`, only removed or resized images set
`hasImageDiff`); fill drawables built while a tile's atlas texture is null get no atlas tweaker
(`render_fill_layer.cpp:214-229`), so `texsize` stays 0 (`fill_layer_tweaker.cpp:89-92`). Candidates, not findings.

Rules carried: restore any phone setting changed (screen timeout 30 min stays); leave the RECORD alone; never merge
this branch; GitHub-only network; no Gradle on the laptop.
