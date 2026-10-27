# Map patterns lost at the reload zoom: the cause, from the S22 logging run

Dispatch 2026-10-27-08 (RECORD -1738). A coder (Opus), taking over the phone session from the coder who wrote
`prompts/handoff/2026-10-27-glyph-logging-handoff.md` (this branch, `540379764`). Run on the S22 Ultra (test phone)
over adb, 2026-10-27 18:45 to 19:00 UTC, on the logging build 1.0.4096+g17131505 (CI run 40561284741, APK sha256
`adbd51bb…392519b`, checked before install). Written 2026-10-27T19:02Z. No fix built. Logs and screenshots are on the
laptop in `/home/zynergy-labs/s22-glyph/` (`session.log`, `shots/`) and are described here in words only.

## Verdict

**The cause is the drawing state of tiles that survive a style reload.** It is not R, not S as the report defined
it, and not D as defined. When the style reloads, MapLibre keeps the tiles already on screen at the held zoom and
lays them out again. After that, those tiles have the features, the image and the right pattern name, and still draw
no pattern. A tile laid out from fresh draws it: a new tile at the same zoom, the same tile at another zoom, or the
same tile after one more forced relayout (the probe). The fault is inside MapLibre Native 13.5.0, below the app's
calls. Which line of MapLibre is at fault was not found (see "Could not determine").

**The cheapest fix:** after each style reload, once the style and its sprite have both loaded, force one more
relayout of the pattern layers, the way the probe did: set each fill pattern to `""`, then back to its value. On the
phone, the probe brought the patterns back by night and by day (below). It is a workaround for a MapLibre fault, so
it goes to the owner as a choice, beside reporting the fault upstream.

## What was run

The phone was on build 4094, with `forager.db` `user_version` 22, before the install. The logging build's
`ForagerDatabase.version` is 22 (`ForagerDatabase.kt:206` on this branch). `adb install -r` installed it. Logcat was
captured for the whole session into one file (374,562 lines). Glyphs were judged by colour count over the map area
x 100–1150, y 450–2300, as Hazel did. The colours differ from Hazel's table on this run. By day the forest is
(169, 211, 155) and the glyph (62, 122, 58). At night the forest is (68, 104, 53) and the glyph (93, 128, 79). With
glyphs, the glyph colour is about 3% of the forest. Without them it is 0, with the forest at one flat colour.

Camera: about 45.33 N, 122.6 W (the 10T ER 28691 19251 area), on forest a few hundred metres from the point, at
the cap (zoom 18.00, read from the `cameraIdle` lines). Two z15 tiles matter: **5221/11747** (on screen when the
style reloaded) and **5222/11747** (east of it, loaded later).

**A logging slip to know when reading the log:** the tile lines label their numbers wrongly. MapLibre's
`OnTileActionListener.onTileAction(op, x, y, z, wrap, overscaledZ, sourceID)` (`MapView.java:1500` at
`android-v13.5.0`) was bound as `(op, z, x, y, …)` in `GlyphDiagnostics.kt`. So a line printed
`z=5221 x=11747 y=15 … oz=18` is tile x=5221, y=11747, z=15, drawn at overscaled zoom 18. `oz` is right. It is
written below the correct way.

## Results, step by step

| Step | Result |
|---|---|
| 1. Day control at the cap (day loaded at z12, then zoomed in) | **Glyphs show.** Glyph colour 1.68% over 54.7% forest. |
| 2. Night entered at the cap | **No glyphs.** Night forest at 57.6% flat, glyph colour 0. Reproduces the report. Run twice, same both times. |
| 2, probe while missing | **Glyphs come back** after `probe.restored`. Glyph colour 1.61%. |
| 5a. Day entered at the cap (Hazel's day case) | **No day glyphs.** Day forest at 56.5% flat, glyph colour 0. |
| 5a, probe while missing | **Day glyphs come back.** Glyph colour 1.68%, the same as step 1. |
| 3. Drag several screen widths at the cap, at night | **Glyphs show on the new tile 5222, and not on tile 5221.** One screenshot (`shots/s3d-11.png`) has the edge between them across one forest polygon: no glyphs left of about x = 400, glyphs right of it. The edge is straight and vertical, where a tile edge would be. |
| 4. Out and in, at that edge | At z17.81, **glyphs on both sides** (a fresh layout of tile 5221 at oz 17). Back at 18.00, **no glyphs on 5221's side again**, with no new parse in the log: the stale oz 18 tile was reused. |
| 5. Night off at the cap, out to z15.84, night on, in to the cap | At z15.84 right after night on: **no glyphs** (the reload zoom again). At the cap: **glyphs on both tiles**, each laid out fresh at oz 18. |
| 6. Wetland tufts at the cap, night entered at the cap | **Not run.** No wetland in the area viewed at z12.8 and z14.25, and I did not search further. Hazel's run has tufts missing in the same case (`2026-10-27-s22-check-v3-8d.md`, Part A, extra checks). The logs cover the tufts layer in every snapshot, and it behaves like the forest layer in all of them. |

## The evidence lines

**The night reload at the cap (step 2).** Uptime ms in `t=`. Every name and image is present before the tile is
laid out again, and nothing goes missing or is removed:

```
t=1124241212 setStyle requested night=true source=Uri zoom=18.0
t=1124241270 spriteRequested id=default url=…/sprites/v5/light zoom=18.0
t=1124241278 setStyleCallback.start zoom=18.00 … img[tree_dark_irregular]=null img[night/tree_dark_irregular]=null …
Street night: 61 night images added, 6 image references switched, 0 refused by MapLibre, 0 left in day images; no night copy missing.
t=1124241416 setStyleCallback.afterNightImages zoom=18.00 fullyLoaded=true landuse_forest_glyphs=[night/tree_dark_irregular vis=visible min=12.0 max=Infinity] … img[night/tree_dark_irregular]=128x128@600 … rendered[landuse_forest_glyphs]@centre=1
t=1124241442 didFinishLoadingStyle zoom=18.00 …
t=1124241458 spriteLoaded id=default url=…/sprites/v5/light
t=1124241461 afterSpriteLoaded zoom=18.00 … img[tree_dark_irregular]=128x128@600 img[night/tree_dark_irregular]=128x128@600 img[tufts]=64x64@600 img[night/tufts]=64x64@600 rendered[landuse_forest_glyphs]@centre=1
t=1124241462 tile StartParse src=protomaps (5221/11747/15) oz=18
t=1124241476 tile EndParse src=protomaps (5221/11747/15) oz=18
```

(The tile lines are shown with the corrected order.) The tile on screen was laid out again 4 ms after the sprite
loaded and 46 ms after the night images were added. Screenshot afterwards: no glyphs.

**The probe while missing (step 2):**

```
t=1124291049 probe: relayout requested; saved=[(landuse_forest_glyphs, night/tree_dark_irregular), (landuse_wetland_tufts, night/tufts)]
t=1124291058 tile StartParse src=protomaps (5221/11747/15) oz=18
t=1124291072 tile EndParse …
t=1124291657 probe.restored zoom=18.00 fullyLoaded=true landuse_forest_glyphs=[night/tree_dark_irregular …] …
t=1124291661 tile StartParse src=protomaps (5221/11747/15) oz=18
t=1124291665 tile EndParse …
```

Screenshot afterwards: glyphs. The same tile, the same zoom, the same image, the same name. The one difference is
that this layout came on a tile whose previous layout had no pattern at all.

**Fresh ground at the cap (step 3):** `t=1124627302 tile LoadFromNetwork src=protomaps (5222/11747/15) oz=18`,
`t=1124627323 tile EndParse …`. That tile draws glyphs. Tile 5221 never got a new oz 18 layout in this step, and
still draws none.

**The tallies:** 0 `canRemoveUnusedStyleImage` lines in the whole session. 0 `spriteError` and 0 `renderError`.
In no snapshot does a pattern layer name `night/tree_dark_irregular` while that image reads null (counted over
every snapshot line in the log).

### How each candidate reads

- **R (image removed): ruled out.** No removal was ever offered (0 lines), and the image was present in every
  snapshot taken while the layer named it, including at the cap while the glyphs were missing.
- **S (laid out before the image was added): ruled out as the report defined it.** In the night case the night
  images were added 46 ms, and the sprite loaded 4 ms, before the tile's only relayout began. No
  `styleImageMissing` line named a pattern the layer then used. The broader shape Hazel inferred, "a style load at
  the held zoom", is right: it is a statement about tiles that survive the reload, not about when the image
  arrived.
- **D (below the app, deterministic for runtime patterns at oz 18): ruled out as defined.** The day sprite's own
  patterns go missing the same way (step 5a), and a fresh oz 18 tile draws the runtime night pattern (step 3).
  The fault is still below the app, as D said, but it is about surviving tiles, not about runtime images or oz 18.
- **The drawing state: this one.** Image present, `fullyLoaded=true`, the night name read back,
  `rendered[landuse_forest_glyphs]@centre=1`, no glyphs drawn. A fresh layout fixes it.

### One more clue, not explained

Every night-to-day reload logged `styleImageMissing` for the **night** names, in a day style that no longer names
them. That happened three times: at 18.0 (`t=1124523528`), at 18.0 (`t=1124838812`) and at z12.84
(`t=1124934074`). The first came 14 ms after the day sprite loaded and before the retained tiles' relayout ended:

```
t=1124523505 tile StartParse src=protomaps (5221/11747/15) oz=18
t=1124523514 spriteLoaded id=default …
t=1124523528 styleImageMissing name=night/tufts zoom=18.0
t=1124523528 styleImageMissing name=night/tree_dark_irregular zoom=18.0
t=1124523537 tile EndParse …
```

So during a reload, an image request made with the **old** style's pattern names is still open, and is answered
after the new sprite loads. The day-to-night reload cannot show this, because the old names there (the day sprite's)
exist again in the new style. My inference, not confirmed: a surviving tile's request from the old style is
answered with the old style's images, or its layout is built partly from it. That would leave the tile with no
position for the name the new style draws. It would explain why only surviving tiles fail. It would also explain
why the probe, whose first step drops the pattern entirely, clears it. I did not find the line in MapLibre that
does it (see "Could not determine").

## The proposed fix and its test (not built)

**Fix F1: one forced pattern relayout after a reload.** It would be a new function, in a new file beside
`NightImageApply.kt`, called from `MapStyleReload`. After `setStyle`'s callback has run **and** the style's sprite
has loaded, it reads every fill layer whose `fill-pattern` is set, sets each to `""`, and sets each back to its
read value once the map has rendered that state. Today that is v3's `landuse_forest_glyphs` and
`landuse_wetland_tufts`. The layers are read from the style, not listed in code. It runs on every style reload, by
day and by night, because the day patterns fail the same way. It changes nothing on a cold start, which lays out
fresh tiles anyway, except one wasted relayout.

- **What is known:** the probe, which is this sequence with a 600 ms gap, fixed both cases it was run in (night
  at the cap, day at the cap).
- **What is not known:** whether a shorter gap, or one frame, works. The relayout MapLibre already does on the
  reload starts after the sprite loads and still fails (step 2). So "relayout once more, straight away" is not shown
  to be enough. The probe's form is: relayout with no pattern, then relayout with the pattern. The fix should keep
  that form until the phone shows a shorter one works.
- **Cost:** two relayouts of the tiles on screen after each reload. The glyphs are already missing at that moment,
  so the `""` step shows nothing new to the user.
- **Options for the owner, alongside F1:** report the fault to MapLibre with a minimal reproduction (a style with
  one fill pattern, reloaded at a held zoom). Or avoid reloading the whole style on a Night Maps toggle, which is a
  design change, much larger than F1.

**JVM test (fails before, passes after).** In the style of `NightImageSwitchTest`, the test uses a fake style that
records `fillPattern` sets, driven through the reload's real hook (the sprite-loaded step that `MapStyleReload`
would call). It asserts that each pattern layer gets exactly `""` and then its original value, in that order, and
ends on the original. It also asserts that layers with no pattern are not touched, and that the sequence is skipped
when the style changed before the restore. Before F1 it fails: there is no such call, so the fake records nothing.
**It shows only the wiring.** It cannot show that the glyphs come back, because a real MapView does not run under
Robolectric in this repo, and the fault is in MapLibre's tiles. So the gate is the device check below.

**Device check (the gate).** On the S22, on Trail Map, at the same spot, with the same colour count. Cheap steps
first.

1. Night on at the cap. Pass: night glyph colour > 1% of the map area, 4 s after the switch. Evidence: screenshot
   and count. On build 4094 this reads 0, so this step fails on the unfixed build.
2. Night off at the cap. Pass: day glyph colour > 1%. Evidence: as above. On 4094 it reads 0.
3. The tile edge: from step 1, drag east until a tile edge crosses forest. Pass: glyphs on both sides of the
   edge. Evidence: per-column counts (unfixed: 0 on the old tile's side).
4. Out and in at that edge (to about 17.8, then double-tap to the cap). Pass: glyphs on both sides at the cap.
5. Tufts: night on at the cap over a wetland. Pass: tufts drawn.
6. Observation, not a gate: in the log, the two pattern relayouts happen once per reload, not in a loop.

## Phone state at the end

Night Maps is off, as at the start (toggled on and off an even number of times; last `setStyle requested
night=false`, last screenshot in day colours). The screen timeout is 1,800,000 ms (Hazel's 30 minutes),
unchanged. Build 4094 is back: the artefact of CI run 40547553913 (head `6064fef8…`, push, success). The zip
matched GitHub's digest `cfdfe52a…`, and the APK sha256 is `942d2e84…`, the same as Hazel's copy. `user_version` was
22 before and after. It was installed with `adb install -r -d`. `-r` alone refuses a lower `versionCode` (4096 to
4094); the schema is the same, so this is not a schema downgrade. After launch the row count of every table matched
the count taken before the logging build went on (one hash over all tables, equal).

## Disclosures

### Confirmed vs inferred

- **Confirmed from the log and screenshots:** every row of the results table. The evidence lines and tallies as
  quoted. That the probe restores the patterns by night and by day. That a new oz 18 tile draws them while the
  surviving one beside it does not, in one screenshot.
- **Confirmed by reading MapLibre at `android-v13.5.0` on GitHub:** the tile listener's parameter order
  (`MapView.java:1500`). `ImageManager::getImages` removes a tile's earlier request before it queues a new one
  (`image_manager.cpp:121-150`). Removed images trigger a relayout, and added ones do not (`render_orchestrator.cpp:221-248`).
- **Inferred:** that the fault is in how MapLibre treats tiles that survive a style reload. That the old-name image
  request is part of it. That F1 fixes it in general, rather than only in the two cases the probe was run in.

### Could not determine

- The MapLibre line at fault. I read `image_manager.cpp`, `geometry_tile.cpp`, `geometry_tile_worker.cpp`,
  `tile_pyramid.cpp`, `render_fill_layer.cpp` and `dynamic_texture_atlas.cpp`. I found no path that clearly gives a
  surviving tile a layout without the new pattern. The candidates I did not close are these. The shared atlas's
  ref-counted bins skip the upload when a bin is already held (`dynamic_texture_atlas.cpp`, `isUploadNeeded`), and
  the old layout frees its bins when it is destroyed (`geometry_tile.cpp:169`). Or a fill drawable is built while
  the tile has no atlas texture, and so gets no atlas tweaker (`render_fill_layer.cpp:214-229`).
- Whether a gap shorter than 600 ms, or a single relayout, is enough for F1.
- Step 6 (tufts), not run: no wetland found.
- Whether symbol icons (not patterns) suffer the same. Not measured.

### Premises that were wrong

- **The report's S, "laid out in a window without the image".** In the night case no such window was seen: the
  images were present before the tile's relayout began. The fault is in the surviving tile, not in the timing of the
  image.
- **Hazel's step 3, "fresh ground at the cap is missing them too".** At oz 18, one z15 tile spans several screen
  widths, so a few widths of dragging can stay on the same surviving tile. On this run, new tiles drew the patterns
  and only the surviving tile did not. Her observation was right for the ground she was on. It was not new
  ground in MapLibre's sense.
- **Rowan's step 7 for this session, `adb install -r` back to 4094.** That is a `versionCode` downgrade, and it needs `-d`.
  The schema is the same, so `-r -d` was used. The handoff's `adb install -r` for the forward install was correct.
- **The handoff's tile line fields** (`z, x, y`) are mislabelled in the logging code (above).

### Decided beyond scope

- **`-r -d` for the restore** (above).
- **One logcat capture for the whole session**, instead of one file per step. Steps are told apart by `t=` and by
  the `setStyle requested` lines.
- **The MapLibre source read**, done to look for the line at fault. It was fetched from GitHub
  (`raw.githubusercontent.com`) into `/home/zynergy-labs/s22-glyph/mbgl/`, laptop only.
- **No row in `docs/audits/README.md`**, as with the code report: the index is a serialization point, and this
  branch is never merged. Rowan can add one.
