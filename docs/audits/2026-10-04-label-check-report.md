# Does MapLibre Android 13.5.0 draw the labelled Forager style, online and offline? (dispatch 2026-09-28-490, measurement only)

**Status: measured on the S22. The spike's code is on `label-check`, not merged.** The test app was uninstalled afterwards, its offline database with it. Forager (1.0.2622 on the S22), the Worker, both Cloudflare accounts and the Pi were untouched.

**Date:** 2026-10-04 (UTC).
**Dispatch:** `prompts/preserved/2026-10-04-05.md` (from `origin/records-after-164`), with the planner's addition: the HTTPS-glyphs download as well.
**Base:** `origin/main` at `f4e6726a` (PR #165), checked against the remote.
**The owner's words,** to this session directly:
- "Yes, start -490" and "Yes, use the phone";
- for each network change: "It's back online" and "Aeroplane mode on".

Screenshots, logs and scripts are in `~/Zynergy/device-evidence/2026-10-04-label-check/` (public places only). The APK and build output are on the owner's USB drive.

## In plain terms

1. **The labelled Forager style works on the phone's map engine, MapLibre Android 13.5.0, online:** street names, place names, route shields and point-of-interest icons, at a town, a forest and a coast, with no errors.
2. **It also downloads as an offline area without the old crash,** both with its fonts served over HTTP and with them served over HTTPS (as production would). Each download finished in about 3 to 4 seconds for a 2 × 2 km area at zooms 10 to 15.
3. **In aeroplane mode, the downloaded area draws with its labels.**
4. **So the crash PR #23 found does not happen with this style on the same engine version.** Why the old style crashed was not narrowed: there was nothing to narrow here.

## Verified before building (reported by message first; the planner accepted it)

- **The crash claim:** `app/src/main/java/com/zynergylabs/forager/app/map/MapLibreOfflineMapRepository.kt:60-63` (in `map/`), as cited. The wiring doc says the bug is "specific to *offline downloads*" (`docs/plans/pmtiles-worker-android-wiring.md:177-179`). `maplibre-migration.md` §7 does not contain the crash, as the survey found.
- **PR #23's description** is the evidence:
  - The MapLibre demo style (two `text-field` layers, `Open Sans Semibold` glyphs) crashed natively during an `OfflineManager` download, right after the first `onStatusChanged` at `completed=0/1`, at every region size tried.
  - The same style with its labels removed, and a raster style, both downloaded cleanly.
- **PR #23 already ran on 13.5.0.** It was closed, not merged; its head `cbfa6f39` pins `maplibre = "13.5.0"`. So the crash was seen on the very version Forager pins. This was not in the survey.
- **Forager's download:** `OfflineTilePyramidRegionDefinition(style URL, bounds, 10.0, 15.0, density)`, with the style over HTTPS because an `asset://` style hung.

## The test app (`spikes/label-check/`)

- A separate Gradle project (`applicationId com.zynergylabs.labelcheck`) on MapLibre `android-sdk` 13.5.0, AGP 9.3.1 and Kotlin 2.3.10, as Forager pins them.
- One Activity, driven by adb intents: **view** (style, centre, zoom), **download** (the same definition Forager uses: tile pyramid, z10 to z15, the screen's density 3.75) and **list**. Every download step is appended and flushed to a file, so the last step before a native crash would survive it.
- **The style:** `map-style/forager-light.json` from main, served by `map-style/serve.py` on the laptop and reached from the phone with `adb reverse tcp:8765` over USB, so nothing went beyond the laptop and phone. Cleartext is allowed to `localhost` only.
- **Tiles:** the old Worker's `/us.json` (not the Pi).
- **The HTTPS-glyphs style:** `map-style/forager-light.maputnik.json`, identical layers, with fonts and icons from `https://protomaps.github.io/basemaps-assets/`.

## Results

### 1. Online (`online/`)

| Place | Zoom | Result |
|---|---|---|
| Downtown Portland | 15.5 | Fully rendered. Street names along the roads, POI icons with labels (post office, university, a restaurant), "Portland" |
| Ramona Falls, Mount Hood National Forest | 14 | Fully rendered. "Rushing Water Creek"; trails as thin white lines |
| Cannon Beach | 13 | Fully rendered. The Highway 101 shield, beach icons, "Ecola Creek", "Tolovana Park" |

Each view: `rendering finished, fully=true`, 0 error lines from the app or MapLibre (`Mbgl`), and an empty crash buffer.

### 2. Offline downloads (`offline/`)

The area was 2 × 2 km at Cannon Beach, zooms 10 to 15, each download in a fresh region.

| Style | Steps | Result |
|---|---|---|
| Original (glyphs at `http://localhost`) | `completed=0/1` → `1/297` → … → `318/318` | **Complete in 3.97 s**: 6,023,911 bytes, 21 tiles. 0 errors, empty crash buffer |
| HTTPS glyphs (`protomaps.github.io`) | `completed=0/1` → `1/318` → … → `318/318` | **Complete in 3.06 s**: 6,023,922 bytes, 21 tiles. 0 errors, empty crash buffer |

**Both passed the `completed=0/1` checkpoint where PR #23's crash happened.**

**What a pack holds, inferred from the counts:**
- The laptop server handed out 97 distinct glyph ranges per font (0–255 through 65280–65535) for each of the three fonts.
- That adds up exactly: 1 style, 1 TileJSON, 3 × 97 glyph ranges and 4 sprite files make the first status's 297 required resources.
- So 13.5.0 downloads a fixed subset of 97 ranges per font, not all 256. A label in a script outside those ranges would have no glyphs offline. English needs only the first range.

### 3. Offline view, in aeroplane mode (`offline-view/`)

**Setup:** `airplane_mode_on=1`, no active network, `adb reverse` removed, and the laptop server stopped. So nothing could be fetched; everything came from the phone's offline database.

**Both styles at zooms 14 and 15:** fully rendered, 0 errors, empty crash buffer, **with labels and icons**:
- street names; "Cannon Beach";
- POIs with icons ("Pig N' Pancake", "Cannon Beach City Hall", the history center and museum);
- "Oregon Coast Highway" and the 101 shield.

**One limit, and how the HTTPS run answers it.** MapLibre keeps packs and its ambient cache in one database, and the original style had been viewed online at Cannon Beach before. So its offline labels could have come from the cache. The HTTPS style was never viewed online: its fonts and icons could only have come from its own download. Its offline views therefore show a pack drawing labels with no network.

### 4. Against the GL JS preview

The phone's screenshots and -485's GL JS screenshots of the same places match: the same layers, colours, label placement style, shields and icons. The phone uses the `@2x` sprite (pixel ratio 3.75), and its icons look sharp. No visible difference was found. Small differences in label placement are expected between renderers, and none stood out.

## Not done

- **Narrowing (step 3):** not needed, since nothing crashed.
- **PR #23's original demo style was not re-run.** So why it crashed in 2026-09 on the same 13.5.0 was not determined: its font (`Open Sans Semibold`), its glyph host (`demotiles.maplibre.org`) or something else. Re-running it in this app is cheap if the answer matters.

## Disclosure

**Confirmed vs inferred.**
- Confirmed on the S22: online rendering at three places; two complete offline downloads with no crash; offline rendering with labels in aeroplane mode, with the HTTPS style's resources available only from its pack.
- Inferred: the 97-range glyph subset, from the server's counts; why PR #23's style crashed and this one does not.

**Could not determine.**
- The cause of PR #23's crash.
- How labels in scripts outside the 97 downloaded ranges would draw offline.

**Premises that were wrong.**
- The dispatch's path for the crash claim omitted the folder (`map/`).
- The record's description of the crash as a fact about labelled styles on 13.5.0 does not hold for this style.

**Decided beyond scope.**
1. **The HTTPS download used `forager-light.maputnik.json`,** whose icons also come from `protomaps.github.io`, not only its fonts.
2. **Each offline view used the same style address its pack was downloaded with,** as an app would.
3. **The `pinch.sh` in the phone's `/data/local/tmp`,** left from earlier work, was not touched.
4. **Laptop space:** it fell from 2,493 to 1,735 MB during this spike's one build, and no file newer than 30 minutes larger than 20 MB could be found anywhere on the disk. So the cause is not determined. Build output was on the USB drive.
