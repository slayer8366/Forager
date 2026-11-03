# Dispatch 2026-11-02-04: waypoint names on the map (Hazel, Opus)

Written by Rowan Waymark, 2026-11-02. RECORD -1964, -1965. Starts after dispatch 2026-10-30-08's D23 is green.

**The owner**, from a landscape screenshot while plotting a route: "One thing for clarity, have waypoints be labeled on the map. There can easily be dozens that clutter a map, so maybe have it display during route plotting. Do you think it should display the waypoint names all the time? What do typical maps do?" Then, to the planner's options: "Option 1", whose option read "Always, crowded ones hidden until you zoom in, the route's stops always named"; after a researcher's check (RECORD -1964): "Option 1 plus an off switch", whose option read "As above, with a setting to turn names off, as CalTopo has." Then "Yes, start both".

**Prior art, read at source by the researcher (-1964):** CalTopo's Show Labels: "Non-Overlapping: when labels overlap only one label will show. As you zoom in you'll see more of your labels" (training.caltopo.com/all_users/tools/config), with All and None.

**Base:** route-plot-ui after D23 (the route's stops need #449). New branch `waypoint-names` cut from it; a PR into route-plot-ui's PR's base later, as the planner says. No merge. Every premise below is to re-check with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | Names on the map |
| **B** | The setting |
| **C** | Tests and checks |

**For every item:** verify first and report by message before building; fail first, then a revert check (build log checked, restore from a saved copy, forward change confirmed after); full CI green on the last real commit, no empty commits; commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. The names are the user's own record names; no new words on screen beyond B1's, which the planner brings to the owner. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object.

## A. Names on the map
- **A1** (2026-11-02, -1964). Every waypoint's name shows on the map beside its pin, by default. Where names would overlap, only the ones that fit show; zooming in shows more (the map's own label collision, as CalTopo's "Non-Overlapping"). Pins themselves are unchanged, including the crowded-icons fan and list (say how names and the fan interact; stop and ask if they conflict).
- **A2** (2026-11-02, -1964). While plotting a route, and on a saved route's card, the route's stops always keep their names, even where they would collide. Say what yields to them.
- **A3** (2026-11-02, -1964). Names follow the map's night and day look, and never sit over map chrome (CLAUDE.md, nothing fully obstructs the map).

- **A4** (2026-11-02, -1974). Hazel's verify-first: no app layer draws text; no raster style declares glyphs (the owner's 2026-10-18-04 "remove the fonts requests", BasemapStyles.kt:20-28); the offline style can carry no glyphs (the PR #23 crash, MapLibreOfflineMapRepository.kt:61-75). The owner: "As small pictures (Recommended)", whose option read "Each name is drawn as a tiny image in the app's own font. Works on every map (Trail, Topo, OSM), offline and with no signal; crowded names still hide and come back as you zoom in. No font download, no map style change." Build Hazel's option 1: each name a bitmap added with addImage, in a "waypoint names" SymbolLayer drawn with the waypoints (so the fan hides a fanned pin's name), no overlap, above the pins; the route's stops in their own layer with overlap allowed, above it, the stops reaching the map while plotting from RouteStop.name; colours from MapPalette.forMode(night); blank names get no label. The planner's call on A3: names are map content, so map chrome drawing over them (at 80%) meets "nothing over chrome".

- **A5** (2026-11-02, -1979). Seen on the S22 (-1978, b4-6e): two route stop names print over each other ("TETEST I"). The owner: "Stops avoid each other (Recommended)", whose option read "Stop names still always beat ordinary waypoint names, but two stop names don't print over each other: the one that would collide hides until you zoom in, like waypoint names. Every stop is still listed by name on the card." Merge origin/route-plot-ui in first (it now holds D27 and later). *To build.*

## B. The setting
- **B1** (2026-11-02, -1964). A setting turns waypoint names off (on by default). Propose where it lives and its words; the planner asks the owner before it ships. It persists across restarts (DataStore, per CLAUDE.md's Room/DataStore rule).

- **B2** (2026-11-02, -1974). The owner: "Settings > Map", whose option read "A checkbox 'Waypoint names' in Settings > Map, under 'Crowded icons'." So: a checkbox "Waypoint names" in Settings > Map under "Crowded icons" (MapSettingsSection.kt), on by default, its own DataStore key ("map.waypoint_names"), surviving restarts.

- **B3** (2026-11-02, -1974). Hazel asked whether the switch also hides the route's stop names. The owner: "Yes, stops keep names (Recommended)", whose option read "The setting hides names on ordinary waypoints; a route's stops stay named while it's on screen, since you're working with them." So off hides the waypoint-name layer only.

## C. Tests and checks
- **C1** (2026-11-02, -1964). Through the real Maps tab: names show by default; a crowded group shows fewer names than pins and more after zooming in; the route's stops are named while plotting; the setting hides them and survives a restart. Fail first, then revert checks. No phone unless the planner says.
- **C2** (2026-11-02, -1974, the planner's call). C1's crowding claim can't run headless (MapLibre's collision is native; no real MapView under Robolectric). Test headless what can be: the name layer's properties (no overlap; stops overlap allowed), the images and features per waypoint with blank names skipped, through the real Maps tab what the map is handed (names by default, the stops' names while plotting, the setting turning them off), and the setting surviving a restart on a new DataStore instance. The collision itself is checked on a phone later and recorded as untested until then. CI only.
