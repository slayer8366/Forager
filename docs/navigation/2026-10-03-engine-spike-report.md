# Valhalla and BRouter routing along trails on the S22 (dispatch 2026-09-28-446, spike, measurement only)

**Status:** measured. The spike's code is pushed on `engine-spike` and not merged. Nothing touched the Forager app or its data. The spike app was uninstalled from the S22 afterwards.

**Date:** 2026-10-04 (UTC).
**Dispatch:** `prompts/preserved/2026-10-03-07.md`, with Amendment 1 (a street route; which path the walker is on) and Amendment 2 (packing per offline region).
**Base:** `origin/main` at `7c072bfc`, checked against the remote.
**The owner's words, in this window:**
- "Yes, start -446", then "-470 first (Recommended)";
- "Yes, use the phone" for the spike runs, and "Aeroplane mode on".

The planner relayed the owner's "Coder can use the USB drive as needed".

Large files are on the owner's USB drive under `engine-spike/`. Measurements, scripts and every position are in `~/Zynergy/device-evidence/2026-10-04-engine-spike/`. **No position or place name of the owner's is in this repository.** Trails are named here; the town route is not.

## In plain terms

1. **Both engines work on the phone with no signal,** and both are fast enough: about half a second to three quarters of a second per 10 km trail route, from a cold app start.
2. **BRouter kept to the trails better.** Asked for about 10 km along the McKenzie River Trail, BRouter's hiking profile stayed on the trail the whole way. Valhalla's walking profile took a road for half of it, because the road was about 1 km shorter.
3. **BRouter is lighter.**
   - Its data for Oregon and Washington together is 155 MB, against about 350 MB for Oregon alone with Valhalla.
   - It adds about 0.3 MB to the app, against about 9 MB per phone type for Valhalla.
   - It uses about a third of Valhalla's memory.
4. **Valhalla can tell which street or trail the walker is on; BRouter cannot.** Valhalla includes a map matcher, and it runs on the phone. That is the base of the wrong-turn alert the owner asked for. BRouter has none.
5. **Both can be cut to a walker-drawn region** and built from the same map download. A 60 × 60 km square of national forest came to 4.0 MB for Valhalla and 0.6 MB for BRouter.
6. **Neither engine draws the walk from an off-trail start to the trail.** Both start the route on the trail, 207 m away, and leave the gap to the app.

## Verified before building (reported by message first; the planner accepted it)

- **Versions, read on 2026-10-04:**
  - Valhalla 3.9.0 is current. valhalla-mobile 0.6.3 (MIT, Maven Central) bundles Valhalla 3.6.3, so its tiles were built with pyvalhalla 3.6.3.
  - BRouter v1.7.10 (MIT, commit `4d2639af`) is not on Maven Central, and JitPack's build of it fails.
- **Data:**
  - Geofabrik `oregon-261002.osm.pbf`, 254,159,390 bytes, md5 verified.
  - BRouter's prebuilt `segments4` tiles W125_N45, W125_N40, W120_N45 and W120_N40, 154,567,417 bytes together. No checksum is published, so they were verified by size and by routing on them.

## Routes

All endpoints are in the evidence folder only. Each trail route follows the trail's own mapped line for about 10 km, picked by `tools/make_routes.py`.

| Route | What it is |
|---|---|
| r1 | McKenzie River Trail #3507 (Willamette NF), 10.0 km along the trail, 7.7 km straight |
| r2 | Salmon River Trail #742 (Mount Hood NF, near Zigzag), 10.0 km along, 7.0 km straight |
| r3 | Green Lakes Trail #17 (Deschutes NF), 10.0 km along, 8.0 km straight, `sac_scale=hiking` |
| r4 | r2 started 208 m from the nearest mapped way (any walkable way, measured to its line), in forest |
| r5 | Streets in the town of the 2026-10-03 walk: from the walk's start, 2.56 km on in the walk's direction |

## Item 1: data on the phone

| | Valhalla 3.6.3 | BRouter 1.7.10 |
|---|---|---|
| Oregon | 346.8 MB (588 tiles; built here in 10 min 51 s, 4.6 GB peak memory, 0 errors) | 154.6 MB: Oregon touches all four 5° tiles |
| Oregon + Washington | about 840 MB, **inferred** by scaling Oregon's tiles-to-extract ratio (1.36) to Washington's 364 MB extract; not built | 154.6 MB, the same four tiles (they also cover parts of CA, NV and ID) |

## Item 2: what each engine adds to an APK

| ABI | Valhalla native library (stored uncompressed) | BRouter |
|---|---|---|
| arm64-v8a | 8.6 MB | 0.31 MB of classes (one jar, pure Java), the same on every ABI |
| armeabi-v7a | 7.1 MB | |
| x86_64 | 9.0 MB | |
| x86 | 9.3 MB | |

- **Valhalla also brings JVM libraries:** about 4.2 MB unshrunk, of which 3.2 MB is `kotlin-reflect` (pulled in by Moshi's Kotlin adapter). Forager does not shrink today, so that would ship in full.
- **BRouter needs its profile files** next to the data: about 64 KB for the hiking profile, `lookups.dat` and one more.

## Items 3 and 7: route time and memory on the S22

**How it was run:**
- In aeroplane mode, confirmed on the phone: `airplane_mode_on=1`, no active network.
- Each run is a fresh process (`am force-stop`, then the Activity started), with five runs per engine and route.
- **Time:** from building the engine to having the route. Valhalla's includes opening its tiles (`ms_engine`, about 0.55 s); BRouter has no separate start.
- **Memory:** the process's peak resident memory (VmHWM). The same app routing nothing peaks at 106 to 109 MB (5 runs), so the engine's own share is roughly the figure minus about 108 MB.

| Route | Engine | Length | Time, median (range) | Of which, routing | Peak memory, median |
|---|---|---|---|---|---|
| r1 McKenzie | Valhalla | 9,072 m | 670 ms (629–698) | 127 ms | 196 MB |
| | BRouter | 10,034 m | 605 ms (591–641) | 605 ms | 136 MB |
| r2 Salmon | Valhalla | 10,024 m | 683 ms (646–746) | 141 ms | 193 MB |
| | BRouter | 10,029 m | 525 ms (492–583) | 525 ms | 130 MB |
| r3 Green Lakes | Valhalla | 10,051 m | 698 ms (670–749) | 138 ms | 185 MB |
| | BRouter | 10,019 m | 585 ms (572–617) | 585 ms | 134 MB |
| r4 off-trail start | Valhalla | 9,934 m | 715 ms (701–794) | 124 ms | 193 MB |
| | BRouter | 9,940 m | 579 ms (524–657) | 579 ms | 131 MB |
| r5 town streets | Valhalla | 3,481 m | 802 ms (751–837) | 168 ms | 226 MB |
| | BRouter | 3,482 m | 1,142 ms (1,026–1,172) | 1,142 ms | 138 MB |

- **Consistency:** 50 runs, 0 errors, and every run of a route returned the same length.
- **Cache:** the first run of each route was not slower than the rest. The phone's file cache was likely warm throughout, though, partly from two smoke runs. A cold read after a reboot was not measured.
- **Engine reuse:** Valhalla's 0.55 s start is paid once per engine instance. Its README says to reuse one instance, so a second route in the same session would take about 0.13 to 0.17 s.

## Item 4: starting off the trail (r4)

| | Valhalla | BRouter |
|---|---|---|
| Snaps to the trail | yes, to the Salmon River Trail | yes, to the same trail |
| How far | the route's first point is 207 m from the requested start | 207 m |
| Returns the connecting segment | no: the route begins on the trail | no, with the default settings |

- The research said BRouter "can return the connection as a straight segment". That was not seen with its defaults, and no option for it was tried.
- So in both cases the app has to draw the way from the walker to the trail itself. For a forager, that fits the owner's ruling to guide back along the walker's own track.

## Item 5: hiking awareness

Each engine's route (run 1) was matched to OSM on the laptop with Valhalla's matcher, so both are judged on one footing (`tools/analyse_routes.py`).

| Route | Valhalla | BRouter |
|---|---|---|
| r1 McKenzie | 50% road, 48% path; **48% on the named trail** (left it for a road about 0.95 km shorter) | 100% path, **100% on the named trail** |
| r2 Salmon | 100% on the trail | 100% on the trail |
| r3 Green Lakes | 100% path, 87% on the named trail; 8.8 km `hiking`, 1.3 km untagged | 100% on the named trail, all `hiking` |

- **Why they differ, inferred:**
  - Valhalla's `pedestrian` costing, with `max_hiking_difficulty` 6, mostly minimises distance.
  - BRouter's `hiking-mountain` profile weights paths over roads.
  - Valhalla has costing options that might keep it on trails (`use_roads`, `walkway_factor` and others). None was tried, so this is a default against a default.
- **Not tested:**
  - Every route here is `sac_scale` hiking or untagged. No route met harder grades.
  - No informal trails (`informal=yes`) were on these routes, so how each engine treats them was not observed.

## Item 7: the street route (r5)

- **Ways used:**
  - Valhalla: 96% road, 4% service road.
  - BRouter: 93% road, 4% footway, 4% service road.
  - The two are the same length to a metre (3,481 and 3,482 m).
- **How sidewalks are mapped near it** (`tools/sidewalks.py`, all ways within 60 m of the route):
  - **No sidewalk is mapped as a separate line.**
  - Sidewalks appear only as tags on roads: 1.23 km `sidewalk=both`, 0.34 km `left`, 4.36 km `sidewalk=no`, and 2.95 km untagged.
  - So what each engine does with separately mapped sidewalks **could not be tested here**.
  - Valhalla's matcher reported no sidewalk attribute on the edges the two routes used, and **why could not be determined**: the routes may avoid the tagged roads, or the attribute may not be filled.
- **Time:** BRouter is slower in town (1.1 s against 0.8 s). The likely cause is a denser street grid to search; inferred.

## Item 8: which path the walker is on

**Valhalla** has a map matcher (Meili), and valhalla-mobile 0.6.3 exposes it on the phone (`trace_route`, `trace_attributes`). **The research's "batch only" was wrong;** see the premises below.

It was run on the laptop through pyvalhalla 3.6.3, the same Valhalla version as the phone library. Input: the 2026-10-03 walk's 194 stored readings (`tools/match_walk.py`).
- **Matching:** 92 readings matched, 94 interpolated between matches, 8 unmatched. The distance from a reading to its matched line was median 8.3 m, 90th percentile 16.0 m, maximum 28.0 m.
- **By street:** 9 different streets, in 19 stretches. Most stretches are 5 to 21 readings long, with one single-reading blip.
- **By OSM way:** 21 stretches. OSM splits one street into several ways, so a change of way is often no turn at all. **A wrong-turn rule would have to compare streets or trails, not ways.**
- **Ways matched:** all roads. None was a footway.
- **How quickly it notices a move onto another way: could not be determined.** Readings are about 5 s apart, and nothing records which way the walker actually took, so the matches cannot be checked against the truth. The owner's memory of the route, or a walk recorded for this purpose, would be needed.
- It was not run on the phone. The library exposes it, so it is cheap to add to a later spike.

**BRouter** has no matcher. Wrong-turn alerts with BRouter would need either the app's own matcher or the route-distance rule the research describes:
- **The app's own matcher** would match to the planned route's line and to the trail data from somewhere else (the offline map tiles, or a trails file). BRouter's own data format is not readable outside its engine without work. Inferred.
- **The route-distance rule:** distance to the active route, with a hold time. It needs no matcher, but it cannot name which other trail the walker took.

## Item 6: integration and licences

**Valhalla (valhalla-mobile):**
- One Maven dependency: a JNI library plus Kotlin models.
- One engine instance opens a tile directory or archive and is reused; "Building it is expensive" (its README). Here that was 0.55 s and about 85 MB.
- Raw-JSON entry points exist for every action.
- Its tiles need a matching Valhalla version (3.6.x for 0.6.3), so a library upgrade means rebuilding the data.
- **Licences of what ships** (each read from the project's licence file or GitHub's licence field on 2026-10-04):
  - valhalla-mobile and Valhalla: MIT.
  - protobuf: BSD-3-style.
  - Boost: BSL-1.0.
  - RapidJSON: MIT. Its JSON-licence files, in `bin/jsonchecker`, are not part of a build.
  - lz4: the library is BSD-2.
  - abseil: Apache-2.0.
  - robin-hood-hashing and unordered_dense: MIT.
  - Moshi and kotlin-reflect: Apache-2.0.
  - osrm-openapi (Stadia Maps): BSD-3.
  - **No copyleft.** The APK carries no notices for them, so the app's licences screen would have to.

**BRouter in-process:**
- **It works.** Its five pure-Java modules (`brouter-util`, `-codec`, `-expressions`, `-mapaccess`, `-core`) compile from the v1.7.10 source as one plain Java library with no dependencies beyond the JDK (`brouter-lib/`).
- It is called as BRouter's own Android app calls it: a `RoutingContext` with the profile path, then `RoutingEngine(…).doRun(0)` and `getFoundTrack()`.
- **What "undocumented" means in practice:** there is no published artifact, so the app would vendor or build the source, and track BRouter's releases by hand.
- MIT, pure Java, no native code.

**Reading files on the phone.** Files adb pushes into an app's `Android/data` directory are owned by the shell, and the app could not open them (`EACCES` on the S22). The data was copied into the app's internal storage with `run-as`. A real app would download into its own storage, so this concerns the test setup only.

## Item 9: packing per offline region

The same 60 × 60 km square, centred on r2 in the Mount Hood forest, was used throughout.

| | Valhalla | BRouter |
|---|---|---|
| Whole tiles covering the square, cut from the state build | 42.7 MB: one 4° tile (10.3 MB), four 1° tiles (17.5 MB) and sixteen 0.25° tiles (14.9 MB) | 154.6 MB: the state's 5° tiles |
| Built from the square only | **4.0 MB** (24 tiles), built in 2.3 s | **0.64 MB**: two `.rd5` files, as the square crosses 45° N; built in about 2 min |
| Routes on it | r2 10.02 km and r4 9.93 km, identical to the state build | r2 10,029 m and r4 9,940 m, identical to the state build |

- **An arbitrary region:** both engines build from any extract. A walker-drawn region is cut from the OpenStreetMap file first (`tools/clip_region.py`, pyosmium), keeping every way with a point inside, whole. Both engines then build from that one file.
- **One extract, one pipeline:** yes. Both were built here from the same clipped file.
- **Clipping cost:** a scan of the whole state took 189 s and 2.2 GB of memory. On a server, a clip from a planet or country file is routine.
- **BRouter's elevation:**
  - Its `.rd5` carries elevation only when SRTM files are supplied. BRouter's own docs say the usual download link is dead (2026).
  - The region build here has none ("filtered ascend = 0"), so a profile's climbing costs do nothing on it.
  - BRouter's prebuilt state tiles do carry elevation.
- **Shipping beside the map tiles:** a region's routing data would be a few MB for either engine, small next to its map tiles. Both engines read directories of files, so the routing data can sit in the region's folder. Inferred; packaging was not built.

## Not done, and why

- **GraphHopper:** not in the dispatch.
- **Tuning Valhalla's costing** to keep to trails: not tried. A setting might close the gap with BRouter on r1, and that is worth trying before choosing.
- **Matching on the phone:** not run, only on the laptop.
- **Cold reads after a reboot:** not measured.
- **Washington's Valhalla data:** not built; the figure above is a scaled estimate.

## Disclosure

**Confirmed vs inferred.**
- Measured on the S22 (all in aeroplane mode, 50 runs): route times, peak memory, route lengths.
- Measured from the files: data sizes, APK contents, region sizes.
- Measured on the laptop with the same Valhalla version: the route matching and sidewalk counts.
- Inferred:
  - Oregon plus Washington for Valhalla.
  - The causes of the r1 difference and of BRouter's slower town route.
  - What a BRouter-based wrong-turn alert would need.
  - Packaging beside the map tiles.

**Could not determine.**
- How quickly a matcher notices a move onto another way (no record of the true path).
- What either engine does with separately mapped sidewalks (none near the route).
- Why Valhalla reported no sidewalk attribute.
- How informal trails and harder `sac_scale` grades are treated (none on these routes).
- Cold-read times.

**Premises that were wrong.**
1. **The research said** Valhalla's matcher (Meili) was "batch only". valhalla-mobile 0.6.3 exposes `trace_route` and `trace_attributes` on the device.
2. **The research said** BRouter "can return the connection as a straight segment" from an off-trail start. With its defaults it did not.
3. **The research guessed** Valhalla's data for Oregon plus Washington at 300 to 600 MB. Oregon alone is 347 MB.
4. **The research and the dispatch** called BRouter in-process "undocumented". It works, as above; what is missing is a published artifact.
5. **valhalla-mobile's README** shows 0.6.1 in its setup text; 0.6.3 was used.
6. **GitHub reports** Valhalla's licence as NOASSERTION. It is MIT, from `COPYING`.

**Decided beyond scope.**
1. **The spike lives under `spikes/engine-spike/`,** a separate Gradle project with its own `applicationId`, built with the repository's wrapper but not part of Forager's build.
2. **BRouter was compiled from source** as one plain Java module, rather than as an included build. Its own build pulls in a second Android Gradle plugin.
3. **The town route is the walk's direction extended to 2.56 km,** since the walk itself went only 0.27 km from its start.
4. **Gradle's home for BRouter's own build was on the USB drive,** so its plugin and Gradle version did not land on the laptop.
5. **The spike app and its data were uninstalled** from the S22 afterwards. A `pinch.sh` left in `/data/local/tmp` from earlier work was not touched.

## Addendum, 2026-10-04: Valhalla tuned toward trails (Amendment 3)

**Asked:** the owner chose "One more Valhalla run first" (`RECORD.md` -478). The task: re-run the routes with Valhalla's pedestrian costing tuned toward trails, using 3.6.3's documented options, and compare the share on the named trail with the defaults and with BRouter.

**How:**
- On the laptop with pyvalhalla 3.6.3, the same Valhalla version valhalla-mobile 0.6.3 bundles, on the same Oregon tiles. No new download.
- Each route's line is matched to OSM ways exactly as for item 5 (`tools/tune_valhalla.py`; output in the evidence folder, `tune-valhalla.txt`).

**What the options can do, read from 3.6.3's documentation and source:**
- The documented pedestrian options are: `walking_speed`, `walkway_factor`, `sidewalk_factor`, `alley_factor`, `driveway_factor`, `step_penalty`, `elevator_penalty`, `use_ferry`, `use_living_streets`, `use_tracks`, `use_hills`, `use_lit`, `service_penalty`, `service_factor`, `destination_only_penalty`, `max_hiking_difficulty`, `shortest`, `max_distance`, two transit distances, `type` and `mode_factor`.
- **There is no `use_roads` for walking**; that option is cycling's.
- In the costing (`src/sif/pedestriancost.cc`, 3.6.3, around line 751), `walkway_factor` multiplies only edges whose use is footway or sidewalk.
- OSM `highway=path`, which is how all three trails are mapped (their edges match as use "path"), gets no factor. Neither does an ordinary road without sidewalk tags.
- So **no documented option makes a road cost more than a trail**. The levers reach footways, sidewalks, tracks, service roads, living streets and alleys only.
- Valhalla 3.9.0's pedestrian costing source has no road option either; a search for `use_roads` and `road_factor` found none. It is newer than what valhalla-mobile bundles in any case.

**Settings tried,** each with `max_hiking_difficulty` 6 as in the spike:
- **A:** `walkway_factor` 0.5, `use_tracks` 1.0.
- **B:** A, plus `service_factor` 3, `sidewalk_factor` 3, `use_living_streets` 0, `alley_factor` 5.
- **C:** B with `walkway_factor` 0.1, the lowest the code allows.

| Route | Valhalla defaults | A | B | C | BRouter (hiking-mountain, defaults) |
|---|---|---|---|---|---|
| r1 McKenzie: on the named trail | 48% (9.07 km; 50% road) | 48% | **51%** (9.24 km; 47% road) | 51% | **100%** (10.03 km) |
| r2 Salmon | 100% | 100% | 100% | 100% | 100% |
| r3 Green Lakes | 87% (all path) | 87% | 87% | 87% | 100% |
| r4 off-trail start | 100% | 100% | 100% | 100% | 100% |
| r5 town: by use | 96% road, 4% service | 87% road, **8% footway** | 87% road, 8% footway | the same | 93% road, 4% footway |

- **The finding:** tuning moves r1 by 3 points, from 48% to 51%. Valhalla still takes the road for 47% of it, because the road is shorter and no option prices a road above a path. On r3 the 13% off the named trail is on other paths, which the options cannot tell apart either.
- **What does respond:** the walkway factor, in town. It moves 8% of r5 onto footways.
- **Times:** 0 to 3 ms per route on the laptop with a warm engine, the same for every setting. These are not comparable with the phone figures above, which include the engine's start; the settings did not change them measurably.

**What this leaves for the choice, inferred:**
- Trail preference with Valhalla would need a change beyond its documented options: a custom costing or patched factors in a fork of valhalla-mobile, or edge data marked at tile build time.
- BRouter's profiles are plain text files that the app ships, so trail preference there is a profile edit.

**Disclosure for this addendum:**
- Confirmed: the shares and lengths above, the documented option list, and the factor code in 3.6.3.
- Inferred: what a fork or a custom costing would take; it was not tried.
- Premise that was wrong: the amendment named `use_roads` as a possible option; 3.6.3 has none for walking.
