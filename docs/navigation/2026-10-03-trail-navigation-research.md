# Navigating along trails, and back to them: trail data, offline route engines, and leaving the trail (research)

**Status:** research, read-only. Nothing built or decided. Filed by the planner on 2026-10-03 (UTC), at `main` `bb0b559e`.

**Asked for by the owner,** verbatim (2026-10-03, `RECORD.md` -442 and -443):
- "The map has trails and such, so when that data is available, I think we should take advantage of them and navigate against them rather than open space".
- "Run some research agents to see if we can get a comprehensive trail dataset so that we can use them for navigation. When a user moves off trail, which they often would do to score a find, then default to the user's own trail marks, and have a way to guide the user safely back to the trail to resume navigating."

**How it was made, and how far to trust it.** Three research agents run by the planner searched public sources on 2026-10-03, on the web only. Each claim below is marked **[C]** where the agent read it on the cited page, **[S]** where only a search-engine snippet was seen (the page refused the fetch, usually HTTP 403), and **[I]** where it is the agent's or the planner's inference. **The planner has not re-checked the sources.** No patent or patent application was consulted; one search result linked a patent and was skipped. No local repository file was read by the agents.

**What the app has today** (read by the planner from `main`, -442): the online basemaps (Street, Topo, Satellite) are raster images, drawn but not readable as trails. The offline maps are Protomaps vector tiles from Forager's own Worker (`docs/plans/own-map-tiles.md`); they carry path lines simplified for drawing and cut at tile edges, with no junctions shared between lines, so they are not a network a route can be found on.

## Summary

1. **A comprehensive trail network exists, and the practical base is OpenStreetMap** [I]: it is the only source that is nationwide, already connected at junctions, updated daily, and licensed in a way an open-source app can ship (ODbL, with attribution). The federal agency datasets (Forest Service, BLM, USGS) are public domain and fill gaps, but they are loose lines that need joining and checking before a route can use them.
2. **Two open-source engines can find a route along trails on the phone without signal** [I]: Valhalla (through the `valhalla-mobile` Android library) and BRouter. Both are MIT licensed. Neither has been measured on a phone; a short spike on the S22 with real Oregon data would decide between them.
3. **Leaving the trail on purpose is not an error for a forager.** No app found treats it that way [I]. The closest are Locus Map (guides to the nearest route point in a straight line) and an established handheld GPS maker's track-back feature (back along the path walked). Search-and-rescue guidance favours retracing your steps over shortcuts. The walker's own track is the only way back known to be passable.

## 1. Trail data

### What exists [C unless marked]
- **OpenStreetMap:** paths, footways, tracks and bridleways, with tags for difficulty (`sac_scale`), how visible the trail is (`trail_visibility`), who manages it (`operator`), and unofficial trails (`informal=yes`). https://wiki.openstreetmap.org/wiki/United_States/Trails ; https://openstreetmap.us/our-work/trails/how-to-map/. An OSM US Trails Working Group has run since December 2021, with a Washington pilot in 2023: https://www.usgs.gov/national-digital-trails/through-its-trails-stewardship-initiative-openstreetmap-us-leading-efforts
- **US Forest Service:** National Forest System Trails, Roads, and the motor-vehicle-use maps (MVUM). https://data.fs.usda.gov/geodata/edw/datasets.php?dsetCategory=transportation
- **National Park Service:** Public Trails, with restricted segments removed. https://mapservices.nps.gov/arcgis/rest/services/NationalDatasets/NPS_Public_Trails/FeatureServer/0
- **BLM:** public trail subsets (motorized, non-motorized, non-mechanized). https://catalog.data.gov/dataset/blm-natl-gtlf-public-nonmotorized-trails
- **USGS National Digital Trails:** a public-domain collection from BLM, NPS, FWS, USFS, TVA and 36 states, 277,254 miles as of May 2022. https://www.usgs.gov/national-digital-trails/news/status-nationwide-digital-trails-dataset
- **States:** Washington's RCO Trails Database (v2.0, 2025-04-15) includes "some approved private sources" and says it is "not intended to be used for route-finding or navigation" [S]. Oregon approved a statewide trails plan on 2026-02-24 and is writing a trail data standard; no downloadable statewide layer was found. https://www.oregon.gov/oprd/PRP/Pages/PLA-statewide-trails.aspx
- **Excluded as proprietary:** Hiking Project (its data API closed in late 2020, https://hikingproject.com/data), a trail database app, a widely used hiking app, a widely used backcountry map app [I].

### Licences
- USGS and BLM: public domain [C]. Forest Service: only a no-warranty disclaimer was found; federal works are generally public domain [I].
- National Park Service: "general informational and mapping purposes only" and "Not for navigation" [C], with no licence field. That reads as a liability disclaimer, but using it for navigation goes against its stated purpose [I].
- OpenStreetMap (ODbL): attribution "© OpenStreetMap" [C] (https://osmfoundation.org/wiki/Licence_and_Legal_FAQ). Data combined with other data stays separate ("Collective Database", no share-alike) only if, per region, each kind of data is all-OSM or all-other; merging duplicates is not covered [C] (https://osmfoundation.org/wiki/License/Community_Guidelines/Collective_Database_Guideline_Guideline). So a route network merged from OSM and agency trails becomes one ODbL database that must be offered under ODbL [I]. For a free, open-source app that is a duty to publish, not a barrier.

### Coverage
- Government and OSM trail data "can be incomplete, out of date, or missing important metadata" [C] (https://openstreetmap.us/news/2024/12/tsi-utah-update). US coverage "is not well documented with limited research" [C] (State of the Map US 2022).
- The only measured completeness figure found is from the Alps, not the US: about 95% in 4 of 5 regions [C] (https://repositum.tuwien.at/handle/20.500.12708/3884). **US completeness is unmeasured.**
- Agency data leaves out social trails and keeps decommissioned ones; OSM includes social trails, which can only be told apart where mappers tagged them [I].

### Whether a route can be found on it
- OSM lines share points at junctions, so a route engine can use them directly. Agency files do not [C]; one 2025 import found overshoots of 0.21 ft, duplicates 0.04 ft apart and about 160 disconnected trails in one file [C].
- OSM Merge (Rob Savoye) merges agency data into OSM with human review [C] (https://openstreetmap.us/news/2024/11/welcome-osmmerge/). Joining lines is easy to automate; deciding which of two versions of a trail is right is not [I].

### Size and freshness [C]
- OSM extracts (Geofabrik): Oregon 242 MB, Washington 347 MB, the whole US 11.3 GB, all features, updated daily. A trails-only extract is a small fraction [I].
- Forest Service national files: Trails 119 MB, Roads 239 MB, MVUM Trails 35 MB (geodatabase), all republished within the last two weeks.

## 2. Offline route engines

| Engine | Licence | On the phone | Hiking awareness | OR+WA data | Starting off the trail |
|---|---|---|---|---|---|
| **Valhalla** via `valhalla-mobile` | MIT [C] | In-process native library (JNI), runs against tiles on the device [C] | `max_hiking_difficulty` (sac_scale 0 to 6), `use_tracks`, `use_hills` [C] | Not found; guessed 300 to 600 MB [I] | Snaps within a search radius; no connector line returned [I] |
| **BRouter** | MIT [C] | Java; only the separate-app route is documented, embedding the library is not [C]/[I] | `sac_scale`, surface and mud, uphill and downhill costs; no `trail_visibility` [C] | About 155 MB for the four tiles covering both states [C] | Searches up to about 50 km and can return the connection as a straight segment [C] |
| GraphHopper | Apache 2.0 [C] | Offline "no longer officially supported"; works only on old versions (4.0, 5.3) [C] | Hike profiles, `hike_rating` [C] | Not found | Snaps to the nearest edge [C] |
| An offline maps app's routing | GPLv3 [C] | Would make the whole app GPL [I] | | | |
| OSRM, Organic Maps | | Not realistic on-device, or not separable [I] | | | |

Sources: https://github.com/valhalla/valhalla ; https://github.com/Rallista/valhalla-mobile ; https://valhalla.github.io/valhalla/api/route/api-reference/ ; https://github.com/abrensch/brouter ; https://brouter.de/brouter/segments4/ (rd5 files dated 2026-10-03) ; https://github.com/graphhopper/graphhopper ; https://discuss.graphhopper.com/t/offlne-routing-on-android/9176 ; the offline maps app's LICENSE file in its public source repository.

- **Drawing the route** on MapLibre is a GeoJSON line layer, the same for any engine [I].
- **Ferrostar** (BSD-3, https://stadiamaps.github.io/ferrostar/) is an open-source navigation SDK with a MapLibre and Compose UI, usually paired with Valhalla. It has a replaceable off-route component [C]. It is prior art for the pairing, not necessarily something to adopt.
- **Which trail the walker is on:** standard map matching (a hidden Markov model) is in GraphHopper and in Valhalla's Meili (batch only) [C]. For live alerts, the distance to the active route with a hold time is enough, and is engine-independent [I].
- **The agent's recommendation** [I]: Valhalla first (medium effort), BRouter as the fallback (medium; smallest confirmed data). **Before choosing:** route 10 km on the S22 with real Oregon data in each, and record data size, memory and route time.

## 3. Leaving the trail, and getting back

### What others do
- **Locus Map** [C]: three distances, a maximum deviation (example 30 m), an off-route notice (75 m) and recalculation (100 m). Past the first it guides "to the nearest route point with a beeline". It advises larger values in rugged terrain. https://www.locusmap.app/how-to-set-alerts-for-leaving-your-navigation-route/
- **An offline maps app** [C]: recalculates to bring you back, rejoining at the track's start or its nearest point, by straight line or a route. (its GPX navigation documentation)
- **A handheld GPS maker's track-back feature** [C]: back "to the starting point of your activity along the path you traveled", with an "Off Course" alert. One user reports it fired only after 50 to 100 m when set to 10 m.
- **A widely used hiking app** [S]: one alert after about 50 m off route; tapping it stops further alerts until you are back on the trail. No guidance back.
- **A route-planning app** [S]: shows the distance walked off the route as a dotted line "so you can easily see how to backtrack".
- **Mapbox Navigation SDK** [C]: off route beyond 15 m, *not counting* the reading's accuracy; rerouting on backtracking needs 50 m over at least 3 updates.
- **Organic Maps** [C]: does not navigate along a track yet.

### Choosing the way back safely
- Guidance for lost hikers [C]: retrace your steps only a short way to your last known place; "avoid taking shortcuts"; "Avoid using navigation tools to justify moving into unknown terrain" (https://www.trailhiking.com.au/safety/what-to-do-if-you-get-lost-hiking/). Hikers use linear features (trails, streams, ridges) as handrails and catching features.
- Open US data that could rule out a straight line [C]: the 3DEP 10 m elevation model (lower 48), NHDPlus HR for water, PAD-US for public land.
- **The limit** [I]: a 10 m elevation model smooths away short cliffs and gullies, and none of these shows brush, deadfall or fences. Data can veto an obviously bad straight line but cannot certify one as safe. **Only the walker's own track is known to be passable.**

### Telling off the trail from GPS drift
- Phone GPS is about 5 m under open sky and worse under trees [C]; one forest study found mostly 5 to 15 m [C].
- Users of one watch that tightened its off-route thresholds report constant false alerts, "shortly followed by 'back on route' … I basically stopped paying attention to them" [C] (a sports watch maker's user forum, thread 15609).
- No published rule for when a walker counts as back on the trail was found.

### The agent's proposed behaviour, as a step path [I; every number is the agent's, untested]
1. The walker steps off the trail and stays more than about the reading's accuracy plus 20 m away, for about 15 s > the map quietly switches: the trail line dims, the walker's own track is highlighted, and a small chip reads "Off trail · your track leads back". No sound.
2. The walker forages and wanders > no alerts. A readout shows the way back to where they left the trail, measured along their own track.
3. The walker taps the chip or starts heading back > guidance follows their own track back to where they left. No straight-line shortcut by default; if one is ever offered, it is checked against slope and water and labelled unverified.
4. The walker gets far from both the trail and their own track (for example 300 m), or dusk approaches > **one** gentle alert, with sound and a notification.
5. The walker comes back near the trail for a few readings, at a point that fits their progress along it > trail guidance resumes, and "Back on trail" shows briefly.

**Risks named:** the mode flickering in forest (needs a hold time), the walker's own track carrying GPS error, a long way back if they wandered far, a wrong match on parallel trails, and a silent switch the walker never notices (the chip has to make it obvious).

## What this means for Forager [I, the planner's]

- **The owner's requirement and the research agree.** Default to the walker's own track off the trail, and guide back along it, is what the handheld GPS maker's track-back feature and the safety guidance point to, and what straight-line apps lack.
- **It fits what exists.** T21's off-track rule already measures against the walker's own path, and T5's way-back route already follows the walked track. Trail navigation adds a second thing to follow, the trail network, with the walker's own track as the way back to it.
- **It belongs with the next update's map work.** Trail data is a second offline download per region, next to the offline map tiles (`docs/plans/own-map-tiles.md`), and the route drawn on the map is T7.
- **It is not small.** Data preparation, an engine, a second download, mode switching and its alerts are each a piece of work.

## Decisions for the owner

1. **Data:** OpenStreetMap as the base. Are the public-domain agency trails (a) left out, (b) shown as a separate reference layer and used to flag gaps, or (c) merged into the route network, which makes it an ODbL database Forager must publish?
2. **"Not for navigation" sources:** are National Park Service and Washington RCO trail data excluded?
3. **Engine:** a short spike on the S22 comparing Valhalla and BRouter with real Oregon data, before choosing?
4. **Off-trail behaviour:** the step path above, confirmed or changed, in particular: a silent switch with a chip rather than an alert; the way back along the walker's own track, with no straight-line shortcut by default; and one alert only when far from both, or at dusk.
5. **Where it sits:** the next update, alongside the own map tiles and T7?

## Could not determine

US trail completeness in OSM against the agencies; explicit licences for the Forest Service, NPS and Washington RCO trail data; a downloadable Oregon statewide layer; Valhalla and GraphHopper data sizes for Oregon and Washington; memory, CPU and route times on a phone for any engine; whether BRouter's library can run inside the app; whether Valhalla returns the connector from an off-trail start; a backcountry navigation app's, a topographic mapping app's and Google walking off-route behaviour; the backcountry map app's and the route-planning app's thresholds; any published rule for rejoining a trail; any slope limit for walking cross-country.
