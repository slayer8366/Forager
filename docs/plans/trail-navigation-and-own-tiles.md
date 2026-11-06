# Trail navigation and Forager's own map tiles: one plan (next update)

**Status: a plan. Nothing is built. One stage is dispatched (the engine spike, -446).** Written by the planner on 2026-10-03 (UTC), at `main` `bb0b559e` plus the records of PR #158 (merged as `09e43bd3`).

**The owner, verbatim (2026-10-03):**
- "The map has trails and such, so when that data is available, I think we should take advantage of them and navigate against them rather than open space" (`RECORD.md` -442).
- "When a user moves off trail, which they often would do to score a find, then default to the user's own trail marks, and have a way to guide the user safely back to the trail to resume navigating" (-443).
- "Trails get this treatment and roads should get the same treatment. Navigation lines should follow established trails unless user deliberately wanders off" (-447).
- "I may consider retiling the maps later on for our own custom tiles", then "If we could do the map retiling around the trail navigation, that would be optimal and aligned with the core purpose", and, to this plan with the engine spike as its first stage, "Yes, do that" (-453).

**This is a claim about the past.** Every statement below about the repository was true at `bb0b559e` or comes from documents named beside it. Each stage begins by re-reading what it stands on (CLAUDE.md, "A planner's picture of the repository is a claim about the past").

## Why one plan

Trail navigation and Forager's own tiles need the same thing: OpenStreetMap data for an area, prepared on Forager's side and held on the phone. Planned together:
- **One download per area.** An offline region carries the map tiles and the routing data together, not two downloads the walker has to manage ("Don't make me think", CLAUDE.md).
- **One snapshot.** The map and the routes come from the same OpenStreetMap extract, so a trail the walker sees is a trail the route can use.
- **One look.** Forager's own style can draw trails as navigation treats them: real footpaths apart from old logging roads, official trails apart from informal ones.
- **Sharp at every zoom.** Vector tiles remove the raster caps that -440 Amendment 1 works around by enlarging tiles (-450).

## What is decided (owner rulings)

- **Data** (-445, 1 and 2): OpenStreetMap is the routable base. Public-domain agency trails (Forest Service, BLM; USGS dropped 2026-10-17, -1176) are a separate reference layer used to flag gaps, never merged into the route network. National Park Service and Washington RCO trail data are left out ("not for navigation").
- **The network** (-447): trails and roads alike. The route follows established paths where they exist.
- **Off the trail** (-445, 4), every number provisional: off for about 15 s, the map quietly dims the trail, highlights the walker's own track and shows "Off trail · your track leads back", with no sound; no alerts while foraging, with a readout of the way back along their own track; heading back, guidance follows their own track to where they left the trail, with no straight-line shortcut; one gentle alert only when far from both the trail and their track, or near dusk; back on the trail, trail navigation resumes and "Back on trail" shows briefly.
- **A wrong turn** (-447): walking along a different trail or road than the route, away from it, gives one alert ("Wrong way, your route is back there", the planner's wording) and the line shows the way back. Leaving all paths is the quiet switch above.
- **Today's off-track rule** (T21) stays as built until this replaces its "path at Return" (-449).
- **Licences:** no proprietary software in the app (the owner, -397). The app is non-commercial and open source; the 2026-09-28 commercial ruling cited in `docs/plans/own-map-tiles.md` is superseded. OSM data carries ODbL attribution, and any derived database Forager ships is offered under ODbL.

## Evidence, 2026-10-17: OSM and federal trails compared in seven areas

A read-only comparison (RECORD -1172; report `docs/audits/2026-10-17-trail-data-comparison.md` on branch `trail-data-comparison`, 7a10b971) measured both sources in seven 10 km areas. The method was committed before any data was read. Every query is logged, and the licences are quoted at source. It **confirms the data ruling above (-445):** OpenStreetMap as the routable base, and the agency trails as a separate reference layer.

**Where both have a trail they agree to about 3 m** (median offset 2.6 to 3.9 m; 11 m in the Frank Church).

**What each adds:**

| Area | OSM | Forest Service / BLM | What it means |
|---|---|---|---|
| Mount Hood | 160.6 km | FS 88.7 km summer trails (+53.8 km snow trails) | OSM has bike and informal trails, plus one FS-numbered trail missing from the FS layer |
| Eagle Cap (Wallowa-Whitman) | 99.5 km, 97% connected | **none** | OSM is the only source |
| Chamberlain Basin (Idaho interior) | 50.3 km | FS 59.3 km | two FS trails missing from OSM (12.5 km) |
| Oregon Badlands (BLM) | 150 km | BLM 54.6 km, all within 15 m of OSM | BLM adds the richest attributes: uses, seasons, surface |
| Cape Perpetua (coast) | 89.7 km (61 km forest roads) | FS 21.7 km | both needed: OSM includes roads tagged as tracks |
| Tiger and Cougar Mountain (no federal land) | 225.5 km | none | OSM only |
| Three Sisters | 63.4 km | FS 58.4 km | one FS trail missing from OSM; one climber trail drawn differently |

**Findings for the stages below:**
- **USGS's transportation trails added nothing** beyond the Forest Service and BLM in any area. They republish agency data about 1 m away, with fewer attributes.
- **The reference layer's real value is its attributes:** official names, trail numbers, class, allowed uses and seasons (where a forest publishes its full record), plus a few backcountry trails OSM lacks.
- **Neither source alone forms a connected network:** trails join through roads. Routing needs the road network in the graph (the -447 "trails and roads alike").
- **No source carries live closures.**
- **Attribution in the app:** "Map data © OpenStreetMap contributors, ODbL" with a link; "National Forest System Trails, USDA Forest Service, CC BY 4.0" with a link. BLM's line is optional (public domain).
- **Share-alike** (inferred, not legal advice): keeping the agency layer separate from the OSM routing database keeps ODbL's share-alike on the OSM part only, as the 2016 guideline from the body that runs OpenStreetMap allows. That matches -445's "never merged".

**Decided, 2026-10-17 (RECORD -1176):** USGS is dropped from the reference layer, which is now the Forest Service and BLM only. The owner: "yes we stick with the original plan. I was trying to be ambitious, but I want to be accurate and have the best data than throwing anything possible."

## Stages

Each stage after the first is written as its own dispatch only when the stage before it has reported, and each dispatch carries the usual report-first, tests-first, revert-check and device-check steps.

1. **Engine spike** (dispatched, -446, with Amendments 1 and 2). Valhalla and BRouter on the S22 with Oregon data: data size, app size, route time and memory, a start off the trail, a street route, telling which path the walker is on, and whether each engine's data can be packed per offline region beside the map tiles. Separate app; nothing merges.
2. **Own-tiles survey,** read-only: step 1 of `docs/plans/own-map-tiles.md`, re-read, with two changes: licences are checked against the non-commercial, open-source model rather than the superseded commercial ruling; and the survey also asks how the Worker's archive is built and whether the same pipeline can emit routing data from the same extract.
3. **Spec, for the owner to rule on** (`plan-forge:spec`):
   - own Street only, or own topo too (contours and hillshade from public-domain elevation data);
   - coverage to start with (Oregon and Washington, or the continental US) and the size of a region download;
   - the engine, from stage 1;
   - how trails, tracks and informal paths look, by day and at night (night stays pure V1 inversion, -314, -322);
   - the reference layer's look;
   - hosting cost for a public app, on Cloudflare;
   - how this meets the Navigator completion plan's remaining stages (`docs/navigation/2026-10-01-navigator-completion-plan.md`: T7, the route drawn on the map; T8 and T9, navigating to a waypoint; and stages G to K), each read and reconciled, not assumed.
4. **Data pipeline:** one OSM extract per area becomes the map tiles, the routing data and the reference layer, with a stated refresh cadence, published from Forager's own Worker.
5. **Own basemap in the app:** online and offline, day and night; offline regions and the owner's data survive the change.
6. **Routing along the network:** the route drawn along trails and roads (T7's line becomes this), the way home and to a waypoint.
7. **Off the trail and back:** the confirmed step path, the wrong-turn alert, and matching the walker to the path they are on. Replaces T21's path at Return.
8. **The reference layer:** agency trails drawn separately, flagging gaps.
9. **The owner's walks:** in town and in a national forest, on the S22, with the lasting record of Return taps and decisions (-451) to read them by.

## Constraints carried forward

- **Positions never enter the repository.** It is public; walks and test routes are real places.
- **Offline regions keep working throughout,** and Room and DataStore rules apply to anything new (CLAUDE.md).
- **Attribution stays visible** for every source drawn.
- **The User-Agent** (`net/AppUserAgent`) goes on every request to the Worker.
- **Every figure is provisional and named once,** and is for a walk to test.
- **Map chrome over the map at 80%** (CLAUDE.md) for every new label, chip and readout.

## Open, not yet asked

- Whether T7 waits for stage 6 or ships first on the walked track.
- A storage ceiling for a region download.
- Coverage beyond the US.
