# Our own topo map: plan report

Dispatch 2026-11-01-01 (RECORD -1911). Juniper Sounding, researcher. Read-only: nothing was built, downloaded in bulk, or run on the Pi or a phone.
Evidence: `docs/plans/data/2026-11-01-own-topo/`. Method and predictions were committed first (5fb4a27d2).
Bases: pre-main d54189479, pi-build de30a84ee (both still current, read 2026-11-01); records-after-173 46b8deafc; Forager-cartography main f1cf681.

Marks: **[R]** read (file or page cited), **[C]** computed (`sizes.py`, `tiles.py`, `dem_slope.py`, outputs beside them), **[I]** inferred. **[A]** means read from Aspen's reply (`prompts/aspen/2026-11-01-02-aspen-to-rowan-topo-heights.md`, 46b8deafc): Aspen's figure, not verified by me, because the Pi is out of bounds for this dispatch.

## The short answer

- **It is feasible, cheap to host, and small on phones.** It adds about 30-40 GB on the server in the likely case (range 17-200 GB), about $0.50 a month in the likely case, and about 2-4 MB to a default offline area.
- **The real work is a new height pull, a build the Pi has never done, and one phone test nobody has run.** The Pi's 3″ grid can't make the 40 ft contours as ruled. It needs 1″ heights: about 48 GB from the US and up to 84 GB from Canada.
- **Honest time: 3 to 6 weeks of calendar time from a go**, assuming the phone test passes and the Pi has its Monday-to-Saturday windows.
- **Recommendation:** run the two-cell pilot and the phone check first (about a week), and don't hold the release on topo until they report. The release gate is still -1628, the whole trail navigation plan. If trail navigation lands first, a topo that isn't ready shouldn't hold it, because Topographical keeps working on the outside map.
- **One conflict needs a ruling first.** Replacing Topographical removes worldwide topo for users outside the US and Canada, the opposite of the owner's 10-31 global ask, unless a fallback is chosen (section 5).

## 1. Topographical today, and what own topo would be

**Today:**
- Topographical is an outside raster service, OpenTopoMap, to zoom 17, online only (`Basemap.kt:152-164`) [R]. It keeps being used by no-signal fallback rules: with no signal and a downloaded area, the map switches to Trail Map (`NoSignalTrailMap.kt:26-60`) [R].
- **Who reaches it:** anyone who taps the "Topo" chip (`MapMode.kt:45`; `MapModeChips.kt:112`, reached from `MapTypePanel` at `CompactMapPickers.kt:96` and `CartographyEntryReportScreen.kt:648`), and anyone holding a stored key that no longer names a mode, such as former Satellite users (`MapMode.kt:66`) [R]. New users open on Trail (`MapMode.kt:58`) [R]. How many users that is: could not determine.
- **Night:** inverted raster, crossfading to OpenStreetMap's raster or Trail Map below zoom 9.5-9.7 (`BasemapStyles.kt:68-90`, `:141`, `:269`) [R].
- **Trail Map** is our vector map to tile zoom 15, shown to 18, with no hillshade or contour anywhere in `app/src/main` (grep: only comments) [R]. It is the only map offline areas store (`StreetStyle.kt`, RECORD -1554) [R].

**Already ruled, and not to be reopened by accident:** on 2026-10-22 the owner took all of Sage's recommendations: own Topo is Trail Map plus relief and contours, replacing OpenTopoMap in the Topographical slot (`docs/design/2026-10-22-topo-rulings.md`, Forager-cartography) [R]. On 2026-10-30 the owner also liked "a faint hillshade" on Trail Map, reopening "Street stays flat" (`2026-10-30-fill-in-rulings.md:51-57`) [R]. The dispatch asks the question afresh, so the three options are laid out below.

| Option | What the user sees | Costs | Gains |
|---|---|---|---|
| **A. Replace** (ruled 10-22) | Chips stay Trail, Topo, OSM. Topo looks like Trail Map with shading and brown contours. | Outside coverage Topo needs an answer (section 5). The OpenTopoMap look goes. | No more dependence on an outside topo server; Topo works offline; night is ours. |
| **B. Beside** | Four chips: Trail, Topo (ours), OpenTopoMap, OSM. | One more choice, the thing "Don't make me think" warns against. | Worldwide topo stays. |
| **C. Add to Trail** | Three chips. Trail gains contours; Topo stays OpenTopoMap. | Clutter on the default map; two topo looks. | Least change to chips. |

**Recommendation: A**, with the same relief archive also feeding Trail Map's faint shading if Sage's renders pass (no extra server data) [I]. Decide section 5 alongside it.

## 2. How to build it, and what the map library does

**The map library, pinned 13.5.0** (`gradle/libs.versions.toml:43`; read at its android-v13.5.0 tag, efbde81) [R]:
- A height-tile source and hillshade layer exist on Android: `HillshadeLayer.java`, with five shading methods in `Property.java:651-678`, exaggeration, shadow, highlight and direction [R]. Height tiles come in both encodings, Terrarium among them (`tileset.hpp:21-24`) [R]. P1 confirmed.
- **No contour generation:** the only paths matching "contour" in the whole tree are four test images [R]. So contours must be vector tiles built ahead.
- Offline downloads handle height-tile sources (`offline_download.cpp:197-199`) [R].
- No version change is needed for any of this [I]. Newer 13.5.x tags exist; not assessed.

| Layer | Option | Size (US + Canada) | Verdict |
|---|---|---|---|
| Relief | **Height tiles, phone shades them** | below | Recommended; ruled. Night needs only a colour pair. |
| Relief | Pre-shaded images | not sized; much larger at any zoom past 12, and needs a night set [I] | Rejected |
| Contours | **Vector tiles, own archive** | below | Recommended; ruled. Reuses the mapping server's generic archive path [R]. |
| Contours | Inside the weekly map | | Rejected: heights rarely change, and the weekly build has no room (section 6). |

**A format choice worth more than any other** [C, I]: 256 px PNG to z12 and 512 px lossless WebP to z11 carry the same pixels. The published planet terrain archive in that WebP form averages 38 KB a tile [R]. The WebP form is about a quarter the size (table in section 4). It's unproven: whether the phone decodes WebP height tiles exactly is a pilot check. The library's Android decoder is the platform's, so it probably works [I]; PNG is the fallback.

**Tools on the Pi:** the raster toolkit is installed, 3.6.2 [A] (a premise from 10-22 now out of date; see Premises). The Pi's vector tiler, 0.10.2 (`pins.env:180`), already reads GeoPackage sources: the build's landcover file is one (`pins.env:223`) [R]. Whether it can tile contours needs a small profile written [I]; the alternative tiler isn't in the Pi's package source (10-22 sizing) [R]. The Terrarium encoder is a short script to write [I].

## 3. Where the heights come from

**Route squares aren't a height grid.** They hold heights only at path points, inside BRouter files. They can't feed contours or shading.

What the Pi does hold, separately [A; G7 record agrees, read]:

| Store | Resolution | Coverage | Size |
|---|---|---|---|
| Grid on the USB drive (`/mnt/storage/elevation/hgt3/`) | 3″ (about 65-90 m) | US, Canada, PR, USVI | 9.8 GB |
| US copies (`hgt1-3dep/`) | 1″ (about 30 m) | 295 US cells only | 7.65 GB |
| BRouter input | 3″ | same as the grid | 1.8 GB |

The weekly build neither writes nor needs the two grids [A].

- **3″:** shading to about z11, contours of 100-200 ft to about z11-12; not the 40 ft at z13-14 as ruled, because gullies smooth away and lines shift [A, inferred by Aspen]. This matches P2.
- **1″ needs a pull:** 1,145 US cells, **48.4 GB** [C, from the sizes in Aspen's own G7 log], plus Canada's MRDEM-30, one 83.85 GB file read in windows [R, G7]. Aspen's "55-65 GB" for the US is the whole set; I compute 62.6 GB, which agrees.
- **Time:** Aspen quotes 2-2.5 days from the 10-22 sizing [A]. The later G7 pilot measured about 10 GB/h on 3DEP [R], which gives about **13 hours** if MRDEM reads as fast [C]. MRDEM's rate at full resolution isn't measured, so plan on 1-2 days. G7 itself did all 4,403 cells in about 8.3 hours, at 3″ [R].
- **1/3″ (10 m)?** 1,449 tiles, 494 GB [C from listing]. Nearly four times the 1″ set, and US-only in effect; no ruling asks for it. Not recommended now.
- **Alaska's 3DEP 1″ is mostly 60 m resampled** (elevation sizing, 2026-10-21) [R], so contours there will be softer.

## 4. Sizes

Tile counts, exact for the footprint of 3,631 height cells [C]: z0-10 88,447; z0-11 348,565; z0-12 1,384,781; z13 4.13 M; z14 16.5 M. The 10-22 sizing and Aspen's table use "about 0.9 M" for z0-12, about a third low.

| Archive | Low | High | Note |
|---|---|---|---|
| Relief, 256 px PNG, z0-12 | 55 GB | 125 GB | [C] 40-90 KB a tile. Hilly real tiles in the repo average 116 KB. |
| **Relief, 512 px WebP, z0-11** | **13 GB** | **26 GB** | [C, I] same detail |
| Relief to z11 from the 3″ grid (PNG) | 14 GB | 31 GB | [C]. Aspen: 10-25 GB [A] |
| **Contours 40 ft, index from z13, all at z14** | **4 GB** | **71 GB** | [C] middle case 17 GB [I] |
| Contours 20 m, same zooms | 2.5 GB | 43 GB | [C] |

**The contour range is wide because the model is weak.** The length formula is exact, but the slope behind it rests on four tiles in one part of Oregon, scaled by each cell's height range. The high case stacks every pessimism. Sage's 3-10 GB and Aspen's quote of it may hold; only the pilot settles this.

**Likely total:** about 30-40 GB (WebP plus middle contours) [I]; full range 17-196 GB [C].

**One offline area,** steep ground at 45°N, using the library's own zoom rules (`tile_cover.cpp:149-156`, `offline_download.cpp:38-50`) [C]. Note a 256 px height source stores z11-12, not z10-12.

| Area radius | Relief PNG / WebP | Contours | Added, worst case |
|---|---|---|---|
| 8 km (miles default) | 1.5 / 0.4 MB, 13 / 5 tiles | 0.2-2.2 MB, 130 tiles | about 4 MB |
| 10 km (km default) | 2.9 / 1.0 MB | 0.3-3.4 MB | about 6 MB |
| 24 km (maximum) | 10.3 / 2.6 MB | 1.6-19.4 MB | about 30 MB |

The 6,000-tile budget is unaffected: the app counts the basemap estimate only (`OfflineTileBudget.kt`) [R]. The real download does grow by these tiles, so the shown count and progress will disagree a little, as they already do for the trails band [I].

## 5. Coverage

**Inside:** US, Canada, Puerto Rico and USVI [R]. Height ends at the US border with Mexico, since 3DEP is clipped to US polygons (`cell.sh`) [R]: shading and contours would stop in a straight-ish line there [I]. At the Canada border lines may jog by a few metres, because the two datums differ (elevation sizing) [R; visual effect I].

**Outside, today:** Topographical is worldwide. Trail Map outside US + Canada is grey or fill only; a world fallback for it is in progress and not deployed (`2026-10-31-global-coverage.md`) [R]. The Pi holds no heights there; a global source is being found by Willow [A].

**Under option A, a user in Brazil or Qatar on Topo would lose it.** The choices:
1. **Outside coverage, the Topo chip keeps OpenTopoMap.** Cheapest in data, and needs app logic to pick by location, with a visible seam at the edge. Keeps the outside-server risk for those users [I].
2. **A global height source** via the coming fallback. A public planet archive at 30 m+ is 355.6 GB [R, header only], about $5.30 a month [C]. Its licence is "various open-data sources": not reviewed, and a composite. A global 3″ source from Willow's work may be smaller; unknown.
3. **Flat Trail Map with a one-line note** saying topo isn't available here yet. No cost, honest, and a real loss.

I recommend 1 for this release, and 2 with the global build. That's a judgment for the owner.

## 6. Pi disk, time and upload

- **Free** [A, read 10-31 21:40 PDT, during a run]: USB 158 GB, card 40 GB, NVMe 48 GB (162.7 GB before the run).
- **The weekly build needs about 157 GB of the NVMe,** so all topo work goes on the USB drive and never overlaps a run; one vector tiler at a time, leaving 2 GB free [A].
- **Window:** Monday to Saturday; the run is about 15 h from Sunday 02:20 PST [A].
- **Clash:** Willow's global build pilot also wants that USB space after this weekend. Aspen will schedule them apart once the owner chooses [A]. This is the one real dependency.
- **Disk fit** [I]: output 30-40 GB likely, plus unmeasured contour working files ("possibly tens of GB" [A]). Likely fits in 158 GB; the 196 GB high case doesn't. The pilot answers this.
- **Build time, unmeasured:** relief 6-24 h on 3 cores, contours 1-2 days [A, from 10-22 sizing]. With the read, about **3-5 days of Pi time** for the first build [I].
- **Upload:** at the last measured 2.9 MiB/s, 40 GB is about 4 hours; 125 GB about 11 hours [C]. The 16 MiB parts allow about 156 GB per file under the 10,000-part limit [C, R], fine for both archives. Rclone can't resume, so use the pi-29 resume method [R].
- **Storage:** $0.015 per GB-month, so $0.60 a month at 40 GB, $0.25-3 across the range [C, R]. Downloads cost nothing [R]. Read counts rise; not computed.
- **Keeping it current:** heights change rarely, so a one-off, not weekly [A].

## 7. Offline, night and zooms

**Zooms, as ruled:** hillshade from z9, index contours and labels from z13, all contours from z14 [R]. Contour tiles stop at z14 and enlarge above; Trail Map's max is 18, Topographical's 17 today [R].

**Offline:**
- Today Topo has no offline form. With own topo it can. That needs a ruling, because the rule is "Offline downloads → Trail Map only" (-1554) [R].
- The store keys everything by style URL (`OfflineStyle.kt:3-9`) [R]. **One style that carries the topo layers, hidden on Trail Map, keeps one download for both maps** [I]. A second style would mean two downloads or a change to the area model.
- Older areas lack the new tiles. They'd show Trail Map without relief, with the existing "Older map style" notice (`StreetStyle.kt`) [R].
- A style with labels now downloads fine offline (RECORD, Cannon Beach packs, line 11435) [R], so the old glyph crash is no longer the risk P5 named. Contour labels are unproven on the phone, though.
- PR #448 (Street v4) is open [R]; its style must land first, or topo's style goes on top of it.

**Night:**
- Contour lines are line layers, so the night recolour reaches them already (`OfflineNightRecolourApply.kt:76`) [R, walking background, fill and line]. Brown's night colour is Sage's call.
- The hillshade layer isn't walked, so it needs its own day and night pair, as ruled in 10-22 ruling 7 [R, I]. That's new app code.
- The zoom 9.5 crossfade exists only because OpenTopoMap's low-zoom tiles invert badly. Own Topo wouldn't need it [I].
- **Hillshade rendering on the S22 has never been seen**; Sage's previews were web renders [R]. This is the largest unknown.

## 8. Credits and licences

Licence wording stays as filed in `docs/research/2026-10-30-nrcan-attribution.md` (branch juniper-nrcan-credit), read at source then and not re-read now.

| Source | Status | Required |
|---|---|---|
| USGS 3DEP | Public domain | Courtesy credit asked, not required [R] |
| NRCan MRDEM | OGL-Canada 2.0 | "Contains information licensed under the Open Government Licence – Canada." plus link [R] |
| Copernicus WorldDEM-30 (inside MRDEM) | Own licence | Notice and liability sentence, already in the app (`DataSourceCredits.kt`) [R] |
| OpenStreetMap, Protomaps | ODbL, BSD | Unchanged |
| OpenTopoMap | CC-BY-SA | Credit goes only if it stops being shown anywhere [R, I] |
| Build tools | MIT-style raster toolkit, Apache-2.0 tiler | They run on the Pi and don't ship, so no app notice [I; toolkit licence read at heading only] |

**App changes:** the Heights rows say "Route heights in Forager are averaged from this data". For topo they need wording that covers shading and contours, and the map caption needs a height credit whenever relief is on. Topo's caption today names OpenTopoMap and SRTM [R]. All of this is inferred, not legal advice. Any notice beside the archives should follow the weekly `NOTICE` route Aspen set up (`2026-10-30-09`) [R]. Any global source needs its own review.

## 9. Build plan

| # | Step | Who | Time | Holds? |
|---|---|---|---|---|
| 0 | Owner rules on section 10; Sage's proposal | Owner, Sage | days | Gate |
| 1 | **Pilot:** one mountain and one plains cell through the whole chain; real sizes, working files, times | Pi (Willow) | 1-2 days | **Gate** |
| 2 | **Phone check:** pilot tiles on the S22 and S26: shading, contour labels, WebP, an offline download | App (Hazel) | 2-3 days | **Gate** |
| 3 | Rule on PNG or WebP, interval, final scope | Owner | | Gate |
| 4 | Pull 1″ and build both archives, on the USB drive, between runs | Pi | 1-2 weeks of calendar | Long but low-risk |
| 5 | Upload; Worker route or generic path check, deploy | Pi, owner's go | 1-2 days | |
| 6 | Style with relief and contours, on top of Street v4 | Sage, Hazel | 3-5 days | Waits for #448 |
| 7 | App: Topo chip draws it, hillshade night pair, offline, credits, coverage fallback; remove OpenTopoMap paths as ruled | Hazel | 1-2 weeks | Sits in big map files; modular-code rules apply |
| 8 | Sorrel, Elder, S22 walk | Sorrel, Elder | 3-5 days | |

**Honest range: 3-6 weeks** [I], with steps 1-2 in the first week. P5's 2-5 weeks was optimistic on the Pi windows and the review steps.

**What would hold the release:** steps 1-2 failing or surprising, USB space lost to the global pilot, or Wi-Fi trouble on the Pi (it failed on 10-31) [R]. **Smaller fallback** that can ship in about half the time: relief only, from the 3″ grid, to z10-11. No new pull, about 3-14 GB (WebP z0-10 to PNG z0-11), no contours [C, I]. It would be a shaded Trail Map rather than a topo map, so it can't replace Topographical.

## 10. Questions for the owner or Sage

**Owner:**
1. Does topo hold the release, or ship when ready? Recommended: decide after steps 1-2.
2. Confirm option A (the 10-22 ruling), given the global ask since then?
3. Outside coverage: keep OpenTopoMap there, source global heights, or a note?
4. Does offline include Topo, changing -1554? Recommended yes, one style.
5. Go for the pilot, then for the pull. Each needs its own go, and heights downloads have needed the owner's word [R].
6. Canada at 1″ too? The Pi has none. The 10-27 ruling "No Canada 1″" was about keep-copies; the topo rulings ask for MRDEM [R].
7. Who writes to OpenTopoMap (-1291), if it's still needed? Status: could not determine.

**Owner and Sage:**
8. 40 ft for everyone, or 20 m for metric users and Canada? Lines at 40 ft labelled in metres read 12, 24, 37 and a second archive costs up to 60% more [C, I].
9. Faint relief on Trail Map: strength, and on by default?
10. Contours' night colour, labels and z13 density; the relief night pair on real phones.
11. How the map ends at the Mexican border.

---

## Confirmed vs inferred

**Confirmed (read or computed):** the library's hillshade, Terrarium, offline handling and lack of contours; tile counts (rerun matched the first run exactly); offline zoom rules; 3DEP listing totals; US re-pull bytes; storage prices and limits.
**Inferred:** every contour size, relief bytes for plains and tundra, Pi build times, WebP on the phone, the one-style approach, the calendar range.
**Aspen's, unverified by me:** current disk, what's on the USB drive, tool install, 3″ limits, and the Pi window.

**Prediction check:** P1 held. P2 held. P3: relief 15-90 GB held only for WebP; PNG is 55-125 GB. Contours 5-40 GB: model is wider. P4 held (24 km is about 30 MB at worst). P5: range revised to 3-6 weeks, and the glyph risk fell away. P6 held. The 10-21 notes' P2 (1/3″ at 300-600 GB, 1″ about 3,780 tiles): 494 GB held; 3,811 tiles.

## Could not determine

- Real contour size, working-file size, and Pi times, until the pilot.
- How hillshade and contour labels look or perform on a phone.
- How many users use Topographical.
- MRDEM's full-resolution read rate.
- Whether plains tiles compress as hoped: no plains tile was available.
- Global height source, and the planet archive's licences.
- Object-storage read growth.
- Whether the OpenTopoMap letter was sent.

## Premises that were wrong

- **"Only the route squares hold heights" (the dispatch's caution):** a separate 3″ grid is kept, so a low-detail build needs no pull [A].
- **10-22 sizing:** "GDAL not installed" is out of date; "0.9 M tiles" is low (1.38 M) [C]; "129 GB free" is superseded; 2-2.5 days' read predates the measured rate.
- **Aspen's "3,632 .hgt":** G7 records 3,631; the extra is probably the checksum list [I]. The sizes agree once GB and GiB are told apart.
- **"Pi disk is the blocker" (10-22):** the USB drive has since changed that, though it now has a rival claim.
- **Corrections to my own evidence:**
  - The 1″ listing held 3,812 rows, one a stray 1/3″ file. Before 130.28 GB, after 130.11 GB across 3,811 tiles.
  - `sizes.py` first printed contours near zero: a unit error, fixed (commit history shows both).
  - The G7 log has 1,440 3DEP lines and 4,402 cells in all, one short of the record's 1,441 and 4,403; so 1,145 cells here against Aspen's 1,146. Cause unknown, effect under 0.1%.
- **Stopped run's evidence:** kept. All of it came from allowed reads: S3 key listings by the method, and a copy of Aspen's public log (hash matches Forager-cartography). `tiles.out` stopped before z15 and the area lines; I reran without z15, which would need about 66 M entries in memory.

## Decided beyond scope

- Read Forager-cartography (public, not named in the dispatch), as the stopped run did.
- Read the first 127 bytes and headers of one public terrain archive, for a size check. No tiles.
- Added the WebP form and the relief-only fallback, which no dispatch asked about.
- Recommendations in 1, 5, and 9 are mine, for the owner to rule.
- Evidence file `published-sizes.txt` names services in its URLs, as citations. The report describes them generically.
